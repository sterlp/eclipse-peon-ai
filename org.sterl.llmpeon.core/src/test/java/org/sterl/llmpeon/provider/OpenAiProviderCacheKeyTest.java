package org.sterl.llmpeon.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.AiProvider;

import dev.langchain4j.model.openai.OpenAiChatRequestParameters;

/**
 * R8 — GPT default cache key: gpt-5* models (case-insensitive prefix) get a stable per-agent
 * {@code prompt_cache_key} as a provider entry; a non-blank user value in the extra body wins,
 * an empty body {@code {}} or {@code prompt_cache_key: ""} count as unset (the default stays).
 * No model or no agent id → no key (byte-identical to pre-R8).
 */
class OpenAiProviderCacheKeyTest {

    private AgentConfig agent(String model, String id, String extraBody) {
        return AgentConfig.builder()
                .provider(AiProvider.OPEN_AI)
                .model(model)
                .id(id)
                .extraBody(extraBody)
                .build();
    }

    private OpenAiChatRequestParameters params(AgentConfig mc) {
        return (OpenAiChatRequestParameters) LlmProviders.of(AiProvider.OPEN_AI).newRequestParameters(mc, List.of());
    }

    @Test
    void gpt5NoBody_defaultKeyInjected() {
        // GIVEN a gpt-5* agent with a stable id and no extra body
        var mc = agent("gpt-5.6", "plan", null);
        // WHEN newRequestParameters
        var params = params(mc);
        // THEN the per-agent default cache key is on the request
        assertThat(params.customParameters()).containsEntry("prompt_cache_key", "peon-ai-plan");
    }

    @Test
    void gpt5PrefixMatchIsCaseInsensitive() {
        // GIVEN a model with an upper-case gpt-5 prefix
        var mc = agent("GPT-5.6-turbo", "plan", null);
        // WHEN newRequestParameters
        var params = params(mc);
        // THEN the default key is injected (Locale.ROOT prefix match)
        assertThat(params.customParameters()).containsEntry("prompt_cache_key", "peon-ai-plan");
    }

    @Test
    void gpt5UserBody_winsOverDefault() {
        // GIVEN a gpt-5* agent whose extra body sets a non-blank prompt_cache_key
        var mc = agent("gpt-5.6", "plan", "{\"prompt_cache_key\":\"custom\"}");
        // WHEN newRequestParameters
        var params = params(mc);
        // THEN the user value wins over the provider default (2a merge)
        assertThat(params.customParameters()).containsEntry("prompt_cache_key", "custom");
    }

    @Test
    void gpt5EmptyStringValue_defaultKept() {
        // GIVEN a gpt-5* agent whose extra body sets prompt_cache_key to "" (unset per R8)
        var mc = agent("gpt-5.6", "plan", "{\"prompt_cache_key\":\"\"}");
        // WHEN newRequestParameters
        var params = params(mc);
        // THEN the default stays (a blank user value does not override the provider entry)
        assertThat(params.customParameters()).containsEntry("prompt_cache_key", "peon-ai-plan");
    }

    @Test
    void gpt5EmptyObjectBody_defaultKept() {
        // GIVEN a gpt-5* agent with an empty extra body {}
        var mc = agent("gpt-5.6", "plan", "{}");
        // WHEN newRequestParameters
        var params = params(mc);
        // THEN the default stays (an empty body does not override the provider entry)
        assertThat(params.customParameters()).containsEntry("prompt_cache_key", "peon-ai-plan");
    }

    @Test
    void nonGpt5Model_noKey() {
        // GIVEN an agent on a model without the gpt-5 prefix
        var mc = agent("deepseek-chat", "plan", null);
        // WHEN newRequestParameters
        var params = params(mc);
        // THEN no prompt_cache_key is sent (no foreign parameter on foreign endpoints)
        assertThat(params.customParameters()).isEmpty();
    }

    @Test
    void noAgentId_noKey() {
        // GIVEN a gpt-5* agent without a stable id (test-built configs must not emit peon-ai-null)
        var mc = agent("gpt-5.6", null, null);
        // WHEN newRequestParameters
        var params = params(mc);
        // THEN no key is injected (byte-identical to pre-R8)
        assertThat(params.customParameters()).isEmpty();
    }

    @Test
    void mergeBlankValue_doesNotOverrideProviderDefault() {
        // GIVEN provider entries plus a user body with a blank value for the same key
        var mc = agent("gpt-5.6", "plan", "{\"prompt_cache_key\":\"\"}");
        // WHEN mergeCustomParameters
        var merged = ProviderRequestSupport.mergeCustomParameters(
                Map.of("prompt_cache_key", "peon-ai-plan"), mc);
        // THEN the provider default survives the blank user value
        assertThat(merged).containsEntry("prompt_cache_key", "peon-ai-plan");

        // GIVEN a lone blank user key that is NOT in the provider entries
        var lone = agent("gpt-5.6", "plan", "{\"foo\":\"\"}");
        // WHEN mergeCustomParameters
        var passed = ProviderRequestSupport.mergeCustomParameters(
                Map.of("prompt_cache_key", "d"), lone);
        // THEN it passes through unchanged (the unset rule is scoped to provider keys)
        assertThat(passed)
                .containsEntry("prompt_cache_key", "d")
                .containsEntry("foo", "");
    }
}
