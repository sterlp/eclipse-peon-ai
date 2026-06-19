package org.sterl.llmpeon;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.sterl.llmpeon.agent.AiCompressorAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.Getter;

public abstract class AbstractChatService {

    @Getter
    protected final ThreadSafeMemory memory = new ThreadSafeMemory();
    protected final ConfiguredChatModel configuredModel;

    protected final ToolService toolService;
    
    private final List<ChatMessage> staticContext = new ArrayList<>();
    private final List<String> userContextInformations = new ArrayList<>();

    /**
     * One-shot system prompt set by a slash command invocation. When non-null the next call to
     * {@link #buildStaticMessages()} uses this value as the system prompt instead of
     * {@link #getSystemPrompt()} and clears the field. Subsequent calls revert to the base prompt.
     */
    private String oneShotSystemPrompt;

    protected AbstractChatService(ConfiguredChatModel configuredModel, ToolService toolService) {
        this.toolService = toolService;
        this.configuredModel = configuredModel;
    }

    protected abstract String getSystemPrompt();
    public abstract double getTemperature();

    /** Returns the configured model name for this agent type, or null to use default. */
    public String getAgentModelName() {
        return configuredModel.getConfig().getModel();
    }

    /**
     * Apply only static filters to tools -- any change kills the KV cache!
     * https://github.com/ggml-org/llama.cpp/issues/22746#issuecomment-4630455537
     */
    protected Predicate<SmartToolExecutor> getToolFilter() {
        return p -> true;
    }
    
    protected boolean includesMcpTools() { return true; }
    
    public boolean setModelName(AiModel modelName) {
        return this.configuredModel.withModel(modelName);
    }

    public int tokenContextUsedInPercent() {
        float used = memory.getTotalTokenUsed();
        if (used < 100) return 0;
        return Math.round(used / Math.min(configuredModel.getConfig().getAutoCompactAfter(), 4000));
    }

    public boolean hasUserText(String message) {
        if (StringUtil.hasNoValue(message)) return true;
        return this.memory.containsUserMessage(message);
    }

    public ChatResponse call(String message, AiMonitor monitor) {
        monitor = AiMonitor.nullSafety(monitor);
        monitor.onCallStart(message);
        // auto compress if we are close to full before we start
        if (configuredModel.getConfig().getAutoCompactAfter() < memory.getTotalTokenUsed()) compressContext(monitor);
        
        var contents = new ArrayList<String>();
        if (userContextInformations.size() > 0) {
            userContextInformations.stream()
                    .filter(m -> !hasUserText(m))
                    .forEach(m -> contents.add(m));
        }
        
        if (StringUtil.hasValue(message)) contents.add(message);
        if (contents.isEmpty()) {
            // nothing
        } else if (contents.size() == 1) {
            addMessage(UserMessage.from(contents.getFirst()));
        } else {
            var m = contents.stream().collect(Collectors.joining("\n\n"));
            addMessage(UserMessage.from(m));
        }

        var start = Instant.now();
        var staticMessages = buildStaticMessages(monitor);
        var response = toolService.executeLoop(
                ToolLoopRequest.builder()
                    .memory(memory)
                    .chatModel(configuredModel)
                    .staticMessages(staticMessages)
                    .monitor(monitor)
                    .toolFilter(getToolFilter())
                    .includeMcpTools(includesMcpTools())
                    .temperature(getTemperature())
                    .modelName(getAgentModelName())
                    .build()
                );

        monitor.onCallCompleted(response, Duration.between(start, Instant.now()));
        return response;
    }

    public ChatResponse compressContext(AiMonitor monitor) {
        var response = new AiCompressorAgent(configuredModel)
                .call(memory.getCopy(), monitor);
        
        memory.clear();
        memory.addResult(response);
        return response;
    }

    /**
     * Only context information which doesn't change - only if we clear!
     * Otherwise we kill the KV-cache!
     */
    public void setStaticContext(List<ChatMessage> staticContext) {
        this.staticContext.clear();
        if (staticContext != null) this.staticContext.addAll(staticContext);
    }
    
    public void setUserContextInformations(List<String> userContextInformations) {
        this.userContextInformations.clear();
        if (userContextInformations != null) this.userContextInformations.addAll(userContextInformations);
    }
    
    public List<String> getUserContextInformations() {
        return new ArrayList<>(this.userContextInformations);
    }

    public void clear() {
        memory.clear();
    }

    /**
     * 1. System-Messages nur am Anfang erlaubt
     * 2. Tool-Messages NUR nach Assistant-Messages MIT tool_calls erlaubt
     * 3. Rollen müssen alternieren: user/assistant/user/assistant
     * 4. Nach User/System darf KEIN Tool kommen!
     */
    public void addMessage(ChatMessage message) {
        memory.add(message);
    }
    public int getContextSize() { return memory.getTotalTokenUsed(); }
    public int getAutoCompactAfter() { return configuredModel.getConfig().getAutoCompactAfter(); }

    private List<ChatMessage> buildStaticMessages(AiMonitor monitor) {
        var messages = new ArrayList<ChatMessage>();
        var override = consumeOneShotSystemPrompt();
        if (override == null) {
            messages.add(SystemMessage.from(getSystemPrompt()));
        } else {
            monitor.onTool("Using command as system prompt");
            messages.add(SystemMessage.from(override));
        }
        messages.addAll(staticContext);
        return messages;
    }

    /**
     * Sets a one-shot system prompt that replaces {@link #getSystemPrompt()} for the very next
     * {@link #call(String, AiMonitor)} only. Pass {@code null} to clear without consuming.
     */
    public void setOneShotSystemPrompt(String systemPrompt) {
        this.oneShotSystemPrompt = (systemPrompt == null || systemPrompt.isBlank()) ? null : systemPrompt;
    }

    public boolean hasOneShotSystemPrompt() {
        return oneShotSystemPrompt != null;
    }

    private String consumeOneShotSystemPrompt() {
        var value = oneShotSystemPrompt;
        oneShotSystemPrompt = null;
        return value;
    }
}
