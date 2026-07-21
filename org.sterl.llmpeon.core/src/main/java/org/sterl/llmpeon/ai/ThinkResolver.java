package org.sterl.llmpeon.ai;

import java.util.Set;

import org.sterl.llmpeon.shared.StringUtil;

/**
 * Resolves the per-agent "think" string into provider-specific thinking/reasoning values.
 *
 * <p>The string IS the effort. The following are treated as "off" and always cause the thinking
 * property to be <b>omitted entirely</b> (each provider then applies its own default): {@code null},
 * {@code ""}, {@code "false"}, {@code "off"}, {@code "no"}, {@code "none"}. Everything else
 * ({@code "true"}/{@code "on"}/{@code "yes"} or an explicit level like {@code "high"}/{@code "medium"}/
 * {@code "low"}/{@code "minimal"}) enables thinking.</p>
 *
 * <p>Consequence (intentional): for the "off" set we send nothing about thinking to the LLM, so each
 * provider's own default applies — a provider with native {@code false} semantics (Ollama's
 * {@code think}) reacts itself; OpenAI simply omits the {@code reasoning} field.</p>
 */
public final class ThinkResolver {

    private static final Set<String> OFF = Set.of("", "false", "off", "no", "none");
    private static final Set<String> ON = Set.of("true", "on", "yes");

    private ThinkResolver() {}
    
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

    /**
     * Effective per-agent think string. Both strings empty = auto: {@code "true"} (heuristic marker)
     * when enabled, {@code ""} (off) when disabled. Any string set = manual: the active string is
     * used verbatim (empty active string = off), and the heuristic never applies.
     */
    public static String effectiveThink(boolean enabled, String on, String off) {
        boolean auto = StringUtil.hasNoValue(on) && StringUtil.hasNoValue(off);
        if (enabled) return auto ? "true" : StringUtil.stripToEmpty(on);
        return auto ? "" : StringUtil.stripToEmpty(off);
    }

    /** Ollama {@code think} flag: {@code null} (omit) when empty; {@code FALSE} for an explicit off-token; else {@code TRUE}. */
    public static Boolean toOllamaThink(String think) {
        var v = norm(think);
        if (v.isEmpty()) return null;
        return OFF.contains(v) ? Boolean.FALSE : Boolean.TRUE;
    }
    
    /** LM Studio custom {@code reasoning}: {@code null} (omit) when empty; {@code "off"} for an explicit off-token; else {@code "on"}. */
    public static String toReasoning(String think) {
        var v = norm(think);
        if (v.isEmpty()) return null;
        if (isTrue(think)) return "on";
        if (isFalse(think)) return "off";
        return think;
    }
}
