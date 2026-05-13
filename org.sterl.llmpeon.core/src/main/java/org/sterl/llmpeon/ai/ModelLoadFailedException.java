package org.sterl.llmpeon.ai;

public class ModelLoadFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModelLoadFailedException(String message, Throwable cause) {
        super(message, cause);
    }

}
