package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.streaming.StreamingBridge;
import org.sterl.llmpeon.tool.tools.WebFetchTool;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
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
        var requestRef  = new AtomicReference<ChatRequest>();
        var aiThinkMsg = AiMessage.builder().thinking("I think").build();
        var aiResponse = AiMessage.from("Hello User");
        var think = new AtomicBoolean(true);
        var cm = mockWithHandler(req -> {
            requestRef.set(req);
            if (think.getAndSet(false)) return ChatResponse.builder().aiMessage(aiThinkMsg).build();
            return ChatResponse.builder().aiMessage(aiResponse).build();
        });
        var memory = MessageWindowChatMemory.withMaxMessages(50);
        // WHEN
        memory.add(UserMessage.from("Hello"));
        subject.executeLoop(new ToolLoopRequest(memory, cm, new StreamingBridge()));

        // THEN
        verify(cm, times(2)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        // AND
        assertThat(memory.messages().get(1)).isEqualTo(aiThinkMsg);
        assertThat(((UserMessage)memory.messages().get(2)).singleText()).contains("ask a clarifying question");
        assertThat(memory.messages().get(3)).isEqualTo(aiResponse);
        // AND last request contains all our messages
        // 1 is the Hello
        assertThat(requestRef.get().messages().get(1)).isEqualTo(aiThinkMsg);
        // 2 is the ask a question
    }

    @Test
    void test_adds_user_message_and_one_system_message() {
        // GIVEN
        var requestRef  = new AtomicReference<ChatRequest>();
        var sys1        =  SystemMessage.from("sys1");
        var sys2        =  SystemMessage.from("sys2");
        var aiMessage   =  AiMessage.from("Hello from AI");
        var userMessage = UserMessage.from("Hello");
        var cm = mockWithHandler(req -> {
            requestRef.set(req);
            return ChatResponse.builder().aiMessage(aiMessage).build();
        });
        
        var mem = MessageWindowChatMemory.withMaxMessages(50);
        mem.add(userMessage);
        // WHEN
        subject.executeLoop(new ToolLoopRequest(mem, cm, new StreamingBridge())
                .staticMessages(Arrays.asList(sys1, sys2)));

        // THEN
        verify(cm, times(1)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        assertThat(requestRef.get().messages().get(1)).isEqualTo(userMessage);
        assertThat(((SystemMessage)requestRef.get().messages().get(0)).text()).contains("sys1", "sys2");
        // AND
        assertThat(mem.messages().get(0)).isEqualTo(userMessage);
        assertThat(mem.messages().get(1)).isEqualTo(aiMessage);
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
