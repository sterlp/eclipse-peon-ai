package org.sterl.llmpeon.parts;

import java.util.ArrayList;
import java.util.List;

import org.sterl.llmpeon.parts.config.LlmConfig;
import org.sterl.llmpeon.parts.tools.SelectedFileTool;

import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;

public class ChatService {
    private final LlmConfig config;
    private final ChatMemory memory = MessageWindowChatMemory.withMaxMessages(100);
    private ChatModel model;

    SystemMessage system = SystemMessage.systemMessage("""
            You are a coding assistant helping developers solve technical tasks. Use available tools to access needed resources.
            When information is missing:
            - Ask the developer directly
            - If a tool doesn't exist, describe what's needed and what the developer should implement

            Communication style:
            - Be precise and concise
            - Use dev-to-dev language
            - Keep responses short and actionable
            """);
    SelectedFileTool selectedFileTool;

    public ChatService(LlmConfig config) {
        this.config = config;
        updateConfig(config);
    }
    
    public void updateConfig(LlmConfig config) {
        this.model = OllamaChatModel.builder()
                .baseUrl(config.url())
                .modelName(config.model())
                .think(config.thinkingEnabled()).build();
    }

    public LlmConfig getConfig() {
        return config;
    }

    public ChatResponse sendMessage(String text) {
        if (text == null)
            return null;
        text = text.trim();
        if (text.isEmpty())
            return null;

        memory.add(UserMessage.from(text));

        ChatResponse response;

        do {
            var m = new ArrayList<ChatMessage>();
            m.add(system);
            m.addAll(memory.messages());
            var request = ChatRequest.builder().messages(m);

            // https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/tools.md
            if (selectedFileTool != null) {
                request.toolSpecifications(ToolSpecifications.toolSpecificationsFrom(selectedFileTool));
            }
            response = model.chat(request.build());
            memory.add(response.aiMessage());

            if (response.aiMessage().hasToolExecutionRequests()) {
                for (var tr : response.aiMessage().toolExecutionRequests()) {
                    memory.add(ToolExecutionResultMessage.from(
                            tr.id(), tr.name(), 
                            selectedFileTool.readCurrentFile()));
                }
            }

        } while (response.aiMessage().hasToolExecutionRequests());

        System.err.println(response.aiMessage());
        System.err.println(response.aiMessage().text());

        return response;
    }

    public List<ChatMessage> getMessages() {
        return memory.messages();
    }

    public void addTool(SelectedFileTool selectedFileTool) {
        this.selectedFileTool = selectedFileTool;
    }
}
