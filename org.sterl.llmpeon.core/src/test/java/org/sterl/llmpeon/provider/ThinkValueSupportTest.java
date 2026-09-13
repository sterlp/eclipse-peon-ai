package org.sterl.llmpeon.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.AiProvider;

/**
 * SWT-free think-value mapping (provider.md R5) + extra-body gate (provider.md R3). No Display
 * needed — the helper is stateless.
 */
class ThinkValueSupportTest {

    // --- Boolean form ---

    @Test
    void booleanOnOffMaps() {
        assertThat(ThinkValueSupport.booleanValue(true)).isEqualTo("true");
        assertThat(ThinkValueSupport.booleanValue(false)).isEqualTo("");
    }

    @Test
    void booleanOnDetectsStoredValue() {
        assertThat(ThinkValueSupport.booleanOn("true")).isTrue();
        assertThat(ThinkValueSupport.booleanOn("")).isFalse();
        assertThat(ThinkValueSupport.booleanOn(null)).isFalse();
        assertThat(ThinkValueSupport.booleanOn("false")).isFalse();
    }

    // --- Values form ---

    @Test
    void valuesDropdownOffersOffAutoAndValues() {
        var values = new ThinkSupport.Values(List.of("none", "minimal", "low", "medium", "high", "xhigh"));
        assertThat(ThinkValueSupport.valuesItems(values))
                .containsExactly("Off", "Auto", "none", "minimal", "low", "medium", "high", "xhigh");
    }

    @Test
    void valuesSelectionMapsToStored() {
        assertThat(ThinkValueSupport.valuesStored("Off")).isEqualTo("");
        assertThat(ThinkValueSupport.valuesStored("Auto")).isEqualTo("true");
        assertThat(ThinkValueSupport.valuesStored("high")).isEqualTo("high");
    }

    @Test
    void valuesStoredMapsToDisplay() {
        assertThat(ThinkValueSupport.valuesDisplay("")).isEqualTo("Off");
        assertThat(ThinkValueSupport.valuesDisplay(null)).isEqualTo("Off");
        assertThat(ThinkValueSupport.valuesDisplay("true")).isEqualTo("Auto");
        assertThat(ThinkValueSupport.valuesDisplay("high")).isEqualTo("high");
    }

    @Test
    void unknownValueDisplaysVerbatim() {
        assertThat(ThinkValueSupport.valuesDisplay("custom-level")).isEqualTo("custom-level");
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
