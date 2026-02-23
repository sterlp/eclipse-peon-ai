package org.sterl.llmpeon.shared;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Utility for polling a condition with a configurable interval and max duration.
 */
public class WaitUtil {

    private static final long DEFAULT_POLL_MS = 500;

    /**
     * Blocks the current thread until the condition returns {@code true}
     * or the timeout is reached.
     *
     * @param condition    checked every {@code pollIntervalMs}; returns {@code true} when done
     * @param maxDuration  maximum time to wait
     * @param pollIntervalMs  sleep duration between checks in milliseconds
     * @return {@code true} if the condition was met, {@code false} on timeout
     */
    public static boolean awaitCondition(BooleanSupplier condition,
                                         Duration maxDuration,
                                         long pollIntervalMs) {
        long deadline = System.currentTimeMillis() + maxDuration.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return true;
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return condition.getAsBoolean();
            }
        }
        return condition.getAsBoolean();
    }

    /**
     * Convenience overload using the default 500ms poll interval.
     */
    public static boolean awaitCondition(BooleanSupplier condition, Duration maxDuration) {
        return awaitCondition(condition, maxDuration, DEFAULT_POLL_MS);
    }
}
