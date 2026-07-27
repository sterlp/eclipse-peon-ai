package org.sterl.llmpeon;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.Getter;

/**
 * Test helper that creates real StreamingChatModel implementations to avoid Mockito mocking issues on Java 26.
 */
public class StreamMock {

    @Getter
    private volatile ChatRequest lastRequest;
    
    private final AtomicInteger callCount = new AtomicInteger();

    /** Create a test StreamingChatModel that captures requests and returns predefined responses. */
    public StreamingChatModel buildMock(Function<ChatRequest, ChatResponse> fn) {
        return new StreamingChatModel() {
            @Override
            public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
                callCount.incrementAndGet();
                lastRequest = request;
                try {
                    ChatResponse response = fn.apply(request);
                    handler.onCompleteResponse(response);
                } catch (Exception e) {
                    handler.onError(e);
                }
            }
        };
    }
    
    public int getCallCount() {
        return callCount.get();
    }
    
    public void reset() {
        callCount.set(0);
        lastRequest = null;
    }
}
