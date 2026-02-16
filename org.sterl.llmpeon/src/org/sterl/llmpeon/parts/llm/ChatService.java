package org.sterl.llmpeon.parts.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.sterl.llmpeon.parts.config.LlmConfig;
import org.sterl.llmpeon.parts.tools.ToolService;

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
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.tool.ToolExecutor;

public class ChatService {
    private final LlmConfig config;
    private final ToolService toolService;
    private final ChatMemory memory = MessageWindowChatMemory.withMaxMessages(100);
    private ChatModel model;
    private List<LlmObserver> observers = new ArrayList<LlmObserver>();

    SystemMessage system = SystemMessage.systemMessage("""
            You are a coding assistant helping developers solve technical tasks. Use available tools to access needed resources.
            Tool usage:
            - If a tool is available to do the job, automatically use it

            When information is missing:
            - Ask the developer directly
            - If a tool doesn't exist, describe what's needed and what the developer should implement

            Communication style:
            - Be precise and concise
            - Use dev-to-dev language
            - Keep responses short and actionable
            """);

    public void addObserver(LlmObserver o) {
        this.observers.add(o);
    }
    public void removeObserver(LlmObserver o) {
        this.observers.remove(o);
    }

    private void informObservers(String value) {
        this.observers.forEach(o -> o.onAction(value));
    }

    public ChatService(LlmConfig config, ToolService toolService) {
        this.config = config;
        this.toolService = toolService;
        updateConfig(config);
    }

    public void updateConfig(LlmConfig config) {
        if ("openai".equals(config.providerType())) {
            var builder = OpenAiChatModel.builder()
                    .timeout(Duration.ofMinutes(2))
                    .baseUrl(config.url())
                    .modelName(config.model())
                    .apiKey(config.apiKey())
                    .maxTokens(config.tokenWindow());
            this.model = builder.build();
        } else {
            this.model = OllamaChatModel.builder()
                    .timeout(Duration.ofMinutes(2))
                    .baseUrl(config.url())
                    .modelName(config.model())
                    .think(config.thinkingEnabled())
                    .build();
        }
    }

    public LlmConfig getConfig() {
        return config;
    }

    public ChatResponse sendMessage(String text) {
        if (text != null && text.trim().length() > 0) {
            memory.add(UserMessage.from(text));
        }

        ChatResponse response;

        do {
            var m = new ArrayList<ChatMessage>();
            m.add(system);
            m.addAll(memory.messages());
            var request = ChatRequest.builder().messages(m);

            var toolSpecs = toolService.getToolSpecs();
            if (!toolSpecs.isEmpty()) {
                request.toolSpecifications(toolSpecs);
            }
            response = model.chat(request.build());
            memory.add(response.aiMessage());

            if (response.aiMessage().hasToolExecutionRequests()) {
                for (var tr : response.aiMessage().toolExecutionRequests()) {
                    ToolExecutor executor = toolService.getExecutor(tr.name());
                    String result;
                    if (executor != null) {
                        informObservers("Using " + tr.name() + " " + tr.arguments());
                        System.err.println("Using tool " + tr.name() + " with: " + tr.arguments());
                        result = executor.execute(tr, null);
                    } else {
                        result = "Error: unknown tool '" + tr.name() + "'";
                    }
                    memory.add(ToolExecutionResultMessage.from(tr.id(), tr.name(), result));
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
}
