package org.sterl.llmpeon.ai;

import java.util.Set;

/**
 * Resolves the per-agent "think" string into provider-specific thinking/reasoning values.
 *
 * <p>Blank ({@code null} or empty) is <em>unset</em>: no thinking/reasoning parameter is sent at
 * all — the model decides. The shared off/on token sets ({@link #offTokens}/{@link #onTokens}) are
 * the frozen generic vocabulary behind the derived {@link #isOff}/{@link #isOn} flags (used by the
 * {@code isThinkSupported} consumers) and the Anthropic generic-on {@link ThinkModelMapping}. The
 * OpenAI family and LM Studio send the stored value <em>verbatim</em> (ADR-0064) — no mapping, no
 * normalization. Only the Ollama toggle interprets the value ({@link #toOllamaThink}), which
 * understands the extra German off-token {@code nein} in its local set.</p>
 */
public final class ThinkResolver {

    private static final Set<String> OFF = Set.of("", "false", "off", "no", "none");
    private static final Set<String> ON = Set.of("true", "on", "yes");
    /** Ollama toggle off-tokens: the shared off set (sans blank) plus the German {@code nein}. */
    private static final Set<String> TOGGLE_OFF = Set.of("false", "off", "no", "nein", "none");

    private ThinkResolver() {}

    /** Generic <em>on</em> tokens the resolver understands ({@code true}/{@code on}/{@code yes}). */
    public static Set<String> onTokens() {
        return ON;
    }

    /** Generic <em>off</em> tokens the resolver understands ({@code ""}/{@code false}/{@code off}/{@code no}/{@code none}). */
    public static Set<String> offTokens() {
        return OFF;
    }

    public static boolean isTrue(String think) {
        return "true".equals(think);
    }

    public static boolean isFalse(String think) {
        return "false".equals(think);
    }

    private static String norm(String think) {
        return think == null ? "" : think.trim().toLowerCase();
    }

    /** @return {@code true} if thinking is off (or unset). */
    public static boolean isOff(String think) {
        return OFF.contains(norm(think));
    }

    /** @return {@code true} if thinking is on. */
    public static boolean isOn(String think) {
        return !isOff(think);
    }

    /**
     * @return {@code true} if the value is a <em>generic</em> on ({@code true}/{@code on}/{@code yes})
     *         rather than a concrete level like {@code high}. Generic-on is what triggers the
     *         Anthropic {@link ThinkModelMapping}.
     */
    public static boolean isGenericOn(String think) {
        return ON.contains(norm(think));
    }

    /**
     * Ollama {@code think} flag: {@code null} (omit) when unset/blank; {@code FALSE} for a toggle-off
     * token ({@code false}/{@code off}/{@code no}/{@code nein}/{@code none}, case-insensitive); else
     * {@code TRUE}. This is the only provider that interprets the value — the verbatim channel sends
     * it as-is (ADR-0064).
     */
    public static Boolean toOllamaThink(String think) {
        var v = norm(think);
        if (v.isEmpty()) return null;
        return TOGGLE_OFF.contains(v) ? Boolean.FALSE : Boolean.TRUE;
    }
}
