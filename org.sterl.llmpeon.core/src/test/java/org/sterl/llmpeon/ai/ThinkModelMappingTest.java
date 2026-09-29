package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ThinkModelMappingTest {

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
