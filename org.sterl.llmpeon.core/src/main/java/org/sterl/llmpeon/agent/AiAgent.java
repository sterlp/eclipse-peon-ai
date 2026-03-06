package org.sterl.llmpeon.agent;

import java.util.Arrays;
import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

public interface AiAgent {

    ChatResponse call(List<ChatMessage> messages, AiMonitor monitor);

    default ChatResponse call(String messages, AiMonitor monitor) {
        return call(UserMessage.from(messages), monitor);
    }
    default ChatResponse call(UserMessage message, AiMonitor monitor) {
        return call(Arrays.asList(message), monitor);
    }
    
    default void withTools(List<ToolSpecification> tools) {}
}
