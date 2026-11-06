package org.sterl.llmpeon.shared;

import java.time.Clock;
import java.time.LocalTime;
import java.util.function.Function;

import lombok.Getter;

/**
 * Measures the wall-clock duration of a tool call for end-of-output disclosure
 * (org.sterl.llmpeon.core/docs/tool-time-disclosure.md, ADR-0053). The start is captured at creation;
 * the suffixes report the measured duration and — for {@link #suffix()} — the
 * local wall time at the moment of the call, never at the start.
 *
 * <p>Formatting stays single-sourced: durations go through
 * {@link StringUtil#humanElapsed}, the clock is injectable (Vorbild
 * {@link Timer}) so tests are deterministic.
 */
public final class CallStats {
    @FunctionalInterface
    public interface CallStatsListener<T> {
        void call(CallStats stats, T agent);
    }
    @FunctionalInterface
    public interface MonitoredFunction<T, R> {
        R apply(T arg, CallStats stats);
    }
    
    /** Starts measuring with the system default clock (local zone). */
    public static CallStats start() {
        return new CallStats(Clock.systemDefaultZone());
    }
    
    public static <T, R> R run(MonitoredFunction<T, R> toRun, T arg,
            CallStatsListener<T> startListener,
            CallStatsListener<T> endListener) {
        var timer = CallStats.start();
        try {
            if (startListener != null) startListener.call(timer, arg);
            return toRun.apply(arg, timer);
        } finally {
            timer.stop();
            if (endListener != null) endListener.call(timer, arg);
        }
    }

    private final Clock clock;
    @Getter
    private final long startedAtMillis;
    private volatile long stoppedAtMillis = -1;

    public CallStats(Clock clock) {
        this.clock = clock;
        this.startedAtMillis = clock.millis();
    }
    
    public void stop() {
        stoppedAtMillis = clock.millis();
    }

    /** Measured elapsed time, human form (e.g. {@code 3s}, {@code 1m 5s}). */
    public String duration() {
        return StringUtil.humanElapsed(durationMs());
    }
    
    public long durationMs() {
        return stoppedAtMillis < 0 
                ? clock.millis() - startedAtMillis
                : stoppedAtMillis - startedAtMillis;
    }

    /** Duration only, e.g. {@code (3s)}. */
    public String durationSuffix() {
        return "(" + duration() + ")";
    }

    /** Duration plus local wall time at the call moment, e.g. {@code (3s, 14:32)}. */
    public String suffix() {
        return "(" + duration() + ", " + LocalTime.now(clock).format(StringUtil.HH_MM) + ")";
    }
}
