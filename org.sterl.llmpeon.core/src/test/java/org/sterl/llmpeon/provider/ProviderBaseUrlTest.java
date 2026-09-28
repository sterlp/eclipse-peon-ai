package org.sterl.llmpeon.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;

/**
 * R-DEF-3: an empty configured base URL falls back to the provider's default; OpenAI-compatible
 * providers without a default fail with the honest Peon error instead of langchain4j's
 * "baseUrl cannot be null or blank". Providers that carry a langchain4j built-in default
 * (Gemini, Mistral, Anthropic) never resolve a URL and are unaffected.
 */
class ProviderBaseUrlTest {

    // UC-DEF-3
    @Test
    void ollamaFallsBackToDefaultUrl() {
        // GIVEN an Ollama config with an empty configured URL
        var config = LlmConfig.builder().providerType(AiProvider.OLLAMA).url(null).model("llama3").build();

        // WHEN the provider resolves the base URL
        var url = LlmProviders.of(AiProvider.OLLAMA).baseUrlFor(config);

        // THEN the provider default is used
        assertThat(url).isEqualTo(OllamaProvider.DEFAULT_BASE_URL).isEqualTo("http://localhost:11434");
    }

    // UC-DEF-3
    @Test
    void lmStudioFallsBackToDefaultUrl() {
        // GIVEN an LM Studio config with an empty configured URL
        var config = LlmConfig.builder().providerType(AiProvider.LM_STUDIO).url(null).model("local-model").build();

        // WHEN the provider resolves the base URL
        var url = LlmProviders.of(AiProvider.LM_STUDIO).baseUrlFor(config);

        // THEN the provider default is used
        assertThat(url).isEqualTo(LmStudioProvider.DEFAULT_BASE_URL).isEqualTo("http://localhost:1234/v1");
    }

    // UC-DEF-3
    @Test
    void configuredUrlWins() {
        // GIVEN an Ollama config with an explicit URL
        var config = LlmConfig.builder().providerType(AiProvider.OLLAMA)
                .url("http://other-host:9999").model("llama3").build();

        // WHEN the provider resolves the base URL
        var url = LlmProviders.of(AiProvider.OLLAMA).baseUrlFor(config);

        // THEN the configured URL is kept verbatim (no fallback)
        assertThat(url).isEqualTo("http://other-host:9999");
    }

    // UC-DEF-3
    @Test
    void openAiWithoutUrlThrowsHonestError() {
        // GIVEN OpenAI-compatible configs without a URL (no provider default)
        var openAi = LlmConfig.builder().providerType(AiProvider.OPEN_AI).url(null).model("gpt-4o").build();
        var official = LlmConfig.builder().providerType(AiProvider.OPEN_AI_OFFICIAL).url(null).model("gpt-4o").build();

        // WHEN the providers resolve the base URL
        // THEN they fail with the honest Peon error, not langchain4j's builder error
        assertThatThrownBy(() -> LlmProviders.of(AiProvider.OPEN_AI).baseUrlFor(openAi))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No base URL configured")
                .hasMessageNotContaining("cannot be null or blank");
        assertThatThrownBy(() -> LlmProviders.of(AiProvider.OPEN_AI_OFFICIAL).baseUrlFor(official))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No base URL configured")
                .hasMessageNotContaining("cannot be null or blank");
    }

    // UC-DEF-3
    @Test
    void githubModelsAndCopilotDefaultUrls() {
        // GIVEN empty-URL configs for both GitHub providers
        var models = LlmConfig.builder().providerType(AiProvider.GITHUB_MODELS).url(null).model("o3").build();
        var copilot = LlmConfig.builder().providerType(AiProvider.GITHUB_COPILOT).url(null).model("gpt-4.1").build();

        // WHEN the providers resolve the base URL
        // THEN each falls back to its migrated default
        assertThat(LlmProviders.of(AiProvider.GITHUB_MODELS).baseUrlFor(models))
                .isEqualTo(GithubModelsProvider.DEFAULT_BASE_URL).isEqualTo("https://models.github.ai/inference");
        assertThat(LlmProviders.of(AiProvider.GITHUB_COPILOT).baseUrlFor(copilot))
                .isEqualTo(GithubCopilotProvider.DEFAULT_BASE_URL).isEqualTo("https://api.githubcopilot.com");
    }

    // UC-DEF-3
    @Test
    void urllessProvidersDoNotThrow() {
        // GIVEN url-less configs for the providers that carry a langchain4j built-in default
        for (AiProvider p : List.of(AiProvider.GOOGLE_GEMINI, AiProvider.MISTRAL, AiProvider.ANTHROPIC)) {
            var config = LlmConfig.builder().providerType(p).url(null)
                    .model("some-model").apiKey("test-key").build();

            // WHEN buildModel runs
            // THEN no base-URL error is thrown (their langchain4j defaults apply)
            assertThatCode(() -> LlmProviders.of(p).buildModel(config)).doesNotThrowAnyException();
        }
    }
}
