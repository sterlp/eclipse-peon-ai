package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.streaming.StreamingBridge;
import org.sterl.llmpeon.tool.tools.WebFetchTool;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

class ToolServiceTest {

    final ToolService subject = new ToolService();

    @Test
    void test_getTool() {
        var tool = subject.getTool(WebFetchTool.class);
        assertTrue(tool.get() instanceof WebFetchTool);
    }

    @Test
    void testLoopEndsWithTextMessageOnly() {
        // GIVEN
        var cm = mock(StreamingChatModel.class);
        var answer = ChatResponse.builder().aiMessage(AiMessage.from("Hello")).build();
        doAnswer(inv -> {
            inv.getArgument(1, StreamingChatResponseHandler.class).onCompleteResponse(answer);
            return null;
        }).when(cm).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        var memory = MessageWindowChatMemory.withMaxMessages(50);
        // WHEN
        memory.add(UserMessage.from("Hello"));
        var response = subject.executeLoop(
                new ToolLoopRequest(memory, cm, new StreamingBridge()));

        // THEN
        assertEquals("Hello", response.aiMessage().text());
        // AND
        verify(cm, times(1)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
    }

    @Test
    void testLoopsOn_result_think_only() {
        // GIVEN
        var cm = mockWithHandler(req -> {
            ChatResponse cr = req.messages().size() < 2
                    ? ChatResponse.builder().aiMessage(AiMessage.builder().thinking("I think").build()).build()
                    : ChatResponse.builder().aiMessage(AiMessage.from("Hello User")).build();
            return cr;
        });
        var memory = MessageWindowChatMemory.withMaxMessages(50);
        // WHEN
        memory.add(UserMessage.from("Hello"));
        subject.executeLoop(
                new ToolLoopRequest(memory, cm, new StreamingBridge()));

        // THEN
        verify(cm, times(2)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        // AND
        assertTrue(((UserMessage)memory.messages().get(2)).singleText().contains("Continue"));
        assertTrue(((UserMessage)memory.messages().get(2)).singleText().contains("I think"));
    }

    @Test
    void test_adds_user_message() {
        // GIVEN
        var requestRef = new AtomicReference<ChatRequest>();
        var cm = mockWithHandler(req -> {
            requestRef.set(req);
            return ChatResponse.builder().aiMessage(AiMessage.from("Hello from AI")).build();
        });

        var msg = UserMessage.from("Hello");
        // WHEN
        subject.executeLoop(
                new ToolLoopRequest(MessageWindowChatMemory.withMaxMessages(50), cm, new StreamingBridge())
                    .userMessage(msg)
        );

        // THEN
        verify(cm, times(1)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        // AND
        assertThat(requestRef.get().messages().getLast()).isEqualTo(msg);
    }
    
    public StreamingChatModel mockWithHandler(Function<ChatRequest, ChatResponse> fn) {
        var cm = mock(StreamingChatModel.class);
        doAnswer(inv -> {
            ChatRequest req = inv.getArgument(0, ChatRequest.class);
            ChatResponse cr = fn.apply(req);
            var handler = inv.getArgument(1, StreamingChatResponseHandler.class);
            handler.onCompleteResponse(cr);
            return null;
        }).when(cm).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        return cm;
    }
}
