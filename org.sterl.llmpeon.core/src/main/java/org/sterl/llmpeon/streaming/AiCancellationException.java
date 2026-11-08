package org.sterl.llmpeon.streaming;

import org.sterl.llmpeon.exception.AbstractPeonException;

import lombok.Getter;

@Getter
public class AiCancellationException extends AbstractPeonException {

    private static final long serialVersionUID = 1L;
    private final String partialThink;
    private final String partialResult;
    
    public AiCancellationException(String msg, String partialThink, String partialResult) {
        super(msg);
        this.partialThink = partialThink;
        this.partialResult = partialResult;
    }

    public AiCancellationException(String partialThink, String partialResult) {
        super("AI call canceled ...");
        this.partialThink = partialThink;
        this.partialResult = partialResult;
    }
}
