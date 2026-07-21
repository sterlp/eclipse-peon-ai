package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReasoningPresetsTest {

    @Test
    void emptyAutoValueIsFirst() {
        assertThat(ReasoningPresets.values().iterator().next()).isEmpty();
    }

    @Test
    void includesOpenAiEffortLevelsFromSdkEnum() {
        assertThat(ReasoningPresets.values())
                .contains("none", "minimal", "low", "medium", "high", "xhigh");
    }

    @Test
    void includesAnthropicOnValuesFromMappingResource() {
        assertThat(ReasoningPresets.values()).contains("enabled", "adaptive");
    }

    @Test
    void includesGenericOnOffTokens() {
        assertThat(ReasoningPresets.values()).contains("true", "false");
    }

    @Test
    void isDeduplicated() {
        var values = ReasoningPresets.values();
        assertThat(values).doesNotHaveDuplicates();
        // "none"/"high" appear in more than one source but must be listed once
        assertThat(values.stream().filter("none"::equals)).hasSize(1);
    }
}
