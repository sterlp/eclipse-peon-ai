package org.sterl.llmpeon.parts.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.sterl.llmpeon.parts.config.LlmConfig;
import org.sterl.llmpeon.parts.tools.ToolService;

import dev.langchain4j.data.message.AiMessage;
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
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.ToolExecutor;

public class ChatService {
    private LlmConfig config;
    private final ToolService toolService;
    private final ChatMemory memory = MessageWindowChatMemory.withMaxMessages(100);
    private ChatModel model;
    private List<LlmObserver> observers = new ArrayList<>();
    private int lastInputTokens = 0;

    private static final SystemMessage COMPRESS_SYSTEM = SystemMessage.systemMessage("""
            You are a conversation compressor. Compress the conversation into a concise briefing.

            Preserve:
            - Key decisions and their rationale
            - Current task state and pending work
            - Important file paths and code references
            - Tool results that are still relevant

            Remove:
            - Duplicate information and redundant exchanges
            - Verbose tool outputs (keep only the essential result)
            - Superseded decisions (only keep the final decision)
            - Greetings, filler, and pleasantries

            Format as a structured summary the developer can continue working from.
            Be as short as possible while keeping all essential context.
            """);

    SystemMessage system = SystemMessage.systemMessage("""
            You are a coding assistant helping developers solve technical tasks. Use available tools to access needed resources.
            Tool usage:
            - If a tool is available to do the job, automatically use it
            - Where is a tool to determine if the developer has currently a file selected 

            When information is missing:
            - Ask the developer directly
            - If a tool doesn't exist, describe what's needed and what the developer should implement
            - assume that the developer is talking about the current selected file if no other context is given and the tool returns a selected file

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
        this.config = config;
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

    public int getTokenWindow() {
        return config.tokenWindow();
    }

    public int getLastInputTokens() {
        return lastInputTokens;
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
            updateTokenCount(response);
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

        // auto-compress at 95%
        if (lastInputTokens >= config.tokenWindow() * 0.95) {
            compressContext();
        }

        return response;
    }

    /**
     * Compresses the current conversation via CompressAgent, clears memory,
     * and adds only the compressed summary as a new starting point.
     * @return the compressed summary text, or empty string if nothing to compress
     */
    public String compressContext() {
        var messages = memory.messages();
        if (messages.isEmpty()) return "";

        // send all messages with compress system prompt, no tools
        var m = new ArrayList<ChatMessage>();
        m.add(COMPRESS_SYSTEM);
        m.addAll(messages);
        var request = ChatRequest.builder().messages(m).build();
        var response = model.chat(request);
        String summary = response.aiMessage().text();

        memory.clear();
        memory.add(AiMessage.from(summary));

        lastInputTokens = estimateTokens();
        return summary;
    }

    private void updateTokenCount(ChatResponse response) {
        TokenUsage usage = response.metadata() != null ? response.metadata().tokenUsage() : null;
        if (usage != null && usage.inputTokenCount() != null) {
            lastInputTokens = usage.inputTokenCount();
        } else {
            lastInputTokens = estimateTokens();
        }
    }

    /** Simple token estimation: ~4 characters per token */
    private int estimateTokens() {
        int chars = system.text().length();
        for (var msg : memory.messages()) {
            if (msg instanceof UserMessage um) {
                chars += um.singleText().length();
            } else if (msg instanceof AiMessage am) {
                chars += am.text() != null ? am.text().length() : 0;
            } else if (msg instanceof ToolExecutionResultMessage tr) {
                chars += tr.text().length();
            }
        }
        return chars / 4;
    }

    public List<ChatMessage> getMessages() {
        return memory.messages();
    }
}
