package org.sterl.llmpeon.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;

/**
 * Copilot is OpenAI-compatible and must carry the same build-time thinking transport switches as
 * its sibling providers (OpenAiProvider): {@code returnThinking} on (otherwise the model's
 * reasoning is never parsed), {@code sendThinking} following the global show-and-resend-thinking
 * preference. {@code OpenAiStreamingChatModel} exposes no public getters for the two private
 * final fields, so the test reads them via reflection.
 */
class GithubCopilotProviderTest {

    // UC-THINK-9
    @Test
    void copilotBuildsModelWithReturnThinking() throws Exception {
        // GIVEN a Copilot config (send-thinking default = on)
        var config = LlmConfig.builder()
                .providerType(AiProvider.GITHUB_COPILOT)
                .url("https://api.githubcopilot.com")
                .apiKey("test-key")
                .model("gpt-4.1")
                .build();

        // WHEN buildModel
        var model = LlmProviders.of(AiProvider.GITHUB_COPILOT).buildModel(config);

        // THEN the built model returns thinking and follows the global transport switch
        assertThat(booleanField(model, "returnThinking")).isTrue();
        assertThat(booleanField(model, "sendThinking")).isEqualTo(config.shouldWeSendThinkingBackToLLM());

        // AND with the transport switch off it is wired through as off
        var disabled = config.toBuilder().sendThinkingEnabled(false).build();
        var disabledModel = LlmProviders.of(AiProvider.GITHUB_COPILOT).buildModel(disabled);
        assertThat(booleanField(disabledModel, "sendThinking")).isEqualTo(disabled.shouldWeSendThinkingBackToLLM());
    }

    private static boolean booleanField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (boolean) field.get(target);
    }
}
