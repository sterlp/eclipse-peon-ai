package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CallStats} — the shared duration/time disclosure helper
 * for tool outputs. See docs/tool-time-disclosure.md R-TD-1, ADR-0053.
 */
class CallStatsTest {

    private static final Instant T0 = Instant.parse("2026-09-22T14:32:00Z");

    /** Fixed clock the test advances manually — own instance, no coupling to other tests. */
    static final class MutableClock extends Clock {
        private Instant instant = T0;
        void advance(Duration d) { instant = instant.plus(d); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    // UC-TD-1
    @Test
    void suffixReportsMeasuredDurationAndWallTimeAtCallMoment() {
        // GIVEN a stats started at 14:32 UTC
        var clock = new MutableClock();
        var stats = new CallStats(clock);

        // WHEN 2400 ms have passed
        clock.advance(Duration.ofMillis(2400));

        // THEN the suffixes are deterministic — duration truncated to whole seconds
        assertThat(stats.durationSuffix()).isEqualTo("(2s)");
        assertThat(stats.suffix()).isEqualTo("(2s, 14:32)");
    }

    // UC-TD-1
    @Test
    void suffixMinuteFormUsesWallTimeAtCallMomentNotAtStart() {
        // GIVEN a stats started at 14:32 UTC
        var clock = new MutableClock();
        var stats = new CallStats(clock);

        // WHEN 65 s have passed
        clock.advance(Duration.ofSeconds(65));

        // THEN minute form for the duration and the wall time is taken at the
        // suffix() call (14:33), not at the start (14:32)
        assertThat(stats.suffix()).isEqualTo("(1m 5s, 14:33)");
    }

    // UC-TD-1
    @Test
    void subSecondDurationIsZeroSeconds() {
        // GIVEN a stats that has not advanced
        var clock = new MutableClock();
        var stats = new CallStats(clock);

        // THEN humanElapsed contract: sub-second reads as 0s
        assertThat(stats.suffix()).isEqualTo("(0s, 14:32)");
        assertThat(stats.duration()).isEqualTo("0s");
    }

    // UC-TD-1
    @Test
    void startWithSystemClockProducesSuffixShape() {
        // GIVEN the static factory with the system default clock
        var stats = CallStats.start();

        // THEN the shape is (N s or Mm Ns, HH:mm) — values not pinned
        assertThat(stats.suffix()).matches("\\(\\d+(?:m \\d+)?s, \\d{2}:\\d{2}\\)");
        assertThat(stats.durationSuffix()).matches("\\(\\d+(?:m \\d+)?s\\)");
    }
}
