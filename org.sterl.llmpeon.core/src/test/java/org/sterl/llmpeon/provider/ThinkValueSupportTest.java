package org.sterl.llmpeon.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.AiProvider;

/**
 * SWT-free think-value mapping (provider.md R5) + extra-body gate (provider.md R3). No Display
 * needed — the helper is stateless.
 */
class ThinkValueSupportTest {

    // --- Toggle form ---

    // UC-THINK-4
    @Test
    void toggleItemsAreUnsetOnOff() {
        assertThat(ThinkValueSupport.toggleItems()).containsExactly("", "true", "false");
    }

    // --- extra-body gate (provider.md R3) ---

    @Test
    void extraBodyVisibleForOpenAi() {
        assertThat(ThinkValueSupport.extraBodyVisible(AiProvider.OPEN_AI)).isTrue();
    }

    @Test
    void extraBodyVisibleForAnthropic() {
        assertThat(ThinkValueSupport.extraBodyVisible(AiProvider.ANTHROPIC)).isTrue();
    }

    @Test
    void extraBodyHiddenForOllama() {
        assertThat(ThinkValueSupport.extraBodyVisible(AiProvider.OLLAMA)).isFalse();
    }
}
