package org.sterl.llmpeon;

import static org.junit.Assert.fail;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.jspecify.annotations.NonNull;
import org.sterl.llmpeon.shared.ChatMessageUtil;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

/**
 * Test helper that creates real StreamingChatModel implementations to avoid Mockito mocking issues on Java 26.
 */
public class StreamMock {

    private volatile ChatRequest lastRequest;
    
    private final AtomicInteger callCount = new AtomicInteger();
    
    public StreamingChatModel buildOkMock() {
        return buildMock(e -> ChatResponse.builder().aiMessage(AiMessage.aiMessage("Ok")).build());
    }

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

    @SuppressWarnings("unchecked")
    public <T extends ChatMessage> Optional<T> getLast(Class<@NonNull T> clazz) {
        if (lastRequest == null) return Optional.empty();
        var msgs = lastRequest.messages();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            if (clazz.isAssignableFrom(msgs.get(i).getClass())) return Optional.of((T)msgs.get(i));
        }
        return Optional.empty();
    }
    
    public List<String> getLastUserMessagesAsString() {
        return getLast(UserMessage.class).get().contents().stream().map(c -> ((TextContent)c).text()).toList();
    }
    
    public List<String> allAsString() {
        if (lastRequest == null) return List.of();
        var result = new LinkedList<String>();
        for (ChatMessage chatMessage : lastRequest.messages()) {
            result.add(ChatMessageUtil.toString(chatMessage));
        }
        return result;
    }

    public int count(String value) {
        if (lastRequest == null) return 0;
        int result = 0;
        for (ChatMessage chatMessage : lastRequest.messages()) {
            if (ChatMessageUtil.toString(chatMessage).contains(value)) result++;
        }
        return result;
    }
    
    public void assertCount(String value, int count) {
        var c = count(value);
        if (c != count) {
            fail("Expected to find times " + value + " " + count + " but found " + c + " in: " + System.lineSeparator()
                + String.join(System.lineSeparator(), allAsString())
            );
        }
    }
    
    public ChatRequest getLastRequest() {
        return lastRequest;
    }
}
