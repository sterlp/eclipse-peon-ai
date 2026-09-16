package org.sterl.llmpeon.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests for PromptLoader resource loading.
 */
class PromptLoaderTest {

    @Test
    void loadExistingPrompt() {
        // Verify default.txt loads successfully (confirms resource exists)
        String prompt = PromptLoader.load("default.md");
        assertThat(prompt).isNotBlank();
    }

    @Test
    void loadNonExistentPromptThrowsException() {
        // Verify that missing prompts throw IllegalStateException
        assertThatThrownBy(() -> PromptLoader.load("nonexistent-prompt.txt"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void loadWithDefaultAppendsToDefault() {
        // Verify that loadWithDefault prepends the default prompt
        String result = PromptLoader.loadWithDefault("default.md");
        assertThat(result).contains(PromptLoader.load("default.md"));
        assertThat(result).contains("\n\n");
    }
}
