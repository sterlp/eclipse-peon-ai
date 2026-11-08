package org.sterl.llmpeon.streaming;

import org.jspecify.annotations.NonNull;
import org.sterl.llmpeon.exception.AbstractPeonException;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.data.message.AiMessage;
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
        super("AI call canceled by user ...");
        this.partialThink = partialThink;
        this.partialResult = partialResult;
    }
    
    @NonNull
    public AiMessage toAiMessage() {
        var result = new AiMessage.Builder().text(StringUtil.trimToEmpty(partialResult) + " " + getMessage());
        if (StringUtil.hasValue(partialThink)) result.thinking(partialThink);
        return result.build();
    }
}
