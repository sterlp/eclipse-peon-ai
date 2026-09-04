package org.sterl.llmpeon.provider;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.SharedHttpClient;
import org.sterl.llmpeon.ai.ThinkModelMapping;
import org.sterl.llmpeon.ai.ThinkResolver;
import org.sterl.llmpeon.shared.StringUtil;

import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatRequestParameters;

/** Shared static request-building helpers for the {@link LlmProvider} classes (1:1 from the old enum). */
public final class ProviderRequestSupport {

    private ProviderRequestSupport() {
    }

    /** Streaming only needs to cover time-to-first-token (connect + model warmup), not the full response duration. */
    public static final Duration MODEL_TIMEOUT = SharedHttpClient.MODEL_TIMEOUT;

    /** Applies the neutral request fields (modelName, temperature, tools) onto any provider builder. */
    public static void applyBase(DefaultChatRequestParameters.Builder<?> b, AgentConfig mc, List<ToolSpecification> tools) {
        b.temperature(effectiveTemperature(mc));
        if (StringUtil.hasValue(mc.getModel())) b.modelName(mc.getModel());
        if (tools != null && !tools.isEmpty()) b.toolSpecifications(tools);
    }


    /** User extra body wins over the typed field to avoid duplicate temperature keys. */
    static Double effectiveTemperature(AgentConfig mc) {
        if (!LlmProviders.of(mc.getProvider()).supportsExtraBody()) return mc.getTemperature();
        var body = ExtraBody.parse(mc.getExtraBody());
        return body != null && body.containsKey("temperature") && !isAbsentValue(body.get("temperature"))
                ? null : mc.getTemperature();
    }

    /**
     * Merges the provider-computed {@code customParameters} entries with the agent's user
     * extra body (2a §4): user entries are layered over the provider entries, so the user
     * body wins on key conflicts (PO decision 2026-08-28). A user entry whose value is
     * {@code null} or a blank string counts as <i>unset</i> (R8): it does not override a
     * provider-supplied default for the same key, but a lone user key not in the provider
     * entries passes through unchanged.
     *
     * <p>Returns {@code null} when there is nothing to send — the caller must then leave
     * {@code customParameters} untouched, keeping the request byte-identical to pre-2a.
     * {@code providerEntries} may be {@code null} (provider has no own entries).
     */
    public static Map<String, Object> mergeCustomParameters(Map<String, Object> providerEntries, AgentConfig mc) {
        var user = ExtraBody.parse(mc.getExtraBody());
        if (user == null) return providerEntries;
        if (providerEntries == null || providerEntries.isEmpty()) return user;
        var merged = new LinkedHashMap<>(providerEntries);
        user.forEach((key, value) -> {
            if (!isAbsentValue(value) || !providerEntries.containsKey(key)) merged.put(key, value);
        });
        return merged;
    }

    /** An unset user value: {@code null} or a blank string (R8 — does not override a provider default). */
    private static boolean isAbsentValue(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
    }

    /**
     * OpenAI-family {@code reasoning.effort} for the agent's think value (3-stage schema):
     * off -&gt; {@code null} (send nothing); a concrete level -&gt; used verbatim; a generic on
     * ({@code true}/{@code on}) -&gt; the {@link ThinkModelMapping} for the OpenAI family and this
     * model (no known reasoning model -&gt; {@code null}, send nothing).
     */
    public static String effortFor(AgentConfig mc) {
        var think = mc.getThink();
        if (ThinkResolver.isOff(think)) return null;
        if (ThinkResolver.isGenericOn(think)) return ThinkModelMapping.resolveOn(AiProvider.OPEN_AI, mc.getModel());
        return ThinkResolver.toReasoningEffort(think);
    }

    /**
     * Per-agent think form for the OpenAI family: the fixed reasoning-effort values, derived from
     * the OpenAI SDK's {@link ReasoningEffort.Known} so the list tracks the library automatically.
     */
    public static ThinkSupport openAiFamilyThinkSupport() {
        return new ThinkSupport.Values(Arrays.stream(ReasoningEffort.Known.values())
                .map(k -> k.name().toLowerCase(Locale.ROOT))
                .toList());
    }

    /**
     * Anthropic {@code thinkingType} for the agent's think value (3-stage schema):
     * off -&gt; {@code null}; a concrete value ({@code adaptive}/{@code enabled}) -&gt; used verbatim;
     * a generic on -&gt; the {@link ThinkModelMapping} for Anthropic and this model (no match -&gt;
     * {@code null}, send nothing).
     */
    public static String anthropicThinkingType(AgentConfig mc) {
        var think = mc.getThink();
        if (ThinkResolver.isOff(think)) return null;
        if (ThinkResolver.isGenericOn(think)) return ThinkModelMapping.resolveOn(AiProvider.ANTHROPIC, mc.getModel());
        return think.trim().toLowerCase();
    }

    /** Shared reasoning-effort parameters for the OpenAI-official-based providers. */
    public static ChatRequestParameters openAiOfficialParameters(AgentConfig mc, List<ToolSpecification> tools) {
        var b = OpenAiOfficialResponsesChatRequestParameters.builder();
        applyBase(b, mc, tools);
        var effort = effortFor(mc);
        if (effort != null) {
            b.reasoningEffort(ReasoningEffort.of(effort)).reasoningSummary(Reasoning.Summary.DETAILED);
        }
        return b.build();
    }
}
