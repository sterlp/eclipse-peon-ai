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
     * R-CT-7: the chat view rebuilds right away exactly when a compact succeeded for the agent it
     * shows. The agent owns the timing (a pre-turn compact is announced only once the fresh message
     * is in the memory), so the view needs no turn knowledge; a slave's compact or a failure never
     * resets it (UC-CT-11, UC-CT-12).
     *
     * @param result              the compact's outcome; {@code null} when the run died before one
     * @param compactedAgentName  the agent that compacted, as carried by the event
     * @param activeAgentName     the agent the view renders, {@code null} when there is none
     */
    public static boolean resetsChat(@Nullable CompactResult result, String compactedAgentName,
                                     @Nullable String activeAgentName) {
        return result != null && result.status() == CompactResult.Status.COMPACTED
                && activeAgentName != null && activeAgentName.equals(compactedAgentName);
    }
}
