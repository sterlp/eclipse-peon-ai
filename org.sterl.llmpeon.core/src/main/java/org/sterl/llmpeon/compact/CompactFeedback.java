package org.sterl.llmpeon.compact;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * The compact's user-facing wording and visibility policy — one home, no scattered literals
 * (R-CT-6). Pure: no monitor, no model call, headless testable.
 */
public final class CompactFeedback {

    /**
     * The one start-line prefix (R-CT-6) — the UI and the tests key on it, so a wording change
     * here moves every reader instead of leaving an absence-assertion silently vacuous.
     */
    public static final String START_PREFIX = "🗜 Compressing ";

    /** When (R-CT-7) a compact makes the chat view rebuild itself. */
    public enum Timing {
        /** No rebuild: nothing ran, it failed, or another agent is shown. */
        NONE,
        /** Rebuild now — no turn was in flight, the memory already holds everything the chat shows. */
        NOW,
        /** Rebuild at the shown agent's next turn start: its fresh message is not in the memory yet. */
        DEFERRED
    }

    private CompactFeedback() {}

    /**
     * {@code 🗜 Compressing Da Mek: 3 messages 124 tokens (estimate) using compact-model} — the token
     * figure is always an estimate (a compact runs before any provider value for this request), and
     * the model part is dropped when the COMPACT slot has no own model (empty means unset).
     */
    public static String startLine(String agentName, int messageCount, long estimatedTokens,
                                   @Nullable String model) {
        return START_PREFIX + agentName + ": " + messageCount + " messages " + estimatedTokens
                + " tokens (estimate)" + (StringUtil.hasValue(model) ? " using " + model : "");
    }

    /**
     * R-CT-7: only a successful compact of the agent the view is showing rebuilds it. A slave's
     * memory is not the chat's content (its compact shows in the roster instead), and a failure left
     * the memory untouched — a rebuild would only throw away a transcript that never changed.
     * <p>
     * A compact that ran inside a turn is deferred, not immediate: the pre-turn trigger (R-CC-20.2)
     * runs before that turn's message enters the memory, so rebuilding right away would drop a
     * question the chat already shows. {@code onCallStart} is the earliest event whose memory holds
     * it, so that is the flush point.
     *
     * @param result              the compact's outcome; {@code null} when the run died before one
     * @param compactedAgentName  the agent that compacted, as carried by the event
     * @param activeAgentName     the agent the view renders, {@code null} when there is none
     * @param withinTurn          whether a turn held the working flag while the compact ran
     */
    public static Timing resetTiming(CompactResult result, String compactedAgentName,
                                     @Nullable String activeAgentName, boolean withinTurn) {
        if (result == null || result.status() != CompactResult.Status.COMPACTED) return Timing.NONE;
        if (activeAgentName == null || !activeAgentName.equals(compactedAgentName)) return Timing.NONE;
        return withinTurn ? Timing.DEFERRED : Timing.NOW;
    }
}
