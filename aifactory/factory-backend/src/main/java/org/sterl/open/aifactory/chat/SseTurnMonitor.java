package org.sterl.open.aifactory.chat;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.OnPartialAiResponse;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.open.aifactory.chat.dto.AgentTileDto;

/**
 * The one place that knows the core's callback language: every {@link AiMonitor} callback of a project
 * becomes a {@link ChatEvent} here, and nothing else. One instance per project, never per turn — the
 * queue drain re-uses the agent's monitor, so a per-turn monitor would lose the streaming events of the
 * second input.
 *
 * <p><b>What reaches the browser</b> (R-WP-4): the core stamps every callback with the agent that produced
 * it, and this class decides who is <em>shown</em> — the lines stamped with the conversation's own agent,
 * plus the lines carrying no stamp. A team member's thinking, tool lines and half answers are its
 * conversation with itself and are dropped here <b>by their stamp</b>, while the delegation's own trace
 * lines stay because Jon's tool emitted them under Jon's name. The rule is policy and therefore lives in
 * the factory, not in the core: the Eclipse plugin hands the same callback stream to its views and goes on
 * showing every member line.</p>
 *
 * <p><b>The member's final answer is not a live line.</b> It has no callback of its own — it is the
 * result of Jon's delegation tool, and the browser reads it as a history line when {@code done} makes it
 * read the history (ADR-0078 Decision 6). So the two channels are disjoint by construction: live is what
 * the conversation's own agent streamed, finished text of anybody is the history's and only the
 * history's.</p>
 *
 * <p><b>Where {@code done} comes from</b>: not here — {@link ProjectRuntime#submit} measures the turn it
 * started and answers it. A callback is the wrong place for the end of a turn: a member's completion is
 * announced to the wrapper chain and stops in the innermost wrapper, so nothing that arrives here says
 * when the caller's turn was over.</p>
 *
 * <p>What is deliberately not forwarded: the conversation agent's own finished answer text. It arrives in
 * the history, and a second copy on the wire would be a second truth (R-WP-11). {@code START}/{@code END}
 * carry no value, {@code TOOL} chunks duplicate the {@code tool} line that {@link #onChatResponse}
 * already sends.</p>
 *
 * <p><b>The monitor is also the stop.</b> Being one per project is what makes {@link #isCanceled()} enough
 * for the whole team: the core hands this same instance to a delegated member
 * ({@code PoDelegateTool.dispatch → target.call(prompt, this.monitor)}, {@code OwnerMonitor} and
 * {@code AsyncMonitor} passing {@code isCanceled()} through) and to the search sub-loop, so one flag ends
 * the turn everywhere at once. That is the Eclipse plugin's route
 * ({@code IProgressMonitor.setCanceled(true)}) without Eclipse, and the reason stopping needs no core
 * change (UC-WP-50).</p>
 */
class SseTurnMonitor implements AiMonitor {

    private final String projectId;
    private final ChatStreamRegistry streams;
    private final Supplier<List<AgentTileDto>> roster;
    private final Consumer<String> sawOrigin;

    /** The stop of UC-WP-50 — read by the core between its model calls, set by {@link ProjectRuntime#stop()}. */
    private volatile boolean canceled;

    /**
     * @param roster    the project's tiles, read again per callback — the monitor keeps no roster of its
     *                  own, so a snapshot cannot be stale (R-WP-7: snapshot, no accumulation)
     * @param sawOrigin every stamp this monitor sees, also the ones whose lines it drops — the search
     *                  sub-loop owns no agent instance and is known to the tiles only through his lines
     */
    SseTurnMonitor(String projectId, ChatStreamRegistry streams, Supplier<List<AgentTileDto>> roster,
            Consumer<String> sawOrigin) {
        this.projectId = projectId;
        this.streams = streams;
        this.roster = roster;
        this.sawOrigin = sawOrigin;
    }

    /**
     * The core's one question for a stop. Overriding the {@link AiMonitor} default of {@code false} is the
     * whole mechanism — the stream handle, the retry backoff, the tool loop and the delegation chain all
     * read this flag, so the factory interrupts no thread and starts no second turn path.
     */
    @Override
    public boolean isCanceled() {
        return canceled;
    }

    /** Asked of the turn that is running now — a turn that has not started yet must not be born stopped. */
    void cancel() {
        canceled = true;
    }

    /**
     * Called by the runtime <b>before</b> every call to Jon: without it the first stop would stop every
     * later turn of the project too, because a project keeps one monitor for its whole life.
     */
    void resetCancel() {
        canceled = false;
    }

    @Override
    public void onStreamingChunk(OnPartialAiResponse chunk) {
        if (!isJon(chunk.origin())) {
            return;
        }
        switch (chunk.type()) {
            case THINK -> streams.publish(projectId, new ChatEvent.Partial("think", chunk.value()));
            case ANSWER -> streams.publish(projectId, new ChatEvent.Partial("answer", chunk.value()));
            case START, TOOL, END -> {
                // no value to show — see the class doc
            }
        }
    }

    @Override
    public void onChatResponse(SimpleMessage message) {
        sawOrigin.accept(message.origin());
        // the tiles ride along before the origin filter: who is working is a fact about the team, and a
        // member's line is exactly the moment his tile lights up — a snapshot, so order never matters
        streams.publish(projectId, new ChatEvent.Agents(roster.get()));
        if (!isJon(message.origin())) {
            return;
        }
        if (message.role() == SimpleMessage.Type.TOOL || message.role() == SimpleMessage.Type.PROBLEM) {
            streams.publish(projectId, new ChatEvent.ToolLine(message.message()));
        }
    }

    /** The core has released its compacting flag here — the end state of UC-WP-13 goes out on this callback. */
    @Override
    public void onCompactCompleted(String agentName, CompactResult result) {
        streams.publish(projectId, new ChatEvent.Agents(roster.get()));
    }

    /**
     * The stamp is the whole decision: shown is what the conversation's own agent said, and an origin that
     * is not his never becomes one of his lines — neither a member's nor a stranger's. No book over open
     * delegations, no tool-call id, nothing remembered from the line before (ADR-0078 Decision 6), so two
     * members delegating in one {@code AiMessage} cannot be told apart by this class and do not have to be.
     *
     * <p>An unstamped line stays visible instead of vanishing: it names no author, so showing it attributes
     * nothing, while dropping it would hide a fact the user should have seen (R-WP-4). Every line of an
     * agent turn carries its stamp, so this branch is the honest fallback and not the rule.</p>
     */
    private boolean isJon(String origin) {
        return origin == null || AgentNames.isJon(origin);
    }
}
