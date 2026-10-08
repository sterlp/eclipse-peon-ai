package org.sterl.llmpeon.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StringUtilTest {

    @Test
    void testOffsetToLine() {
        assertEquals("Paul", StringUtil.offsetToLine("""
                Hallo
                Paul
                """, 1));
    }

    /** SAT3 (docs/sub-agent-timing.md): whole seconds, truncated, minutes when >= 60s. */
    @Test
    void humanElapsed_formatsCompactly() {
        assertEquals("0s", StringUtil.humanElapsed(400));      // sub-second -> 0s
        assertEquals("3s", StringUtil.humanElapsed(3900));     // truncated, not rounded
        assertEquals("12s", StringUtil.humanElapsed(12_000));
        assertEquals("1m 5s", StringUtil.humanElapsed(65_000));
        assertEquals("2m 0s", StringUtil.humanElapsed(120_000));
        assertEquals("0s", StringUtil.humanElapsed(-5));       // negative counts as zero
    }

    /** R-RS-1 (docs/tool-result-size.md): the exact character count as a suffix. */
    @Test
    void charsSuffix_basic() {
        assertEquals("(3 chars)", StringUtil.charsSuffix("abc"));
    }

    /** R-RS-1: a null or empty result counts as zero — honest, never omitted. */
    @Test
    void charsSuffix_nullOrEmptyIsZero() {
        assertEquals("(0 chars)", StringUtil.charsSuffix(null));
        assertEquals("(0 chars)", StringUtil.charsSuffix(""));
    }

}
