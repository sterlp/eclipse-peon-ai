package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ThinkModelMappingTest {

    // UC-THINK-8
    @Test
    void openAiKnownReasoningModelsMapToHigh() {
        assertThat(ThinkModelMapping.resolveOn(AiProvider.OPEN_AI, "gpt-5.5")).isEqualTo("high");
        assertThat(ThinkModelMapping.resolveOn(AiProvider.OPEN_AI, "o3-mini")).isEqualTo("high");
        assertThat(ThinkModelMapping.resolveOn(AiProvider.OPEN_AI, "GPT-4o")).isEqualTo("high");
    }

    // UC-THINK-8
    @Test
    void openAiUnknownModelMapsToNothing() {
        assertThat(ThinkModelMapping.resolveOn(AiProvider.OPEN_AI, "kimi-k2")).isNull();
        assertThat(ThinkModelMapping.resolveOn(AiProvider.OPEN_AI, null)).isNull();
    }

    // UC-THINK-8
    @Test
    void anthropicOpusIsAdaptiveOtherClaudeEnabled() {
        assertThat(ThinkModelMapping.resolveOn(AiProvider.ANTHROPIC, "claude-opus-4-8")).isEqualTo("adaptive");
        assertThat(ThinkModelMapping.resolveOn(AiProvider.ANTHROPIC, "claude-opus-4-7")).isEqualTo("adaptive");
        assertThat(ThinkModelMapping.resolveOn(AiProvider.ANTHROPIC, "claude-sonnet-4-5")).isEqualTo("enabled");
    }

    // UC-THINK-8
    @Test
    void anthropicUnknownModelMapsToNothing() {
        assertThat(ThinkModelMapping.resolveOn(AiProvider.ANTHROPIC, "some-other-model")).isNull();
    }

    // UC-THINK-8
    @Test
    void providerWithoutMappingFileReturnsNull() {
        assertThat(ThinkModelMapping.resolveOn(AiProvider.OLLAMA, "llama3")).isNull();
    }
}
