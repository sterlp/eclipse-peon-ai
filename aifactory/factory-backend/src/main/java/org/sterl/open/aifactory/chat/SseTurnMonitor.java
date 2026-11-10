package org.sterl.open.aifactory.chat;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.AiMonitor.AiFileUpdate;
import org.sterl.llmpeon.shared.CallStats;
import org.sterl.llmpeon.shared.OnPartialAiResponse;
import org.sterl.llmpeon.shared.SimpleDiff;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.open.aifactory.chat.dto.AgentTileDto;

/**
 * The one place that knows the core's callback language: every {@link AiMonitor} callback of a project
 * becomes a {@link ChatEvent} here, and nothing else. One instance per project, never per turn — the
 * queue drain re-uses the agent's monitor, so a per-turn monitor would lose the streaming events of the
 * second input.
 *
 * <p><b>What reaches the browser</b> (R-WP-4): the core stamps every callback with the agent that produced
 * it, and this class decides <em>which wire</em> a line takes — the lines stamped with the conversation's
 * agent, plus the lines carrying no stamp. A member's thinking, tool lines and half answers are his own
 * conversation and go <b>by their stamp</b> to his own wire, never into Jon's chat (R-WP-53). The
 * delegation's trace lines stay: Jon's tool emitted them under his own name. The rule is policy and so lives
 * the factory, not in the core: the Eclipse plugin hands the same callback stream to its views and goes on
 * showing every member line.</p>
 *
 * <p><b>A file write is a block, not a line.</b> {@link #onFileUpdate} is the callback that carries the
 * content of a change instead of a chat line, and the one that arrives <em>unstamped</em>:
 * {@code OwnerMonitor} passes it through untouched, so no origin filter can apply to it — a diff of a
 * team member's write belongs to the project's chat exactly as Jon's does (R-WP-36, UC-WP-68).
 * Overriding it also retires {@code AiMonitor}'s default, the {@code Updated: <file>} notice: the block
 * names its own file, and the plugin, which renders the same callback, shows no such line either.</p>
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
    private final Function<String, List<AgentTileDto>> roster;
    private final Consumer<String> sawOrigin;

    /** The stop of UC-WP-50 — read by the core between its model calls, set by {@link ProjectRuntime#stop()}. */
    private volatile boolean canceled;

    /**
     * @param roster    the project's tiles, asked again per callback — the monitor keeps no roster of its
     *                  own, so a snapshot cannot be stale (R-WP-7: snapshot, no accumulation). The argument
     *                  names the member a delegation just started — the core sets his working flag only
     *                  inside {@code call()}, so the delegation itself is what makes his tile answer
     *                  (R-WP-60) — or {@code null} for every other callback
     * @param sawOrigin every stamp this monitor sees, also the ones whose lines it drops — the search
     *                  sub-loop owns no agent instance and is known to the tiles only through his lines
     */
    SseTurnMonitor(String projectId, ChatStreamRegistry streams, Function<String, List<AgentTileDto>> roster,
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
     * Called by the runtime <b>before</b> every call to Jon and <b>before</b> every tile action: without it
     * the first stop would stop every later turn of the project too, and a Compact, which never resets,
     * would keep failing against the flag of a stop that ended long before it — because a project keeps one
     * monitor for its whole life.
     */
    void resetCancel() {
        canceled = false;
    }

    @Override
    public void onStreamingChunk(OnPartialAiResponse chunk) {
        ChatEvent.Partial partial = partialOf(chunk);
        if (partial == null) {
            return;
        }
        if (isJon(chunk.origin())) {
            streams.publish(projectId, partial);
        } else {
            // his stamp sends his thinking and his half answers to his own wire (R-WP-53, UC-WP-90)
            streams.publishAgent(projectId, chunk.origin(), partial);
        }
    }

    /** The live line of one chunk, or {@code null} when the chunk carries no text to show (see the class doc). */
    private static ChatEvent.Partial partialOf(OnPartialAiResponse chunk) {
        return switch (chunk.type()) {
            case THINK -> new ChatEvent.Partial("think", chunk.value());
            case ANSWER -> new ChatEvent.Partial("answer", chunk.value());
            case START, TOOL, END -> null;
        };
    }

    @Override
    public void onChatResponse(SimpleMessage message) {
        sawOrigin.accept(message.origin());
        // the tiles ride along before the origin filter: who is working is a fact about the team, and a
        // member's line is exactly the moment his tile lights up — a snapshot, so order never matters
        streams.publish(projectId, new ChatEvent.Agents(roster.apply(null)));
        if (message.role() != SimpleMessage.Type.TOOL && message.role() != SimpleMessage.Type.PROBLEM) {
            return;
        }
        // the stamp rides along: a step names its agent only where the core stamped one (R-WP-40, UC-WP-72)
        ChatEvent.ToolLine step = new ChatEvent.ToolLine(message.message(), message.origin());
        if (isJon(message.origin())) {
            streams.publish(projectId, step);
        } else {
            streams.publishAgent(projectId, message.origin(), step);
        }
    }

    /**
     * The core's diff of one written file, straight to the wire (R-WP-36). Computed here and not in the
     * browser because the core already owns the algorithm and its three answers — empty, a real diff, a
     * binary summary — and deciding them twice would be a second truth about the same change.
     */
    @Override
    public void onFileUpdate(AiFileUpdate update) {
        String diff = SimpleDiff.unifiedDiff(update.file(), update.oldContent(), update.newContent());
        if (diff.isEmpty()) {
            return;                                  // nothing changed: a block of nothing would claim a change
        }
        if (diff.startsWith("--- a/")) {             // SimpleDiff's own contract for a computed diff
            streams.publish(projectId, new ChatEvent.Diff(update.file(), diff));
            return;
        }
        // the summary of content the core will not diff (binary) — same fallback as the plugin (AIChatView.onFileUpdate)
        // and no stamp to hand out: this callback names no author, so the step stays unnamed (UC-WP-68)
        streams.publish(projectId, new ChatEvent.ToolLine(diff, null));
    }

    /**
     * A delegation began, said by the delegation tool's own listener and not by a stamped callback — so the
     * origin filter of {@link #isJon(String)} has no part here: the event names a member without carrying a
     * word of his (R-WP-40, UC-WP-72).
     */
    void onDelegationStart(CallStats stats, AiAgent member) {
        publishDelegation(member, true);
    }

    /** The same delegation ended. The browser closes its entry on this event and on nothing else. */
    void onDelegationEnd(CallStats stats, AiAgent member) {
        publishDelegation(member, false);
    }

    /**
     * Both events of a delegation are {@code (CallStats, AiAgent)} — the core hands no order text and no
     * tool-call id, so the member is all the wire can say and the entry says nothing else. A member without
     * a name is the one reason this translation withholds an event: a line naming nobody states nothing, and
     * an event naming nobody has no tile to report either. The tile snapshot rides with both directions
     * (R-WP-60): the member's own flag is set only inside his {@code call()}, so without this his tile would
     * light on his first line and go out on the next stranger's event.
     */
    private void publishDelegation(AiAgent member, boolean working) {
        if (member == null || !StringUtil.hasValue(member.getName())) {
            return;
        }
        streams.publish(projectId, new ChatEvent.Delegation(member.getName(), working));
        streams.publish(projectId, new ChatEvent.Agents(roster.apply(working ? member.getName() : null)));
    }

    /** The core has released its compacting flag here — the end state of UC-WP-13 goes out on this callback. */
    @Override
    public void onCompactCompleted(String agentName, CompactResult result) {
        streams.publish(projectId, new ChatEvent.Agents(roster.apply(null)));
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
