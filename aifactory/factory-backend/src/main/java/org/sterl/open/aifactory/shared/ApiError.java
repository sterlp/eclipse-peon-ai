package org.sterl.open.aifactory.shared;

import java.util.List;

import org.springframework.lang.Nullable;

/**
 * The one error body of the factory API — {@code {status, error, message}}. {@code message} is what
 * the UI prints next to the input (git output verbatim, R-WP-4).
 *
 * <p>{@code fieldErrors} names the rejected fields of a validated body (R-WP-55) and is null whenever
 * nothing was validated — it never repeats {@code message} as one sentence to parse back apart.</p>
 */
public record ApiError(int status, String error, String message,
        @Nullable List<ApiFieldError> fieldErrors) {
}
