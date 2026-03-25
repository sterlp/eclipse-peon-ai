package org.sterl.llmpeon.agent;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

public class AiSearchAgent {

    final SystemMessage system = SystemMessage.systemMessage("""
            You are a focused search assistant. Your only job is to find information.

            Strategy:
            - Use available tools to explore files, read source code, fetch documentation, or navigate types.
            - Prefer targeted searches over broad ones. Read only what is relevant to the question.
            - Stop using tools as soon as you have enough information to answer.

            Output:
            - Return a concise, factual answer.
            - Include the relevant file paths and only the minimal code excerpts that directly answer the question.
            - Omit irrelevant detail.
            - Do not ask follow-up questions. If you cannot find the answer, say so and explain what you tried.
            """);

    private final ChatModel chatModel;
    private final ChatRequest.Builder request = ChatRequest.builder();

    public AiSearchAgent(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public ChatResponse call(List<ChatMessage> inMessages, AiMonitor monitor) {
        var messages = new ArrayList<ChatMessage>(inMessages);
        messages.addFirst(system);
        return chatModel.chat(request
                .temperature(0.0)
                .messages(messages)
                .build());
    }

    public void withTools(List<ToolSpecification> tools) {
        request.toolSpecifications(tools);
    }
}
