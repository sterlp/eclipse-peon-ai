package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * BDD 1–3 (Plan 2a §7): effective connection resolution.
 */
class EffectiveConnectionTest {

    @Test
    void effectiveConnectionInheritsBaseUntilAgentOverrides() {
        // GIVEN
        LlmConfig base = LlmConfig.of(AiProvider.OPEN_AI).model("gpt-4o")
                .url("http://base:1234/v1").apiKey("base-key").build();
        AgentConfig agent = AgentConfig.builder().provider(AiProvider.OPEN_AI).build();
        // WHEN
        EffectiveConnection effective = EffectiveConnection.of(base, agent);
        // THEN
        assertThat(effective.isBase()).isTrue();
        assertThat(effective.identity()).isEqualTo(
                new ConnectionIdentity(AiProvider.OPEN_AI, "http://base:1234/v1", "base-key", null));
        assertThat(effective.buildConfig().getUrl()).isEqualTo("http://base:1234/v1");
        assertThat(effective.buildConfig().getApiKey()).isEqualTo("base-key");
        assertThat(effective.perRequestBody()).isNull();
    }

    @Test
    void effectiveConnectionUsesAgentUrlAndKey() {
        // GIVEN
        LlmConfig base = LlmConfig.of(AiProvider.OPEN_AI).model("gpt-4o")
                .url("http://base:1234/v1").apiKey("base-key").build();
        AgentConfig agent = AgentConfig.builder().provider(AiProvider.OPEN_AI)
                .url("http://agent:5678/v1").apiKey("agent-key").build();
        // WHEN
        EffectiveConnection effective = EffectiveConnection.of(base, agent);
        // THEN
        assertThat(effective.isBase()).isFalse();
        assertThat(effective.identity().url()).isEqualTo("http://agent:5678/v1");
        assertThat(effective.identity().apiKey()).isEqualTo("agent-key");
        assertThat(effective.identity().provider()).isEqualTo(AiProvider.OPEN_AI);
        assertThat(effective.buildConfig().getUrl()).isEqualTo("http://agent:5678/v1");
        assertThat(effective.buildConfig().getApiKey()).isEqualTo("agent-key");
    }

    @Test
    void buildTimeBodyIsInIdentityOnlyForBuildTimeProviders() {
        String body = "{\"foo\":1}";
        // GIVEN an Anthropic agent with extraBody
        EffectiveConnection anthropic = EffectiveConnection.of(
                LlmConfig.of(AiProvider.ANTHROPIC).model("claude").url("https://api.anthropic.com").build(),
                AgentConfig.builder().provider(AiProvider.ANTHROPIC).extraBody(body).build());
        // THEN the body is part of the connection identity (build-time)
        assertThat(anthropic.identity().buildTimeBody()).isEqualTo(body);
        assertThat(anthropic.buildConfig().getExtraBody()).isEqualTo(body);
        assertThat(anthropic.perRequestBody()).isNull();
        // GIVEN an OpenAI agent with extraBody
        EffectiveConnection openAi = EffectiveConnection.of(
                LlmConfig.of(AiProvider.OPEN_AI).model("gpt").url("http://base:1234/v1").build(),
                AgentConfig.builder().provider(AiProvider.OPEN_AI).extraBody(body).build());
        // THEN the body is per-request, not part of the identity
        assertThat(openAi.identity().buildTimeBody()).isNull();
        assertThat(openAi.perRequestBody()).isEqualTo(body);
        // GIVEN a Gemini agent with extraBody
        EffectiveConnection gemini = EffectiveConnection.of(
                LlmConfig.of(AiProvider.GOOGLE_GEMINI).model("gemini").url("https://gemini.example").build(),
                AgentConfig.builder().provider(AiProvider.GOOGLE_GEMINI).extraBody(body).build());
        // THEN the body is ignored (mode NONE)
        assertThat(gemini.perRequestBody()).isNull();
        assertThat(gemini.identity().buildTimeBody()).isNull();
    }

    @Test
    void toStringMasksPerRequestBodyAndCredential() {
        // GIVEN an OpenAI agent with a per-request extra body and a base credential
        String body = "{\"prompt_cache_key\":\"llmpeon\"}";
        EffectiveConnection effective = EffectiveConnection.of(
                LlmConfig.of(AiProvider.OPEN_AI).model("gpt").url("http://base:1234/v1").apiKey("base-key").build(),
                AgentConfig.builder().provider(AiProvider.OPEN_AI).extraBody(body).build());
        // WHEN toString
        var rendered = effective.toString();
        // THEN the body content and the credential are masked, the shape stays readable
        assertThat(rendered)
                .contains("perRequestBody=" + body.length() + " chars")
                .contains("apiKey=***")
                .contains("isBase=false")
                .doesNotContain(body)
                .doesNotContain("prompt_cache_key")
                .doesNotContain("base-key");
    }
}
