package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ThinkResolverTest {

    // UC-THINK-1
    @Test
    void toOllamaThink_unsetOrBlankIsOmitted() {
        assertThat(ThinkResolver.toOllamaThink(null)).isNull();
        assertThat(ThinkResolver.toOllamaThink("")).isNull();
        assertThat(ThinkResolver.toOllamaThink("   ")).isNull();
    }

    // UC-THINK-3
    // UC-THINK-12
    @Test
    void toOllamaThink_offTokensSendFalse_onSendsTrue() {
        // toggle-off tokens (incl. German "nein", case-insensitive) -> false
        for (var off : new String[] {"false", "FALSE", "none", "no", "off", " Off ", "nO", "nein", "NEIN"}) {
            assertThat(ThinkResolver.toOllamaThink(off)).as("off %s", off).isEqualTo(Boolean.FALSE);
        }
        // everything else (incl. "ja" and arbitrary values) -> true
        for (var on : new String[] {"true", "high", "on", "yes", "minimal", "ja", "Ja", "banana"}) {
            assertThat(ThinkResolver.toOllamaThink(on)).as("on %s", on).isEqualTo(Boolean.TRUE);
        }
    }

    // UC-THINK-3
    @Test
    void isOff_isOn_keepFrozenTokens() {
        // the shared off vocabulary is frozen (isThinkSupported consumers + Anthropic generic-on)
        for (var off : new String[] {"false", "off", "no", "none", ""}) {
            assertThat(ThinkResolver.isOff(off)).as("isOff %s", (Object) off).isTrue();
        }
        for (var on : new String[] {"true", "on", "yes", "high"}) {
            assertThat(ThinkResolver.isOn(on)).as("isOn %s", on).isTrue();
        }
        // case-insensitive
        assertThat(ThinkResolver.isOff("FALSE")).isTrue();
        assertThat(ThinkResolver.isOff("Off")).isTrue();
        // "nein" is a TOGGLE-only off-token, NOT part of the shared OFF set (the §2.2 boundary)
        assertThat(ThinkResolver.isOff("nein")).isFalse();
        assertThat(ThinkResolver.isOn("nein")).isTrue();
    }
}
