package org.sterl.open.aifactory.chat;

import java.nio.file.Path;
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
import org.sterl.llmpeon.tool.WriteValidator;
import org.sterl.open.aifactory.config.TurnNotConfiguredException;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;

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

    private final String projectId;
    private final Supplier<LlmConfig> configForTurn;
    private final ConfiguredChatModel model;
    private final AiPoAgent jon;
    private final ChatStreamRegistry streams;
    private final ExecutorService turns;
    private final SseTurnMonitor monitor;

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
        this.monitor = new SseTurnMonitor(projectId, streams);
        this.model = new ConfiguredChatModel(initialConfig.get());

        JonToolSet toolSet = JonToolSet.forProject(projectRoot);
        NamedAgent plan = new NamedAgent("Da Thinka", new AiPlanAgent(model, toolSet.tools()));
        NamedAgent review = new NamedAgent("Da Dok", new AiReviewAgent(model, toolSet.tools()));
        NamedAgent dev = new NamedAgent("Da Mek", new AiDevAgent(model, toolSet.tools()));
        // registered before the agent is built, otherwise Jon cannot delegate in his first turn; the
        // budget is a supplier so a changed config takes effect without a rebuild
        toolSet.addDelegateTool(plan, review, dev, () -> model.getConfig().getAutoCompactAfter());

        WriteValidator writeValidator = toolSet.writeValidator();
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
    }

    /**
     * Fire and forget: the caller's thread is never a turn's thread, so the POST can answer while Jon is
     * still working (architecture doc "prüft nichts über den Turn hinaus"). A second input while he
     * works lands in his own queue — the core's serialization is the only one the factory needs (D2).
     */
    void submit(String text) {
        turns.submit(() -> {
            try {
                model.updateConfig(configForTurn.get());
                jon.call(text, monitor);
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
     * The chat as the memory has it: user text and Jon's answers, everything else skipped — a system
     * prompt, a tool call, a tool result and an empty message are not chat lines (D4). A queued input
     * keeps the core's {@code [Queued Message]} marker, it is reality and not the factory's to hide (Q6).
     */
    List<ChatLine> history() {
        List<ChatLine> lines = new ArrayList<>();
        for (ChatMessage message : jon.getMemory().getCopy()) {
            if (message instanceof UserMessage user) {
                String text = textOf(user);
                if (!text.isBlank()) {
                    lines.add(new ChatLine(ChatLine.ROLE_USER, text));
                }
            } else if (message instanceof AiMessage answer && !answer.hasToolExecutionRequests()
                    && answer.text() != null && !answer.text().isBlank()) {
                lines.add(new ChatLine(ChatLine.ROLE_JON, answer.text()));
            }
        }
        return List.copyOf(lines);
    }

    /** Jon himself — reachable without a rebuild for the team view and {@code clear()} of Inc 5. */
    AiPoAgent jon() {
        return jon;
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
