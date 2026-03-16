package org.sterl.llmpeon.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
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
    
    /**
     * Some local LLMs only support one system message, so we have to collect them.
     */
    default List<ChatMessage> toOneSystemMessage(List<ChatMessage> messages) {
        var result = new ArrayList<ChatMessage>();
        var systemMessage = "";
        for (var m : messages) {
            if (m instanceof SystemMessage sm) {
                systemMessage = sm.text() + "\n";
            } else {
                result.add(m);
            }
        }
        if (StringUtil.hasValue(systemMessage)) {
            result.addFirst(SystemMessage.from(systemMessage));
        }
        return result;
    }
}
