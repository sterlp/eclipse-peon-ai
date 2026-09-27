package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ThinkResolverTest {

    private static final String[] OFF = {null, "", "  ", "false", "off", "no", "none", "FALSE", "Off"};

    // UC-THINK-3
    @Test
    void offValuesMapToGenericOmitValues() {
        for (var v : OFF) {
            assertThat(ThinkResolver.toReasoningEffort(v)).as("effort %s", v).isNull();
            assertThat(ThinkResolver.toOnOff(v)).as("onOff %s", v).isNull();
            assertThat(ThinkResolver.toBoolean(v)).as("bool %s", v).isNull();
            assertThat(ThinkResolver.isOn(v)).as("isOn %s", v).isFalse();
        }
    }

    // UC-THINK-3
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

    // UC-THINK-1
    @Test
    void toOllamaThink_unsetOrBlankIsOmitted() {
        assertThat(ThinkResolver.toOllamaThink(null)).isNull();
        assertThat(ThinkResolver.toOllamaThink("")).isNull();
        assertThat(ThinkResolver.toOllamaThink("   ")).isNull();
    }

    // UC-THINK-3
    @Test
    void toOllamaThink_offTokensSendFalse_onSendsTrue() {
        for (var off : new String[] {"false", "FALSE", "none", "no", "off", " Off ", "nO"}) {
            assertThat(ThinkResolver.toOllamaThink(off)).as("off %s", off).isEqualTo(Boolean.FALSE);
        }
        for (var on : new String[] {"true", "high", "on", "yes", "minimal"}) {
            assertThat(ThinkResolver.toOllamaThink(on)).as("on %s", on).isEqualTo(Boolean.TRUE);
        }
    }

    // UC-THINK-3
    @Test
    void toReasoning_blankOmits_offTokensSendOff_genericOnSendsOn_elseVerbatim() {
        assertThat(ThinkResolver.toReasoning(null)).isNull();
        assertThat(ThinkResolver.toReasoning("")).isNull();
        assertThat(ThinkResolver.toReasoning("  ")).isNull();
        for (var off : new String[] {"false", "FALSE", "none", "no", "off", " Off "}) {
            assertThat(ThinkResolver.toReasoning(off)).as("off %s", off).isEqualTo("off");
        }
        for (var on : new String[] {"true", "on", "yes", "TRUE"}) {
            assertThat(ThinkResolver.toReasoning(on)).as("on %s", on).isEqualTo("on");
        }
        assertThat(ThinkResolver.toReasoning("high")).isEqualTo("high");
    }
}
