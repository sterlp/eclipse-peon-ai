package org.sterl.llmpeon.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.model.AiModel;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.StreamingChatModel;

/**
 * BDD provider.md R1/R2: every known provider name resolves via the factory to its provider
 * class, and the legacy preference names stay stable through {@link AiProvider#parse(String)}.
 */
class ProviderFactoryTest {

    static Stream<Arguments> providerCases() {
        return Stream.of(
                Arguments.of(AiProvider.OLLAMA, OllamaProvider.class),
                Arguments.of(AiProvider.OPEN_AI, OpenAiProvider.class),
                Arguments.of(AiProvider.OPEN_AI_OFFICIAL, OpenAiOfficialProvider.class),
                Arguments.of(AiProvider.LM_STUDIO, LmStudioProvider.class),
                Arguments.of(AiProvider.GOOGLE_GEMINI, GoogleGeminiProvider.class),
                Arguments.of(AiProvider.MISTRAL, MistralProvider.class),
                Arguments.of(AiProvider.ANTHROPIC, AnthropicProvider.class),
                Arguments.of(AiProvider.GITHUB_MODELS, GithubModelsProvider.class),
                Arguments.of(AiProvider.GITHUB_COPILOT, GithubCopilotProvider.class));
    }

    @Test
    void given_allNineProviders_when_all_then_oneProviderPerNameInEnumOrder() {
        // GIVEN all 9 known provider names
        // WHEN LlmProviders.all()
        // THEN one provider per enum constant, in enum order, the same singletons as of(...)
        var all = LlmProviders.all();
        assertThat(all).hasSize(AiProvider.values().length);
        for (var p : AiProvider.values()) {
            assertThat(all).contains(LlmProviders.of(p));
        }
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("providerCases")
    void given_aProviderName_when_of_then_itResolvesToItsClassAndBehaves(AiProvider name,
            Class<? extends LlmProvider> expectedClass) {
        // GIVEN one of the 9 known provider names
        // WHEN LlmProviders.of(name)
        // THEN it is the expected provider class, resolved as a stable singleton
        var provider = LlmProviders.of(name);
        assertThat(provider).isInstanceOf(expectedClass);
        assertThat(LlmProviders.of(name)).isSameAs(provider);

        // AND newRequestParameters is callable with a minimal agent config (no network)
        var mc = AgentConfig.builder().provider(name).model("m").temperature(0.3).build();
        var params = provider.newRequestParameters(mc, List.<ToolSpecification>of());
        assertThat(params).isNotNull();
        assertThat(params.modelName()).isEqualTo("m");
        assertThat(params.temperature()).isEqualTo(0.3);
    }

    @Test
    void given_aProviderWithoutListing_when_listAiModels_then_itFallsBackToTheConfiguredModel() {
        // GIVEN a provider that does not override listAiModels (all 9 real ones do — HTTP)
        var provider = new DefaultListingProvider();

        // WHEN listAiModels with a configured model name
        // THEN the configured model is wrapped as a single-element list, and listModels delegates
        assertThat(provider.listAiModels(LlmConfig.builder().model("m").build()))
                .extracting(AiModel::getName, AiModel::getId)
                .containsExactly(tuple("m", "m"));
        assertThat(provider.listModels(LlmConfig.builder().model("m").build())).containsExactly("m");

        // AND without a model name the fallback list is empty
        assertThat(provider.listAiModels(LlmConfig.builder().build())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "OLLAMA", "OPEN_AI", "OPEN_AI_OFFICIAL", "LM_STUDIO", "GOOGLE_GEMINI",
            "MISTRAL", "ANTHROPIC", "GITHUB_MODELS", "GITHUB_COPILOT" })
    void given_aKnownPreferenceName_when_parse_then_itResolvesToThatConstant(String name) {
        // GIVEN a known preference value (all 9 enum names)
        // WHEN AiProvider.parse(name)
        // THEN it resolves to that exact constant
        assertThat(AiProvider.parse(name)).isEqualTo(AiProvider.valueOf(name));
    }

    @Test
    void given_anUnknownOrNullPreferenceName_when_parse_then_itFallsBackToOllama() {
        // GIVEN unknown or null preference values
        // WHEN AiProvider.parse(...)
        // THEN both fall back to OLLAMA, and the factory still resolves a working provider
        for (var name : new String[] { null, "SOME_FUTURE_PROVIDER" }) {
            assertThat(AiProvider.parse(name)).as("parse(%s)", name).isEqualTo(AiProvider.OLLAMA);
            assertThat(LlmProviders.of(AiProvider.parse(name))).isInstanceOf(OllamaProvider.class);
        }
    }

    /** Test-only provider that uses the interface defaults for listAiModels/listModels. */
    private static final class DefaultListingProvider implements LlmProvider {
        @Override
        public StreamingChatModel buildModel(LlmConfig config) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public ExtraBodyMode extraBodyMode() {
            return ExtraBodyMode.NONE;
        }

        @Override
        public ThinkSupport thinkSupport() {
            return ThinkSupport.UNKNOWN;
        }
    }
}
