package org.sterl.llmpeon.compact;

import org.jspecify.annotations.Nullable;
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
}
