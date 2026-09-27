package org.sterl.llmpeon.ai;

import java.util.Set;

/**
 * Resolves the per-agent "think" string into provider-specific thinking/reasoning values.
 *
 * <p>Blank ({@code null} or empty) is <em>unset</em>: no thinking/reasoning parameter is sent at
 * all — the model decides. Explicit off tokens ({@code "false"}, {@code "off"}, {@code "no"},
 * {@code "none"} — case-insensitive, trimmed) disable thinking where the provider knows the
 * concept: Ollama sends {@code think:false} ({@link #toOllamaThink}), LM Studio sends
 * {@code reasoning:off} ({@link #toReasoning}); OpenAI/Anthropic have no off concept and omit the
 * parameter. Everything else ({@code "true"}/{@code "on"}/{@code "yes"} or an explicit level like
 * {@code "high"}/{@code "medium"}/{@code "low"}/{@code "minimal"}) enables thinking.</p>
 */
public final class ThinkResolver {

    private static final Set<String> OFF = Set.of("", "false", "off", "no", "none");
    private static final Set<String> ON = Set.of("true", "on", "yes");

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
     *         provider/model {@link ThinkModelMapping}.
     */
    public static boolean isGenericOn(String think) {
        return ON.contains(norm(think));
    }

    /**
     * OpenAI {@code reasoning.effort} value. Returns {@code null} when reasoning must not be sent at
     * all. {@code "true"}/{@code "on"}/{@code "yes"} map to {@code "high"}; explicit levels pass through.
     */
    public static String toReasoningEffort(String think) {
        var v = norm(think);
        if (OFF.contains(v)) return null;
        if (ON.contains(v)) return "high";
        return v;
    }

    /** LM Studio custom {@code reasoning} value: {@code "on"} or {@code null} (omit). */
    public static String toOnOff(String think) {
        return isOff(think) ? null : "on";
    }

    /** Ollama {@code think} flag: {@link Boolean#TRUE} or {@code null} (omit). */
    public static Boolean toBoolean(String think) {
        return isOff(think) ? null : Boolean.TRUE;
    }

    /** Ollama {@code think} flag: {@code null} (omit) when unset/blank; {@code FALSE} for an off token; else {@code TRUE}. */
    public static Boolean toOllamaThink(String think) {
        var v = norm(think);
        if (v.isEmpty()) return null;
        return OFF.contains(v) ? Boolean.FALSE : Boolean.TRUE;
    }
    
    /** LM Studio custom {@code reasoning}: {@code null} (omit) when unset/blank; {@code "off"} for an
     *  off token; {@code "on"} for a generic on ({@code true}/{@code on}/{@code yes}); else verbatim. */
    public static String toReasoning(String think) {
        var v = norm(think);
        if (v.isEmpty()) return null;
        if (OFF.contains(v)) return "off";
        if (ON.contains(v)) return "on";
        return think;
    }
}
