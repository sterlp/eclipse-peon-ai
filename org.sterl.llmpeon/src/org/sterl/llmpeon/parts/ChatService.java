package org.sterl.llmpeon.parts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sterl.llmpeon.parts.config.LlmConfig;

import java.lang.reflect.Method;
import java.time.Duration;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
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
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;

public class ChatService {
    private final LlmConfig config;
    private final ChatMemory memory = MessageWindowChatMemory.withMaxMessages(100);
    private ChatModel model;

    SystemMessage system = SystemMessage.systemMessage("""
            You are a coding assistant helping developers solve technical tasks. Use available tools to access needed resources.
            Tool usage:
            - If a tool is available to do the job, use it

            When information is missing:
            - Ask the developer directly
            - If a tool doesn't exist, describe what's needed and what the developer should implement

            Communication style:
            - Be precise and concise
            - Use dev-to-dev language
            - Keep responses short and actionable
            """);

    private final List<ToolSpecification> toolSpecs = new ArrayList<>();
    private final Map<String, ToolExecutor> toolExecutors = new HashMap<>();

    public ChatService(LlmConfig config) {
        this.config = config;
        updateConfig(config);
    }

    public void updateConfig(LlmConfig config) {
        this.model = OllamaChatModel.builder()
                .timeout(Duration.ofMinutes(2))
                .baseUrl(config.url())
                .modelName(config.model())
                .think(config.thinkingEnabled())
                .build();
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

            // https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/tools.md
            if (!toolSpecs.isEmpty()) {
                request.toolSpecifications(toolSpecs);
            }
            response = model.chat(request.build());
            memory.add(response.aiMessage());

            if (response.aiMessage().hasToolExecutionRequests()) {
                for (var tr : response.aiMessage().toolExecutionRequests()) {
                    var executor = toolExecutors.get(tr.name());
                    String result;
                    if (executor != null) {
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

    /**
     * Registers any object that has methods annotated with {@link Tool}.
     * Existing tools with the same name will be replaced.
     */
    public void addTool(Object toolObject) {
        for (Method method : toolObject.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                var spec = ToolSpecifications.toolSpecificationFrom(method);
                // remove old spec with same name if present
                toolSpecs.removeIf(s -> s.name().equals(spec.name()));
                toolSpecs.add(spec);
                toolExecutors.put(spec.name(), new DefaultToolExecutor(toolObject, method));
            }
        }
    }
}
