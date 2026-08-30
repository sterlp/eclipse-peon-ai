package org.sterl.llmpeon.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;

import dev.langchain4j.model.anthropic.AnthropicChatRequestParameters;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;

/**
 * Extra body injection (2a §4, BDD 12–17): per-request providers merge the agent's
 * {@code extraBody} into {@code customParameters} with the user body winning on key conflicts
 * (PO decision 2026-08-28); Anthropic bakes it in at build time. No body ⇒
 * {@code customParameters} untouched (byte-identical to pre-2a).
 */
class ExtraBodyRequestTest {

    private AgentConfig agent(AiProvider p, String model, String extraBody) {
        return agent(p, model, null, extraBody);
    }

    private AgentConfig agent(AiProvider p, String model, String think, String extraBody) {
        return AgentConfig.builder().provider(p).model(model).think(think).extraBody(extraBody).build();
    }

    private OpenAiChatRequestParameters params(AiProvider p, AgentConfig mc) {
        return (OpenAiChatRequestParameters) LlmProviders.of(p).newRequestParameters(mc, List.of());
    }

    @Test
    void perRequestBodyIsInjectedIntoCustomParameters() {
        // GIVEN an OpenAI agent with a valid extra body
        var mc = agent(AiProvider.OPEN_AI, "gpt-5.5", "{\"foo\":1,\"bar\":\"baz\"}");
        // WHEN newRequestParameters
        var params = params(AiProvider.OPEN_AI, mc);
        // THEN customParameters carries the body keys
        assertThat(params.customParameters())
                .containsEntry("foo", 1)
                .containsEntry("bar", "baz");
    }

    @Test
    void openAiClaudeModel_noUserBody_noCacheControlSent() {
        // GIVEN a claude model without an extra body (clean break: the provider hardcode is gone)
        var mc = agent(AiProvider.OPEN_AI, "claude-sonnet-4-5", null);
        // WHEN newRequestParameters
        var params = params(AiProvider.OPEN_AI, mc);
        // THEN no cache_control is sent (caching.md: no cache until the user configures one)
        assertThat(params.customParameters()).doesNotContainKey("cache_control");
    }

    @Test
    void userBodyCacheControl_reachesRequest() {
        // GIVEN the Claude example body (cache_control=ephemeral)
        var mc = agent(AiProvider.OPEN_AI, "claude-sonnet-4-5",
                "{\"cache_control\":{\"type\":\"ephemeral\"}}");
        // WHEN newRequestParameters
        var params = params(AiProvider.OPEN_AI, mc);
        // THEN the example reaches the request verbatim (the pasted body actually works)
        assertThat(params.customParameters())
                .containsEntry("cache_control", Map.of("type", "ephemeral"));
    }

    @Test
    void mergeUserWins_overProviderEntry() {
        // GIVEN a provider entry and a user body with the same key
        var mc = agent(AiProvider.OPEN_AI, "claude-sonnet-4-5",
                "{\"cache_control\":{\"type\":\"persistent\"}}");
        // WHEN mergeCustomParameters
        var merged = ProviderRequestSupport.mergeCustomParameters(
                Map.of("cache_control", Map.of("type", "ephemeral")), mc);
        // THEN the user body wins (PO decision 2026-08-28)
        assertThat(merged)
                .containsEntry("cache_control", Map.of("type", "persistent"));
    }

    @Test
    void noBodyLeavesCustomParametersUntouched() {
        // GIVEN no body, no claude model, no think
        var mc = agent(AiProvider.OPEN_AI, "gpt-5.5", null);
        // WHEN newRequestParameters
        var params = params(AiProvider.OPEN_AI, mc);
        // THEN customParameters is empty (byte-identical to pre-2a; the library normalizes unset to {})
        assertThat(params.customParameters()).isEmpty();
    }

    @Test
    void invalidBodyIsIgnoredCallProceeds() {
        // GIVEN an invalid extra body
        var mc = agent(AiProvider.OPEN_AI, "gpt-5.5", "{invalid");
        // WHEN newRequestParameters
        var params = params(AiProvider.OPEN_AI, mc);
        // THEN parameters are as if there were no body (no throw)
        assertThat(params.customParameters()).isEmpty();
        assertThat(params.modelName()).isEqualTo("gpt-5.5");
    }

    @Test
    void lmStudioReasoningAndBodyMerge() {
        // GIVEN think set + a body without a reasoning key
        var merged = params(AiProvider.LM_STUDIO,
                agent(AiProvider.LM_STUDIO, "m", "high", "{\"foo\":1}"));
        // THEN the reasoning entry and the body keys coexist
        assertThat(merged.customParameters())
                .containsEntry("reasoning", "high")
                .containsEntry("foo", 1);

        // GIVEN a body with a conflicting reasoning key
        var conflict = params(AiProvider.LM_STUDIO,
                agent(AiProvider.LM_STUDIO, "m", "high", "{\"reasoning\":\"low\"}"));
        // THEN the user body wins (PO decision 2026-08-28)
        assertThat(conflict.customParameters())
                .containsEntry("reasoning", "low");
    }

    @Test
    void anthropicBuildTimeBodyAppliedAtBuild() throws Exception {
        // GIVEN an Anthropic build config carrying a raw extra body
        var config = anthropicConfig("{\"foo\":1}");
        // WHEN the model is built
        var model = (AnthropicStreamingChatModel) LlmProviders.of(AiProvider.ANTHROPIC).buildModel(config);
        // THEN the body entries are baked into the model's custom parameters (build-time key entity)
        assertThat(model).isNotNull();
        assertThat(customParameters(model)).containsEntry("foo", 1);
    }

    @Test
    void anthropicBuild_noCacheFlagsByDefault() throws Exception {
        // GIVEN an Anthropic build config without extra body (clean break)
        var config = anthropicConfig(null);
        // WHEN the model is built
        var model = (AnthropicStreamingChatModel) LlmProviders.of(AiProvider.ANTHROPIC).buildModel(config);
        // THEN no native caching is enabled by default (the typed flags are absent/false)
        var defaults = (AnthropicChatRequestParameters) field(model, "defaultRequestParameters");
        assertThat(defaults.cacheSystemMessages()).isNotEqualTo(Boolean.TRUE);
        assertThat(defaults.cacheTools()).isNotEqualTo(Boolean.TRUE);
    }

    @Test
    void anthropicBuildWithoutBodyLeavesCustomParametersEmpty() throws Exception {
        // GIVEN an Anthropic build config without extra body
        var config = anthropicConfig(null);
        // WHEN the model is built
        var model = (AnthropicStreamingChatModel) LlmProviders.of(AiProvider.ANTHROPIC).buildModel(config);
        // THEN customParameters stays empty (byte-identical to pre-2a; the library normalizes unset to {})
        assertThat(customParameters(model)).isEmpty();
    }

    private static LlmConfig anthropicConfig(String extraBody) {
        return LlmConfig.builder()
                .providerType(AiProvider.ANTHROPIC)
                .model("claude-sonnet-4-5")
                .url("http://localhost:1")
                .apiKey("k")
                .extraBody(extraBody)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> customParameters(AnthropicStreamingChatModel model) throws Exception {
        return (Map<String, Object>) field(model, "customParameters");
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
