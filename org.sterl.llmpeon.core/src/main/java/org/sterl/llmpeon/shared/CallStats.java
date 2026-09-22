package org.sterl.llmpeon.shared;

import java.time.Clock;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Measures the wall-clock duration of a tool call for end-of-output disclosure
 * (docs/tool-time-disclosure.md, ADR-0053). The start is captured at creation;
 * the suffixes report the measured duration and — for {@link #suffix()} — the
 * local wall time at the moment of the call, never at the start.
 *
 * <p>Formatting stays single-sourced: durations go through
 * {@link StringUtil#humanElapsed}, the clock is injectable (Vorbild
 * {@link Timer}) so tests are deterministic.
 */
public final class CallStats {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final Clock clock;
    private final long startedAtMillis;

    /** Starts measuring with the system default clock (local zone). */
    public static CallStats start() {
        return new CallStats(Clock.systemDefaultZone());
    }

    public CallStats(Clock clock) {
        this.clock = clock;
        this.startedAtMillis = clock.millis();
    }

    /** Measured elapsed time, human form (e.g. {@code 3s}, {@code 1m 5s}). */
    public String duration() {
        return StringUtil.humanElapsed(clock.millis() - startedAtMillis);
    }

    /** Duration only, e.g. {@code (3s)}. */
    public String durationSuffix() {
        return "(" + duration() + ")";
    }

    /** Duration plus local wall time at the call moment, e.g. {@code (3s, 14:32)}. */
    public String suffix() {
        return "(" + duration() + ", " + LocalTime.now(clock).format(HH_MM) + ")";
    }
}
