package org.sterl.llmpeon.shared;

import java.time.Clock;

/**
 * A generic wall-clock timer in milliseconds, built on {@link Clock} for deterministic testing.
 * <p>
 * No synchronization is used: a single thread writes (start/stop/reset) while the UI thread reads
 * {@link #millis()} / {@link #running()}. A {@code volatile} field is sufficient for this
 * write-once / read-many contract. There is deliberately <b>no</b> atomicity across the
 * {@code start} and {@code elapsed} fields — the R18 concurrency level only.
 * <p>
 * {@link #start()} records the start instant (and restarts if already running); {@link #stop()}
 * freezes the elapsed duration (no-op when not running); {@link #reset()} zeroes everything.
 * {@link #millis()} returns the live duration while running, otherwise the last frozen duration.
 */
public class Timer {

    private final Clock clock;

    /** Epoch millis of the current start, or {@code 0} when not running. */
    private volatile long start;

    /** Frozen duration in millis, meaningful while not running. */
    private volatile long elapsed;

    public Timer() {
        this(Clock.systemUTC());
    }

    public Timer(Clock clock) {
        this.clock = clock;
    }

    /** Records the start instant and (re)starts the timer. */
    public void start() {
        this.start = clock.millis();
    }

    /** Freezes the elapsed duration. A no-op when the timer is not running. */
    public void stop() {
        if (start != 0) {
            this.elapsed = clock.millis() - start;
            this.start = 0;
        }
    }

    /** Resets the timer: not running and {@link #millis()} returns {@code 0}. */
    public void reset() {
        this.start = 0;
        this.elapsed = 0;
    }

    /** The live duration while running, otherwise the last frozen duration. */
    public long millis() {
        return start != 0 ? clock.millis() - start : elapsed;
    }

    /** Whether the timer is currently running. */
    public boolean running() {
        return start != 0;
    }
}
