package org.sterl.open.aifactory.chat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.AiReviewAgent;
import org.sterl.llmpeon.agent.NamedAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.llmpeon.poagent.tools.PoDelegateTool;
import org.sterl.llmpeon.tool.WriteValidator;
import org.sterl.open.aifactory.config.TurnNotConfiguredException;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * One project's chat: its Jon, his team, his tools, and the serialization of his turns. A plain class on
 * purpose — the constructor is the test seam, and only the registry may throw a runtime away.
 *
 * <p>Everything a turn needs is read per turn, never per runtime: the model keeps its built state until
 * {@link #submit} hands it a fresh config, which is what makes a changed setting land on the
 * <b>next</b> turn without a restart (UC-WP-24 GIVEN 3). Nothing here knows HTTP, JSON or the SSE wire,
 * and the history is the core's memory — the factory never derives a file name (D4).</p>
 */
final class ProjectRuntime {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectRuntime.class);

    /** The core's search sub-loop ({@code @Tool(name = "searchAgent")}) — a tool of the core, not a member of this team. */
    private static final String SEARCH_AGENT_TOOL = "searchAgent";
    private static final String SEARCH_AGENT_DISPLAY_NAME = "Da Sniffa";

    private final String projectId;
    private final Supplier<LlmConfig> configForTurn;
    private final ConfiguredChatModel model;
    private final AiPoAgent jon;
    private final ChatStreamRegistry streams;
    private final ExecutorService turns;
    private final SseTurnMonitor monitor;
    private final AgentNames names;

    /**
     * @param initialConfig the config the model is built with — the plain read, because a project must
     *                      get a runtime (and a restored history) before its config is complete; an
     *                      incomplete config is refused per turn instead, as a live {@code error}
     * @param configForTurn the validated read, taken again before every turn
     */
    ProjectRuntime(String projectId, Path projectRoot, Path stateDir,
            Supplier<LlmConfig> initialConfig, Supplier<LlmConfig> configForTurn,
            ChatStreamRegistry streams, ExecutorService turns) {
        this.projectId = projectId;
        this.configForTurn = configForTurn;
        this.streams = streams;
        this.turns = turns;
        this.model = new ConfiguredChatModel(initialConfig.get());

        JonToolSet toolSet = JonToolSet.forProject(projectRoot);
        WriteValidator writeValidator = toolSet.writeValidator();
        NamedAgent plan = new NamedAgent("Da Thinka", new AiPlanAgent(model, toolSet.tools()));
        NamedAgent review = new NamedAgent("Da Dok", new AiReviewAgent(model, toolSet.tools()));
        NamedAgent dev = new NamedAgent("Da Mek", new AiDevAgent(model, toolSet.tools()) {
            @Override
            public WriteValidator getWriteValidator() {
                // UC-WP-8/9 name the team too, and the core default is ALLOW_ALL — Da Mek gets exactly
                // Jon's two roots, from the same instance, so one rule has one source (R-WP-6)
                return writeValidator;
            }
        });
        // registered before the agent is built, otherwise Jon cannot delegate in his first turn; the
        // budget is a supplier so a changed config takes effect without a rebuild
        toolSet.addDelegateTool(plan, review, dev, () -> model.getConfig().getAutoCompactAfter());

        // the team in the core's lifecycle order: Da Thinka, Da Mek, Da Dok (AiPoAgent.getTeam())
        this.jon = new AiPoAgent(model, toolSet.tools(), stateDir, List.of(plan, dev, review)) {
            @Override
            public WriteValidator getWriteValidator() {
                // the core default is WriteValidator.DOCS, which would make half the checkout off limits
                return writeValidator;
            }
        };
        // the slaves' own naming is the core's job, this one line is the factory's (D6)
        this.jon.setStaticContext(List.of(ContextItem.of("Your name is Da Boss.")));

        // keyed by the core's tool names, filled with the names this factory built, so the product names
        // a member in exactly one place (D1) and a history line needs no second record of its author
        this.names = authorTable(plan.uiName(), dev.uiName(), review.uiName());
        this.monitor = new SseTurnMonitor(projectId, streams, this.names);
    }

    /**
     * The one author table of a project's chat: the core's delegation tools, named by the team this
     * factory built. Package-private and single — what is in here is what a history line can be
     * attributed to, and a tool missing here is a line that silently stays unnamed.
     */
    static AgentNames authorTable(String planName, String devName, String reviewName) {
        return AgentNames.empty()
                .withTool(PoDelegateTool.TALK_PLAN, planName)
                .withTool(PoDelegateTool.PLAN_WITH_PLAN_AGENT, planName)
                .withTool(PoDelegateTool.ASK_DEV, devName)
                .withTool(PoDelegateTool.BUILD_WITH_DEV, devName)
                .withTool(PoDelegateTool.REVIEW_PLAN_AGENT, reviewName)
                // the search sub-loop is a tool of the core, not a member of this team — R-WP-4 asks for
                // the author of a line, not for a seat in the team
                .withTool(SEARCH_AGENT_TOOL, SEARCH_AGENT_DISPLAY_NAME);
    }

    /**
     * Fire and forget: the caller's thread is never a turn's thread, so the POST can answer while Jon is
     * still working (architecture doc "prüft nichts über den Turn hinaus"). A second input while he
     * works lands in his own queue — the core's serialization is the only one the factory needs (D2).
     *
     * <p>The duration starts <b>inside</b> the runnable — waiting for a worker thread is not part of the
     * turn. One {@code done} per <b>turn</b>, queue drain included: the architecture doc ends a turn when
     * {@code call()} returns, and {@code call()} answers what queued up while it ran. An input that came
     * in while he worked is answered by that turn and by its {@code done}, so it sends none of its
     * own.</p>
     */
    void submit(String text) {
        turns.submit(() -> {
            long startedAt = System.nanoTime();
            try {
                model.updateConfig(configForTurn.get());
                ChatResponse answer = jon.call(text, monitor);
                if (answer != null) {
                    streams.publish(projectId,
                            new ChatEvent.Done(Duration.ofNanos(System.nanoTime() - startedAt).toMillis()));
                }
            } catch (TurnNotConfiguredException notConfigured) {
                streams.publish(projectId, new ChatEvent.Failed(notConfigured.getMessage()));
            } catch (RuntimeException failure) {
                LOG.error("chat turn of project {} failed", projectId, failure);
                streams.publish(projectId,
                        new ChatEvent.Failed("the turn failed — the server log names the reason"));
            }
        });
    }

    /**
     * The chat as the memory has it: user text, Jon's answers, and the final answer of a team member —
     * everything else skipped, so a system prompt, a tool call, an ordinary tool result and an empty
     * message are not chat lines (D4). A queued input keeps the core's {@code [Queued Message]} marker,
     * it is reality and not the factory's to hide (Q6).
     *
     * <p>Every line states its author (R-WP-4, UC-WP-39): Jon by name, the member by name, the typed
     * input by none. So the team's answer is read back out of the one history file the core already
     * keeps — no second store, no parsing of the text the model sees (R-WP-11).</p>
     */
    List<ChatLine> history() {
        List<ChatLine> lines = new ArrayList<>();
        for (ChatMessage message : jon.getMemory().getCopy()) {
            if (message instanceof UserMessage user) {
                String text = textOf(user);
                if (!text.isBlank()) {
                    lines.add(ChatLine.user(text));
                }
            } else if (message instanceof ToolExecutionResultMessage result) {
                ChatLine memberAnswer = finalTeamAnswerOf(result);
                if (memberAnswer != null) {
                    lines.add(memberAnswer);
                }
            } else if (message instanceof AiMessage answer && !answer.hasToolExecutionRequests()
                    && answer.text() != null && !answer.text().isBlank()) {
                lines.add(ChatLine.jon(answer.text(), jon.getName()));
            }
        }
        return List.copyOf(lines);
    }

    /**
     * The final answer a member handed Jon: the result of the tool that is its delegation, told apart
     * from an ordinary tool result by its <b>tool name</b> alone. Two delegations in one answer of Jon's
     * are two result messages, each carrying its own tool name — which is why reading them back needs
     * neither the tool-call id nor a place to remember an order (ADR-0078 Decision 6).
     *
     * <p>The text is the result <b>verbatim</b>, statistics line included: it is the same text the model
     * reads, and shortening it would be the factory's guess about what matters. That includes a failed
     * tool — a failure is a line with an author like any other (UC-WP-40), so nothing here filters by
     * text. A tool this table does not know stays no line, and neither does a result that is not one
     * text ({@code text()} would throw on it).</p>
     */
    private ChatLine finalTeamAnswerOf(ToolExecutionResultMessage result) {
        String author = names.authorOf(result.toolName());
        if (author == null || !result.hasSingleText()) {
            return null;
        }
        return ChatLine.jon(result.text(), author);
    }

    /** Jon himself — reachable without a rebuild for the team view and {@code clear()} of Inc 5. */
    AiPoAgent jon() {
        return jon;
    }

    /**
     * The one model of this project — the same instance every agent was built with, so "the next turn
     * uses the changed value" is a statement about this object staying alive (UC-WP-24 GIVEN 3).
     */
    ConfiguredChatModel model() {
        return model;
    }

    /** Only the text parts of a user message, joined as the core joins them; image parts are no chat line. */
    private static String textOf(UserMessage user) {
        StringBuilder text = new StringBuilder();
        for (Content content : user.contents()) {
            if (content instanceof TextContent txt && !txt.text().isBlank()) {
                if (!text.isEmpty()) {
                    text.append(System.lineSeparator());
                }
                text.append(txt.text());
            }
        }
        return text.toString();
    }
}
