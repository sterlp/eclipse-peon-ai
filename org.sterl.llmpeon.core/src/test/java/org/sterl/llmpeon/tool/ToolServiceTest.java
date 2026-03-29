package org.sterl.llmpeon.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.tool.tools.WebFetchTool;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

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
        var cm = mock(ChatModel.class);
        var answer = ChatResponse.builder().aiMessage(AiMessage.from("Hello")).build();
        when(cm.chat(any(ChatRequest.class))).thenReturn(answer);
        var memory = MessageWindowChatMemory.withMaxMessages(50);
        // WHEN
        memory.add(UserMessage.from("Hello"));
        var response = subject.executeLoop(
                new ToolLoopRequest(memory, cm));

        // THEN
        assertEquals("Hello", response.aiMessage().text());
        // AND
        verify(cm, times(1)).chat(any(ChatRequest.class));
    }

    @Test
    void testLoopsOnOnThinkOnly() {
        // GIVEN
        var cm = mock(ChatModel.class);
        when(cm.chat(any(ChatRequest.class))).thenAnswer(i -> {
            var cr = i.getArgument(0, ChatRequest.class);
            if (cr.messages().size() < 2) return ChatResponse.builder().aiMessage(AiMessage.builder().thinking("I think").build()).build();
            return ChatResponse.builder().aiMessage(AiMessage.from("Hello")).build();
        });
        var memory = MessageWindowChatMemory.withMaxMessages(50);
        // WHEN
        memory.add(UserMessage.from("Hello"));
        var response = subject.executeLoop(
                new ToolLoopRequest(memory, cm));

        // THEN
        assertEquals("Hello", response.aiMessage().text());
        // AND
        verify(cm, times(2)).chat(any(ChatRequest.class));
    }

}
