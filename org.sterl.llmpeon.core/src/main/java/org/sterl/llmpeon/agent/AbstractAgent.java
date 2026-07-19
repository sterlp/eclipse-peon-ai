package org.sterl.llmpeon.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.Getter;

public abstract class AbstractAgent implements AiAgent {

    @Getter
    protected final ThreadSafeMemory memory = new ThreadSafeMemory();
    protected final ConfiguredChatModel configuredModel;

    protected final ToolService toolService;
    
    private final List<ChatMessage> staticContext = new ArrayList<>();
    private final List<String> userContextInformations = new ArrayList<>();
    
    protected AbstractAgent(ConfiguredChatModel configuredModel, ToolService toolService) {
        this.toolService = toolService;
        this.configuredModel = configuredModel;
        
        Objects.requireNonNull(this.configuredModel, "ConfiguredChatModel cannot be null");
        Objects.requireNonNull(this.toolService, "ToolService cannot be null");
    }

    public abstract Double getTemperature();

    /** Returns the configured model name for this agent type, or null to use default. */
    @Override
    public String getAgentModelName() {
        return configuredModel.getConfig().getModel();
    }
    @Override
    public boolean setAgentModelName(String modelName) {
        return this.configuredModel.withModel(modelName);
    }

    /**
     * Apply only static filters to tools -- any change kills the KV cache!
     * https://github.com/ggml-org/llama.cpp/issues/22746#issuecomment-4630455537
     */
    protected Predicate<SmartToolExecutor> getToolFilter() {
        return p -> true;
    }

    /**
     * Filters tools by name — applied to MCP tools, which {@link #getToolFilter()} cannot see.
     * Default: allow all. Overridden by custom agents to enforce their tool allowlist.
     */
    protected Predicate<String> getToolNameFilter() {
        return n -> true;
    }

    /** True if the given built-in tool is offered to the LLM for this agent (UI introspection). */
    public boolean isToolActive(SmartToolExecutor exec) {
        return getToolFilter().test(exec);
    }

    /** True if the given MCP tool name is offered to the LLM for this agent (UI introspection). */
    public boolean isMcpToolActive(String toolName) {
        return getToolNameFilter().test(toolName);
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

    @Override
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
            var textContents = contents.stream()
                    .map(TextContent::from)
                    .toArray(TextContent[]::new);
            addMessage(UserMessage.from(textContents));
        }

        var start = Instant.now();
        var staticMessages = buildStaticMessages();
        var response = toolService.executeLoop(
                ToolLoopRequest.builder()
                    .memory(memory)
                    .chatModel(configuredModel)
                    .staticMessages(staticMessages)
                    .monitor(monitor)
                    .toolFilter(getToolFilter())
                    .toolNameFilter(getToolNameFilter())
                    .temperature(getTemperature())
                    .modelName(getAgentModelName())
                    .standingOrders(List.copyOf(userContextInformations))
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

    private List<ChatMessage> buildStaticMessages() {
        var messages = new ArrayList<ChatMessage>();
        messages.add(SystemMessage.from(getSystemPrompt()));
        messages.addAll(staticContext);
        return messages;
    }
}
