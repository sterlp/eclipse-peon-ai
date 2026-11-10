package org.sterl.llmpeon.exception;

public abstract class AbstractPeonException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AbstractPeonException(String message, Throwable cause) {
        super(message, cause);
    }

    public AbstractPeonException(String message) {
        super(message);
    }
}
