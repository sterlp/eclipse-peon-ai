package org.sterl.open.aifactory.chat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.AiReviewAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.llmpeon.poagent.tools.PoDelegateTool;
import org.sterl.llmpeon.tool.WriteValidator;
import org.sterl.llmpeon.tool.tools.SearchAgentTool;
import org.sterl.open.aifactory.config.TurnNotConfiguredException;
import org.sterl.open.aifactory.chat.dto.AgentAction;
import org.sterl.open.aifactory.chat.dto.AgentTileDto;
import org.sterl.open.aifactory.chat.dto.AgentTranscriptDto;
import org.sterl.open.aifactory.chat.dto.TileCapability;

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

    /** This factory's label for a search result line and for its tile — its own word, not the core's loop stamp ({@code SearchAgentTool.LOOP_NAME}). */
    private static final String SEARCH_AGENT_DISPLAY_NAME = "Search";

    private final String projectId;
    private final Supplier<LlmConfig> configForTurn;
    private final ConfiguredChatModel model;
    private final AiPoAgent jon;
    private final ChatStreamRegistry streams;
    private final ExecutorService turns;
    private final SseTurnMonitor monitor;
    private final AgentNames names;

    /**
     * Who holds this project right now — a turn or one tile action, decided in one place so the two can
     * never meet inside a memory, and so that a send during an action waits instead of being refused.
     */
    private final TurnClaim claim = new TurnClaim();

    /** The agent whose turn failed; his tile shows ERROR, and only while he is idle again (UC-WP-11). */
    private volatile String failedAgent;

    /** The search sub-loop owns no agent instance — his tile's state is the one fact his lines tell us. */
    private volatile boolean searchWorking;

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
        AiAgent plan = new AiPlanAgent(model, toolSet.tools());
        AiAgent review = new AiReviewAgent(model, toolSet.tools());
        AiAgent dev = new AiDevAgent(model, toolSet.tools()) {
            @Override
            public WriteValidator getWriteValidator() {
                // UC-WP-8/9 name the team too, and the core default is ALLOW_ALL — Dev gets exactly
                // Jon's two roots, from the same instance, so one rule has one source (R-WP-6)
                return writeValidator;
            }
        };
        // registered before the agent is built, otherwise Jon cannot delegate in his first turn; the
        // budget is a supplier so a changed config takes effect without a rebuild
        toolSet.addDelegateTool(plan, review, dev, () -> model.getConfig().getAutoCompactAfter());

        // the team in the core's lifecycle order: Plan, Dev, Review (AiPoAgent.getTeam())
        this.jon = new AiPoAgent(model, toolSet.tools(), stateDir, List.of(plan, dev, review)) {
            @Override
            public WriteValidator getWriteValidator() {
                // the core default is WriteValidator.DOCS, which would make half the checkout off limits
                return writeValidator;
            }
        };
        // shared static context only — the identity lines are the core's job: this call is what hands
        // every slave its own name (AiPoAgent.setStaticContext), a name of its own must never ride along.
        // The one text this factory adds is the format of a sender (R-WP-25): it names no person, so the
        // copy the core fans out to every member says something true to all of them and identity to none.
        this.jon.setStaticContext(List.of(ContextItem.of(UserName.promptLine())));
        // keyed by the core's tool names, filled with the names this factory built, so the product names
        // a member in exactly one place (D1) and a history line needs no second record of its author
        this.names = authorTable(plan.getName(), dev.getName(), review.getName());
        this.monitor = new SseTurnMonitor(projectId, streams, this::tiles, this::sawOrigin);
    }

    private AgentTileDto tileOf(AiAgent agent, AgentTiles.Seat seat, long budget) {
        TurnClaim.Action current = claim.action();
        boolean actingHere = current != null && current.name().equalsIgnoreCase(agent.getName());
        // the core sets no flag for a clear, so the claimed action is what makes the tile answer at once
        boolean working = agent.isWorking() || actingHere && current.action() == AgentAction.CLEAR;
        boolean compacting = agent.isCompacting() || actingHere && current.action() == AgentAction.COMPACT;
        long tokens = agent.getMemory().getTotalTokenUsed();
        boolean estimate = agent.getMemory().isTokenEstimate();
        return AgentTiles.of(agent.getName(), tokens, estimate, agent.tokenContextUsedInPercent(), budget,
                working, compacting, agent == jon && failedAgent != null,
                AgentTiles.capabilitiesOf(seat));
    }

    /**
     * The team's tiles — one snapshot of live core state, read on demand and never stored (R-WP-11).
     * Safe to call from a monitor callback or the turn thread: every number is a volatile read of an
     * agent that is already running, nothing here blocks on a turn.
     *
     * <p>The order is the product's: Jon, then his team in the core's lifecycle order, then the search
     * sub-loop. A member the core added later appears without any change here.</p>
     */
    List<AgentTileDto> tiles() {
        long budget = model.getConfig().getAutoCompactAfter();
        List<AgentTileDto> tiles = new ArrayList<>();
        for (AiAgent agent : jon.getTeam()) {
            tiles.add(tileOf(agent, seatOf(agent), budget));
        }
        tiles.add(AgentTiles.of(SEARCH_AGENT_DISPLAY_NAME, 0L, false, 0, 0L, searchWorking, false, false,
                AgentTiles.capabilitiesOf(AgentTiles.Seat.SUB_LOOP)));
        return List.copyOf(tiles);
    }

    /**
     * An action for one tile. Two refusals, in this order: a name that is not a tile is a wrong request
     * ({@link IllegalArgumentException} ⇒ 400) even while a turn runs, and a running turn or a second
     * action is a busy project ({@link IllegalStateException} ⇒ 409) — an action is never queued, because
     * a clear the user asked for in the middle of a turn is not a wish he still holds afterwards.
     *
     * <p>It runs on {@link #turns} and never on this thread: Clear and Compact mutate a memory that a
     * parallel turn would write over. That is held, not promised — {@link TurnClaim#claimAction} takes the
     * project here, on the caller's thread, and a turn that starts from now on waits for the action to
     * finish rather than running beside it.</p>
     */
    void act(String name, AgentAction action) {
        AiAgent agent = tileNamed(name);
        if (!AgentTiles.capabilitiesOf(seatOf(agent)).contains(action.capability())) {
            throw new IllegalArgumentException(agent.getName() + " offers no " + action);
        }
        claim.claimAction(agent.getName(), action, teamIsWorking());
        try {
            streams.publish(projectId, new ChatEvent.Agents(tiles()));
            turns.submit(() -> {
                try {
                    // the flag belongs to a turn, and an action can only claim the project while no turn is
                    // in flight — so whatever the flag holds here belongs to a turn that is already over.
                    // Taken at the start, not after: measured 2026-11-01, a Compact that inherits the stop of
                    // that earlier turn is canceled by the core at its first partial and reports
                    // „COMPACT failed" for as long as nobody sends again (§16 PRE-EXISTING). A stop cannot
                    // reach this window anyway (turnIsStoppable needs a free claim), so nothing live is cleared
                    monitor.resetCancel();
                    runAct(agent, action);
                } finally {
                    // released before the snapshot: a reader that is allowed to act again never sees the tile
                    // still holding the action, and a turn task waiting for this wakes on the same release
                    claim.releaseAction();
                    streams.publish(projectId, new ChatEvent.Agents(tiles()));
                }
            });
        } catch (RuntimeException taskNeverStarted) {
            // the release belongs to a task that will now never run: without this the project stays claimed
            // for every later send and every later action until the factory restarts (§16 CONCERN 2)
            claim.releaseAction();
            LOG.error("action {} of agent {} of project {} was never started", action, agent.getName(),
                    projectId, taskNeverStarted);
            // the tiles are read live, so this only tells the browsers that were watching: a refusal they
            // cannot see would leave the tile holding an action that never ran
            streams.publish(projectId, new ChatEvent.Agents(tiles()));
            throw taskNeverStarted;
        }
    }

    /** The seat a name sits on — the one source of what its tile may do (D-14). */
    private AgentTiles.Seat seatOf(AiAgent agent) {
        return agent == jon ? AgentTiles.Seat.JON : AgentTiles.Seat.MEMBER;
    }

    /**
     * The insight into one tile's own conversation (R-WP-26, UC-WP-49), read out of the core's memory and
     * out of nothing else. Two names answer 400 like an action does: one that is no tile of this project,
     * and one whose tile offers no insight — Jon's conversation <b>is</b> the chat, and the search sub-loop
     * owns no memory between its calls.
     *
     * <p>Nothing here is a conversation of its own: the edge reads, it cannot write, choose or start
     * anything, so the chat stays the one conversation a project has (UC-WP-16).</p>
     */
    AgentTranscriptDto transcript(String name) {
        AiAgent agent = tileNamed(name);
        if (!AgentTiles.capabilitiesOf(seatOf(agent)).contains(TileCapability.TRANSCRIPT)) {
            throw new IllegalArgumentException(agent.getName() + " offers no insight");
        }
        return AgentTranscript.of(agent.getName(), agent.getMemory().getCopy());
    }

    /** The team member a tile names — the roster is the only address space, so no name is a free path. */
    private AiAgent tileNamed(String name) {
        for (AiAgent agent : jon.getTeam()) {
            if (agent.getName().equalsIgnoreCase(name)) {
                return agent;
            }
        }
        throw new IllegalArgumentException("no tile named " + name);
    }

    private boolean teamIsWorking() {
        for (AiAgent agent : jon.getTeam()) {
            if (agent.isWorking() || agent.isCompacting()) {
                return true;
            }
        }
        return false;
    }

    /** A failed Compact is a live line and no new channel (D-8); {@code SKIPPED_SMALL} is a no-op, not news. */
    private void runAct(AiAgent agent, AgentAction action) {
        try {
            if (action == AgentAction.CLEAR) {
                agent.clear();
            } else {
                CompactResult result = agent.compact(monitor);
                if (result.status() == CompactResult.Status.FAILED_EMPTY) {
                    streams.publish(projectId, new ChatEvent.ToolLine(agent.getName() + ": " + result.resultLine()));
                }
            }
        } catch (RuntimeException failure) {
            LOG.error("{} of agent {} of project {} failed", action, agent.getName(), projectId, failure);
            streams.publish(projectId, new ChatEvent.ToolLine(agent.getName() + ": " + action
                    + " failed — the server log names the reason"));
        }
    }

    /** A search line is the only proof that the sub-loop is running — it owns no agent to ask. */
    private void sawOrigin(String origin) {
        if (SearchAgentTool.LOOP_NAME.equals(origin)) {
            searchWorking = true;
        }
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
                .withTool(SearchAgentTool.NAME, SEARCH_AGENT_DISPLAY_NAME);
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
     *
     * <p>A task of a queued input therefore also <b>owns nothing</b>: it frees no project, resets no cancel
     * flag and ends no turn — its {@code call()} answers {@code null} precisely because another turn is
     * running, and a task that cleared the project there would hand out a turn the project still has
     * (UC-WP-50: the stop of that turn must still be accepted).</p>
     */
    void submit(String text, String user) {
        // the name stands in front of the text before the core sees either one: the memory is the one
        // store, so a name written into it needs no field of its own and outlives a restart by itself
        // (R-WP-25, R-WP-11, UC-WP-48 GIVEN 3). Nothing below changes for it — claim, reset, done.
        String message = UserName.payload(user, text);
        // the send that finds no turn in flight is the one that opens it — the flag is set on this thread,
        // so a stop arriving one millisecond later already addresses the turn the user just started (D-13)
        boolean opensTurn = claim.openTurn();
        failedAgent = null;
        searchWorking = false;
        turns.submit(() -> {
            long startedAt = System.nanoTime();
            boolean ranTurn = opensTurn;
            boolean askedCore = false;
            try {
                if (opensTurn) {
                    // a turn starts with a clean cancel flag — the monitor outlives the turn, so the stop of
                    // the previous one must not be born into this one (D-17). Only the send that opened the
                    // turn may clear it: a queued input arriving here would clear the stop the running turn
                    // has just been given. Never on the caller's thread, which may hold a stop it just got
                    monitor.resetCancel();
                }
                // a Clear or Compact that claimed the project first runs to its end: this waits and never
                // refuses, because an input sent during an action is still wanted after it (BUG-1)
                claim.awaitNoAction();
                // the tiles answer before the core sets his working flag, and a previous turn's ERROR
                // leaves the display with this line instead of lingering until the first tool line
                streams.publish(projectId, new ChatEvent.Agents(tiles()));
                model.updateConfig(configForTurn.get());
                askedCore = true;
                ChatResponse answer = jon.call(message, monitor);
                ranTurn = ranTurn || answer != null;
                if (answer != null) {
                    streams.publish(projectId,
                            new ChatEvent.Done(Duration.ofNanos(System.nanoTime() - startedAt).toMillis()));
                }
            } catch (TimeoutException neverReleased) {
                // the wait gave up: the project is handed back with a word about it instead of standing in
                // the wait forever — only the send that opened the turn owns that end (see finally)
                failedAgent = jon.getName();
                LOG.error("turn of project {} never got the project back from a claimed action", projectId,
                        neverReleased);
                streams.publish(projectId, new ChatEvent.Failed(
                        "the project is still held by an action — this turn ended, the server log names the reason"));
            } catch (TurnNotConfiguredException notConfigured) {
                ranTurn = ranTurn || askedCore;
                failedAgent = jon.getName();
                streams.publish(projectId, new ChatEvent.Failed(notConfigured.getMessage()));
            } catch (RuntimeException failure) {
                ranTurn = ranTurn || askedCore;
                if (TurnOutcome.of(failure) == TurnOutcome.CANCELED) {
                    // the user ended this turn himself: done, no error, no failed tile — and the lines he
                    // already saw stay, because nothing here is re-read (UC-WP-50, D-16)
                    streams.publish(projectId,
                            new ChatEvent.Done(Duration.ofNanos(System.nanoTime() - startedAt).toMillis()));
                } else {
                    failedAgent = jon.getName();
                    LOG.error("chat turn of project {} failed", projectId, failure);
                    streams.publish(projectId,
                            new ChatEvent.Failed("the turn failed — the server log names the reason"));
                }
            } finally {
                if (ranTurn) {
                    searchWorking = false;
                    // the end snapshot goes out while the turn still owns the project, so a tile never shows
                    // WORKING to a reader that is already allowed to act again
                    streams.publish(projectId, new ChatEvent.Agents(tiles()));
                    claim.endTurn();
                }
            }
        });
    }

    /**
     * The counter-movement of {@link #submit} (UC-WP-50): one flag on the project's one monitor, which Jon,
     * every running delegation and the search sub-loop share — so his turn and theirs end together, with no
     * thread interrupted and no second turn path. The core reads the flag between its model calls, which is
     * also the boundary a test has to respect: a tool that is already running runs to its end.
     *
     * <p>It writes nothing and starts nothing: the lines the user has seen stay live and the history stays
     * the memory. A queued input is the core's own business — measured 2026-11-01, an input its turn had
     * already polled is dropped by the abort ({@code AbstractAgent.call} polls at :318, the loop leaves at
     * :331 on the cancel, the drain at :334 finds an empty queue), so chat.md's last clause of UC-WP-50
     * stays open. Refused while no turn runs — a {@link IllegalStateException} ⇒ 409 — because a stop
     * without a turn is a click that meant nothing, and a half Compact is not something a turn's stop
     * should touch (D-17) — which is also why a turn that is only <em>waiting</em> behind an action cannot
     * be stopped yet: it shares this monitor's one cancel flag with that action, so the same click is
     * accepted as soon as the action has released the project.</p>
     */
    void stop() {
        if (!claim.turnIsStoppable()) {
            throw new IllegalStateException("no turn is running");
        }
        monitor.cancel();
    }

    /**
     * The chat as the memory has it: user text, Jon's answers, and the final answer of a team member —
     * everything else skipped, so a system prompt, a tool call, an ordinary tool result and an empty
     * message are not chat lines (D4). A queued input keeps the core's {@code [Queued Message]} marker,
     * it is reality and not the factory's to hide (Q6).
     *
     * <p>Every line states its author (R-WP-4, UC-WP-39): Jon by name, the member by name, the typed
     * input by the name his message carries (R-WP-25). So the team's answer is read back out of the one
     * history file the core already keeps — no second store, and nothing read out of a text that is not
     * the factory's own: the single thing parsed here is the prefix this component wrote in front of a
     * send, by {@link UserName}, at the two positions that one writes it at (D-6-3).</p>
     */
    List<ChatLine> history() {
        List<ChatLine> lines = new ArrayList<>();
        for (ChatMessage message : jon.getMemory().getCopy()) {
            if (message instanceof UserMessage typed) {
                UserName.Parsed sender = UserName.parse(textOf(typed));
                if (!sender.text().isBlank()) {
                    lines.add(ChatLine.user(sender.text(), sender.name()));
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
