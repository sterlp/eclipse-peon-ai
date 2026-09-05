package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the generic {@link Timer}. See docs/streaming-display.md R18.
 */
class TimerTest {

    private static final Instant T0 = Instant.parse("2026-09-05T08:00:00Z");

    /** Fixed clock the test advances manually — own instance, no coupling to other tests. */
    static final class MutableClock extends Clock {
        private Instant instant = T0;
        void advance(Duration d) { instant = instant.plus(d); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    @Test
    void startCapturesStartAndIsRunning() {
        // GIVEN a fresh timer
        var clock = new MutableClock();
        var timer = new Timer(clock);

        // WHEN it starts
        timer.start();

        // THEN it is running with zero elapsed time
        assertThat(timer.running()).isTrue();
        assertThat(timer.millis()).isZero();
    }

    @Test
    void stopFreezesElapsedDuration() {
        // GIVEN a running timer
        var clock = new MutableClock();
        var timer = new Timer(clock);
        timer.start();

        // WHEN 1200ms have passed and it is stopped
        clock.advance(Duration.ofMillis(1200));
        timer.stop();

        // THEN the frozen duration is 1200ms and it is no longer running
        assertThat(timer.running()).isFalse();
        assertThat(timer.millis()).isEqualTo(1200);
    }

    @Test
    void resetZeroesAfterStop() {
        // GIVEN a stopped timer with a frozen duration
        var clock = new MutableClock();
        var timer = new Timer(clock);
        timer.start();
        clock.advance(Duration.ofMillis(500));
        timer.stop();

        // WHEN it is reset
        timer.reset();

        // THEN it is not running and reports zero
        assertThat(timer.running()).isFalse();
        assertThat(timer.millis()).isZero();
    }

    @Test
    void millisIsLiveWhileRunning() {
        // GIVEN a running timer
        var clock = new MutableClock();
        var timer = new Timer(clock);
        timer.start();

        // WHEN time advances
        clock.advance(Duration.ofMillis(350));

        // THEN millis() reflects the live elapsed time
        assertThat(timer.millis()).isEqualTo(350);
    }

    @Test
    void stopWhileNotRunningIsNoOp() {
        // GIVEN a timer that never started
        var clock = new MutableClock();
        var timer = new Timer(clock);

        // WHEN stop is called
        timer.stop();

        // THEN nothing changes
        assertThat(timer.running()).isFalse();
        assertThat(timer.millis()).isZero();
    }

    @Test
    void startAfterStopRunsAgain() {
        // GIVEN a timer that started and stopped
        var clock = new MutableClock();
        var timer = new Timer(clock);
        timer.start();
        clock.advance(Duration.ofMillis(400));
        timer.stop();

        // WHEN it is started again after another 200ms
        clock.advance(Duration.ofMillis(200));
        timer.start();

        // THEN it is running again with a fresh zero elapsed time
        assertThat(timer.running()).isTrue();
        assertThat(timer.millis()).isZero();
    }

    @Test
    void startWhileRunningRestarts() {
        // GIVEN a running timer that already has live elapsed time
        var clock = new MutableClock();
        var timer = new Timer(clock);
        timer.start();
        clock.advance(Duration.ofMillis(800));

        // WHEN it is started again (restart)
        timer.start();

        // THEN the elapsed time is reset to zero and it keeps running
        assertThat(timer.running()).isTrue();
        assertThat(timer.millis()).isZero();
    }
}
