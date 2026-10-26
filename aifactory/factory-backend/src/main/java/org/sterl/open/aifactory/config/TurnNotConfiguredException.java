package org.sterl.open.aifactory.config;

/**
 * The factory's own config cannot serve a turn: a field is missing, or the provider name is not one the
 * core can build a model for. Parity with {@code project.ProjectNotFoundException} — its own type, so a
 * caller can tell it apart from a validation failure without reading a message.
 *
 * <p>Deliberately <b>not</b> answered by {@code shared.ApiExceptionHandler}: by the time a turn reads the
 * config the POST has long answered 202, so the honest channel is the live {@code error} event, not an
 * HTTP status the architecture doc does not know (Q1).</p>
 */
public class TurnNotConfiguredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TurnNotConfiguredException(String message) {
        super(message);
    }
}
