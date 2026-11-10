package org.sterl.open.aifactory.shared;

/**
 * One rejected field of a request body (R-WP-55, UC-WP-92). {@code field} is the property path of the
 * wire DTO — the same name the UI element is bound to — so the answer needs no parsing to find the
 * field it belongs to; {@code message} is the validation text of the server, word for word.
 */
public record ApiFieldError(String field, String message) {
}
