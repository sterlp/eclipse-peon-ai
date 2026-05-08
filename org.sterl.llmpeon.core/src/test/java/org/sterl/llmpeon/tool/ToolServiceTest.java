package org.sterl.llmpeon.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
        var cm = mock(StreamingChatModel.class);
        doAnswer(inv -> {
            ChatRequest req = inv.getArgument(0, ChatRequest.class);
            StreamingChatResponseHandler handler = inv.getArgument(1, StreamingChatResponseHandler.class);
            ChatResponse cr = req.messages().size() < 2
                    ? ChatResponse.builder().aiMessage(AiMessage.builder().thinking("I think").build()).build()
                    : ChatResponse.builder().aiMessage(AiMessage.from("Hello User")).build();
            handler.onCompleteResponse(cr);
            return null;
        }).when(cm).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
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

}
