package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ThinkResolverTest {

    private static final String[] OFF = {null, "", "  ", "false", "off", "no", "none", "FALSE", "Off"};

    @Test
    void offValuesAreOmittedEverywhere() {
        for (var v : OFF) {
            assertThat(ThinkResolver.toReasoningEffort(v)).as("effort %s", v).isNull();
            assertThat(ThinkResolver.toOnOff(v)).as("onOff %s", v).isNull();
            assertThat(ThinkResolver.toBoolean(v)).as("bool %s", v).isNull();
            assertThat(ThinkResolver.isOn(v)).as("isOn %s", v).isFalse();
        }
    }

    @Test
    void truthyValuesMapToHigh() {
        for (var v : new String[] {"true", "on", "yes", "TRUE"}) {
            assertThat(ThinkResolver.toReasoningEffort(v)).isEqualTo("high");
            assertThat(ThinkResolver.toOnOff(v)).isEqualTo("on");
            assertThat(ThinkResolver.toBoolean(v)).isTrue();
            assertThat(ThinkResolver.isOn(v)).isTrue();
        }
    }

    @Test
    void explicitLevelsPassThrough() {
        for (var v : new String[] {"high", "medium", "low", "minimal"}) {
            assertThat(ThinkResolver.toReasoningEffort(v)).isEqualTo(v);
            assertThat(ThinkResolver.toOnOff(v)).isEqualTo("on");
            assertThat(ThinkResolver.toBoolean(v)).isTrue();
            assertThat(ThinkResolver.isOn(v)).isTrue();
        }
        // normalization
        assertThat(ThinkResolver.toReasoningEffort("HIGH")).isEqualTo("high");
        assertThat(ThinkResolver.toReasoningEffort(" Medium ")).isEqualTo("medium");
    }

    @Test
    void effectiveThink_autoMode_bothEmpty() {
        // both strings empty -> auto: "true" marker when on, "" when off
        assertThat(ThinkResolver.effectiveThink(true, "", "")).isEqualTo("true");
        assertThat(ThinkResolver.effectiveThink(true, null, null)).isEqualTo("true");
        assertThat(ThinkResolver.effectiveThink(false, "", "")).isEqualTo("");
    }

    @Test
    void effectiveThink_manualMode_anyStringSet_disablesHeuristic() {
        // on-string set -> verbatim when on; off empty -> "" when off
        assertThat(ThinkResolver.effectiveThink(true, "high", "")).isEqualTo("high");
        assertThat(ThinkResolver.effectiveThink(false, "high", "")).isEqualTo("");
        // off-string set -> manual: on empty -> "" (no heuristic), off verbatim
        assertThat(ThinkResolver.effectiveThink(true, "", "false")).isEqualTo("");
        assertThat(ThinkResolver.effectiveThink(false, "", "false")).isEqualTo("false");
    }

    @Test
    void toOllamaThink_distinguishesEmptyFromExplicitFalse() {
        assertThat(ThinkResolver.toOllamaThink("")).isNull();
        assertThat(ThinkResolver.toOllamaThink(null)).isNull();
        assertThat(ThinkResolver.toOllamaThink("false")).isEqualTo(Boolean.FALSE);
        assertThat(ThinkResolver.toOllamaThink("none")).isEqualTo(Boolean.FALSE);
        assertThat(ThinkResolver.toOllamaThink("true")).isEqualTo(Boolean.TRUE);
        assertThat(ThinkResolver.toOllamaThink("high")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void toReasoning_distinguishesEmptyFromExplicitOff() {
        assertThat(ThinkResolver.toReasoning("")).isNull();
        assertThat(ThinkResolver.toReasoning("false")).isEqualTo("off");
        assertThat(ThinkResolver.toReasoning("high")).isEqualTo("on");
        assertThat(ThinkResolver.toReasoning("true")).isEqualTo("on");
    }
}
