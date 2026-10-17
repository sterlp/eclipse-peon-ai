package org.sterl.llmpeon.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;

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

    /**
     * R5a (docs/token-usage.md): under 1 million toK is whole thousands, no decimals, no grouping
     * (max "999k") — plain integer division, so it never picks up a locale artifact. Pinned under
     * GERMANY: a mutation that routed the k form through a locale NumberFormat with decimals would
     * render 131072 as "131,07k" and go red.
     */
    @Test
    void toK_underOneMillionIsWholeThousands() {
        withLocale(Locale.GERMANY, () -> {
            assertEquals("131k", StringUtil.toK(131_072));
            assertEquals("1k", StringUtil.toK(1_000));
            assertEquals("0k", StringUtil.toK(999));           // integer division truncates
            assertEquals("0k", StringUtil.toK(0));
            assertEquals("999k", StringUtil.toK(999_999));     // just under the M boundary stays k
        });
    }

    /** R5a: exactly 1 million is the M boundary — "1M" (max-fraction-digits trims the trailing .00). */
    @Test
    void toK_oneMillionIsOneM() {
        withLocale(Locale.GERMANY, () -> assertEquals("1M", StringUtil.toK(1_000_000)));
        withLocale(Locale.US, () -> assertEquals("1M", StringUtil.toK(1_000_000)));
    }

    /**
     * R5a: from 1 million up toK switches to millions with up to 2 fraction digits, the decimal
     * separator and the integer grouping following the default locale. Issue #154: the old
     * "1234k" read ambiguously as 1.2k or 1.2M — "1,23M" / "1.23M" is unambiguous.
     */
    @Test
    void toK_aboveOneMillionUsesMillionsTwoFractionDigits() {
        withLocale(Locale.GERMANY, () -> {
            assertEquals("1,23M", StringUtil.toK(1_234_567));
            assertEquals("1,84M", StringUtil.toK(1_840_000));
            assertEquals("33,02M", StringUtil.toK(33_020_000));
        });
        withLocale(Locale.US, () -> {
            assertEquals("1.23M", StringUtil.toK(1_234_567));
            assertEquals("1.84M", StringUtil.toK(1_840_000));
            assertEquals("33.02M", StringUtil.toK(33_020_000));
        });
    }

    /**
     * R5a edge: 999,999,999 rounds up over the M boundary to 1000.00 millions, grouped per locale —
     * "1.000M" (DE) / "1,000M" (US), pinned honestly (a mutation that dropped grouping or the
     * 2-fraction-digit rounding goes red).
     */
    @Test
    void toK_justUnderABillionRoundsToGroupedThousandMillions() {
        withLocale(Locale.GERMANY, () -> assertEquals("1.000M", StringUtil.toK(999_999_999)));
        withLocale(Locale.US, () -> assertEquals("1,000M", StringUtil.toK(999_999_999)));
    }

    /** Pins the default locale for the body, restoring the original afterwards (JUnit 5 runs core sequentially). */
    private static void withLocale(Locale locale, Runnable body) {
        Locale original = Locale.getDefault();
        Locale.setDefault(locale);
        try {
            body.run();
        } finally {
            Locale.setDefault(original);
        }
    }
}
