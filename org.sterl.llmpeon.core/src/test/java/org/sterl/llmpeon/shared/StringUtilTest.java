package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

    /** SAT3 (org.sterl.llmpeon.core/docs/sub-agent-timing.md): whole seconds, truncated, minutes when >= 60s. */
    @Test
    void humanElapsed_formatsCompactly() {
        assertEquals("0s", StringUtil.humanElapsed(400));      // sub-second -> 0s
        assertEquals("3s", StringUtil.humanElapsed(3900));     // truncated, not rounded
        assertEquals("12s", StringUtil.humanElapsed(12_000));
        assertEquals("1m 5s", StringUtil.humanElapsed(65_000));
        assertEquals("2m 0s", StringUtil.humanElapsed(120_000));
        assertEquals("0s", StringUtil.humanElapsed(-5));       // negative counts as zero
    }

    /** R-RS-1 (org.sterl.llmpeon.core/docs/tool-result-size.md): the exact character count as a suffix. */
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
     * R5a (org.sterl.llmpeon.core/docs/token-usage.md): under 1 million toK is whole thousands, no decimals, no grouping
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

    /**
     * R-OD-8 bullet 2: a null or blank message renders as "(no message)" — never the word "null",
     * which the human cannot tell apart from a real message.
     */
    // UC-OD-11
    @Test
    void exceptionChain_nullOrBlankMessageIsNoMessage() {
        assertThat(StringUtil.exceptionChain(new RuntimeException()))
                .isEqualTo("RuntimeException: (no message)")
                .doesNotContain("null");
        assertThat(StringUtil.exceptionChain(new RuntimeException("   ")))
                .isEqualTo("RuntimeException: (no message)");
    }

    /**
     * R-OD-8: the failure line is the doc example, and its time comes from the injected Clock — pinned in
     * UTC for the exact doc text, and in the machine zone with hour/minute derived from the same instant,
     * so the pin holds in every timezone without binding the format to one of them.
     */
    // UC-OD-11
    @Test
    void toolFailureLine_usesInjectedClock() {
        var instant = Instant.parse("2026-10-24T14:32:00Z");
        var failure = new IllegalStateException("closed");

        assertThat(StringUtil.toolFailureLine("buildWithDev", failure, Clock.fixed(instant, ZoneOffset.UTC)))
                .isEqualTo("buildWithDev failed (14:32): IllegalStateException: closed — details logged.");

        var zone = ZoneId.systemDefault();
        var localTime = LocalTime.ofInstant(instant, zone);
        assertThat(StringUtil.toolFailureLine("buildWithDev", failure, Clock.fixed(instant, zone)))
                .isEqualTo(String.format(
                        "buildWithDev failed (%02d:%02d): IllegalStateException: closed — details logged.",
                        localTime.getHour(), localTime.getMinute()));
    }

    /**
     * R-OD-8: a chain link that only restates its cause is dropped — both restatement shapes, on the
     * helper itself and without any tool loop in between: the JDK wrapper (message is the cause's whole
     * toString) and the langchain4j wrapper (message is the cause's message verbatim).
     */
    // UC-OD-11
    @Test
    void exceptionChain_dropsLinksThatOnlyRestateTheirCause() {
        // GIVEN — JDK shape: RuntimeException(cause) prints the cause into its own message
        var jdkWrapper = new RuntimeException(new IOException("closed"));
        assertThat(jdkWrapper.getMessage()).isEqualTo("java.io.IOException: closed");

        // THEN — one link: the wrapper said nothing the cause does not
        assertThat(StringUtil.exceptionChain(jdkWrapper)).isEqualTo("IOException: closed");

        // AND — langchain4j shape: same message in wrapper and cause
        var lc4jWrapper = new IllegalStateException("closed", new IOException("closed"));
        assertThat(StringUtil.exceptionChain(lc4jWrapper)).isEqualTo("IOException: closed");
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
