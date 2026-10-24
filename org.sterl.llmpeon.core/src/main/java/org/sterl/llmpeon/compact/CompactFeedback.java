package org.sterl.llmpeon.compact;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.shared.CallStats;
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

    /**
     * The one success end-line marker (R-CT-8) — production and tests key on it, so a wording change
     * moves every reader instead of leaving an absence-assertion silently vacuous.
     */
    public static final String END_MARKER = " compacted: ";

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
     * R-CT-8: the one end-line every successful compact sends via {@code onTool} — trigger-blind and
     * uniform, so the root and a slave read identically. The {@code before} figures are the stager's
     * ({@link CompactResult.Stats}), the {@code after} figures the memory's own right after the re-seed, so
     * the line answers "how big is the result" rather than "how big was the input". Duration and wall
     * time come from the shared {@link CallStats} suffix (ADR-0053) — never formatted here.
     *
     * @param messageCountAfter memory size after the re-seed
     * @param tokensAfter       memory token total after the re-seed (an estimate for the fresh seed)
     * @param callStats         started at the compact's visible start; rendered now, so the wall time
     *                          is the moment the line is built
     */
    public static String endLine(String agentName, CompactResult result, int messageCountAfter,
                                 int tokensAfter, CallStats callStats) {
        var stats = result.stats();
        return "🗜 " + agentName + END_MARKER + stats.messageCount() + " → " + messageCountAfter + " messages, ~"
                + tokens(stats.estimateBefore()) + " → ~" + tokens(tokensAfter) + " tokens "
                + callStats.suffix() + " " + StringUtil.charsSuffix(result.summary());
    }

    /** Token display for the end-line: under 1000 raw (never {@code "0k"}), above compact as k/M. */
    private static String tokens(int value) {
        return value < 1000 ? String.valueOf(value) : StringUtil.toK(value);
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
