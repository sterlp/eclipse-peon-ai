package org.sterl.llmpeon.exception;

import java.util.IdentityHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import dev.langchain4j.exception.RateLimitException;

/**
 * Shared exception helpers used across core and plugin layers.
 */
public final class ExceptionUtil {

    private ExceptionUtil() {}

    /**
     * Returns the first non-envelope of the cause chain — {@link ExecutionException} and
     * {@link CompletionException} are future envelopes, everything else is the real cause.
     * Cycle-safe, null-safe; returns {@code null} for a pure envelope chain without bottom cause.
     */
    public static Throwable unwrap(Throwable throwable) {
        if (throwable == null) return null;
        var seen = new IdentityHashMap<Throwable, Boolean>();
        var current = throwable;
        while (current != null) {
            if (seen.putIfAbsent(current, Boolean.TRUE) != null) return current; // cyclic envelope chain
            if (!(current instanceof ExecutionException || current instanceof CompletionException)) return current;
            current = current.getCause();
        }
        return null;
    }

    /**
     * Returns {@code true} when the given throwable or any of its nested causes
     * is a {@link CancellationException}.
     */
    public static boolean isCanceled(Throwable throwable) {
        if (throwable == null) return false;
        Throwable current = throwable;
        do {
            if (current instanceof CancellationException) return true;
            current = current.getCause();
        } while (current != null && current != throwable);
        return false;
    }

    /**
     * Returns {@code true} when the given throwable or any of its nested causes
     * is a {@link RateLimitException}.
     */
    public static boolean isRateLimit(Throwable throwable) {
        if (throwable == null) return false;
        Throwable current = throwable;
        do {
            if (current instanceof RateLimitException) return true;
            current = current.getCause();
        } while (current != null && current != throwable);
        return false;
    }
}
