package org.sterl.open.aifactory.shared;

/**
 * The one error body of the factory API — {@code {status, error, message}}. {@code message} is what
 * the UI prints next to the input (git output verbatim, R-WP-4).
 */
public record ApiError(int status, String error, String message) {
}
