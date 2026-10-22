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
     * Returns the first throwable of the given type anywhere in the cause chain — the throwable
     * itself or one of its nested causes. Cycle-safe, null-safe; {@code null} when absent.
     */
    public static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        if (throwable == null) return null;
        var seen = new IdentityHashMap<Throwable, Boolean>();
        var current = throwable;
        while (current != null) {
            if (seen.putIfAbsent(current, Boolean.TRUE) != null) return null; // cycle without a match
            if (type.isInstance(current)) return type.cast(current);
            current = current.getCause();
        }
        return null;
    }

    /**
     * Returns {@code true} when the given throwable or any of its nested causes
     * is a {@link CancellationException}.
     */
    public static boolean isCanceled(Throwable throwable) {
        return findCause(throwable, CancellationException.class) != null;
    }

    /**
     * Returns {@code true} when the given throwable or any of its nested causes
     * is a {@link RateLimitException}.
     */
    public static boolean isRateLimit(Throwable throwable) {
        return findCause(throwable, RateLimitException.class) != null;
    }
}
