package org.sterl.llmpeon.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.sterl.llmpeon.ai.AiProvider;

/**
 * BDD provider.md R3/R5: the per-provider capabilities (extra body support, think input form)
 * are fixed per class.
 */
class ProviderCapabilitiesTest {

    static Stream<Arguments> extraBodyCases() {
        return Stream.of(
                // true: per-request customParameters (OpenAI family, LM Studio) or build-time (Anthropic)
                Arguments.of(AiProvider.OPEN_AI, true),
                Arguments.of(AiProvider.LM_STUDIO, true),
                Arguments.of(AiProvider.GITHUB_COPILOT, true),
                Arguments.of(AiProvider.ANTHROPIC, true),
                // false: no extra body support
                Arguments.of(AiProvider.OLLAMA, false),
                Arguments.of(AiProvider.OPEN_AI_OFFICIAL, false),
                Arguments.of(AiProvider.GOOGLE_GEMINI, false),
                Arguments.of(AiProvider.MISTRAL, false),
                Arguments.of(AiProvider.GITHUB_MODELS, false));
    }

    @ParameterizedTest(name = "{0}.supportsExtraBody() == {1}")
    @MethodSource("extraBodyCases")
    void given_aProvider_when_supportsExtraBody_then_itMatchesTheCapabilityTable(AiProvider name, boolean expected) {
        // GIVEN one of the 9 providers
        // WHEN supportsExtraBody()
        // THEN it matches the R3 capability table
        assertThat(LlmProviders.of(name).supportsExtraBody()).isEqualTo(expected);
    }

    static Stream<Arguments> extraBodyModeCases() {
        return Stream.of(
                // PER_REQUEST: per-request customParameters (OpenAI family, LM Studio, Copilot)
                Arguments.of(AiProvider.OPEN_AI, ExtraBodyMode.PER_REQUEST),
                Arguments.of(AiProvider.LM_STUDIO, ExtraBodyMode.PER_REQUEST),
                Arguments.of(AiProvider.GITHUB_COPILOT, ExtraBodyMode.PER_REQUEST),
                // BUILD_TIME: baked into the model at build time (Anthropic)
                Arguments.of(AiProvider.ANTHROPIC, ExtraBodyMode.BUILD_TIME),
                // NONE: no extra body support
                Arguments.of(AiProvider.OLLAMA, ExtraBodyMode.NONE),
                Arguments.of(AiProvider.OPEN_AI_OFFICIAL, ExtraBodyMode.NONE),
                Arguments.of(AiProvider.GOOGLE_GEMINI, ExtraBodyMode.NONE),
                Arguments.of(AiProvider.MISTRAL, ExtraBodyMode.NONE),
                Arguments.of(AiProvider.GITHUB_MODELS, ExtraBodyMode.NONE));
    }

    @ParameterizedTest(name = "{0}.extraBodyMode() == {1}")
    @MethodSource("extraBodyModeCases")
    void extraBodyModePerClass(AiProvider name, ExtraBodyMode expected) {
        // GIVEN one of the 9 providers
        // WHEN extraBodyMode()
        // THEN it matches the R3 mode table
        assertThat(LlmProviders.of(name).extraBodyMode()).isEqualTo(expected);
    }

    static Stream<Arguments> thinkSupportCases() {
        var openAiFamily = ProviderRequestSupport.openAiFamilyThinkSupport();
        return Stream.of(
                Arguments.of(AiProvider.OLLAMA, new ThinkSupport.Boolean()),
                Arguments.of(AiProvider.OPEN_AI, openAiFamily),
                Arguments.of(AiProvider.OPEN_AI_OFFICIAL, openAiFamily),
                Arguments.of(AiProvider.GITHUB_MODELS, openAiFamily),
                Arguments.of(AiProvider.GITHUB_COPILOT, openAiFamily),
                Arguments.of(AiProvider.LM_STUDIO, new ThinkSupport.FreeString()),
                Arguments.of(AiProvider.GOOGLE_GEMINI, ThinkSupport.NONE),
                Arguments.of(AiProvider.MISTRAL, ThinkSupport.NONE),
                Arguments.of(AiProvider.ANTHROPIC, new ThinkSupport.Values(List.of("adaptive", "enabled"))));
    }

    @ParameterizedTest(name = "{0}.thinkSupport() == {1}")
    @MethodSource("thinkSupportCases")
    void given_aProvider_when_thinkSupport_then_itMatchesTheCapabilityTable(AiProvider name, ThinkSupport expected) {
        // GIVEN one of the 9 providers
        // WHEN thinkSupport()
        // THEN it matches the R5 capability table (values lists element-exact)
        assertThat(LlmProviders.of(name).thinkSupport()).isEqualTo(expected);
    }

    @Test
    void given_theOpenAiFamily_when_thinkSupport_then_theValuesTrackTheSdkEffortLevels() {
        // GIVEN the OpenAI family's think support
        // WHEN its values are read
        // THEN they are exactly the SDK's reasoning-effort levels, lowercased
        assertThat(ProviderRequestSupport.openAiFamilyThinkSupport())
                .isEqualTo(new ThinkSupport.Values(List.of("none", "minimal", "low", "medium", "high", "xhigh")));
    }
}
