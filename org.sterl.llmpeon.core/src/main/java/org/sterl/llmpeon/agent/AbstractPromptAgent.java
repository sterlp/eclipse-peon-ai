package org.sterl.llmpeon.agent;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

public abstract class AbstractPromptAgent implements AiAgent {

    protected final ChatModel chatModel;
    private final ChatRequest.Builder request = ChatRequest.builder();

    protected AbstractPromptAgent(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    protected abstract String getPrompt();

    protected abstract double getTemperature();

    @Override
    public ChatResponse call(List<ChatMessage> inMessages, AiMonitor monitor) {
        var messages = new ArrayList<ChatMessage>(inMessages);
        messages.addFirst(SystemMessage.from(getPrompt()));
        return chatModel.chat(request
                .temperature(getTemperature())
                .messages(toOneSystemMessage(messages))
                .build());
    }

    @Override
    public void withTools(List<ToolSpecification> tools) {
        request.toolSpecifications(tools);
    }
}
