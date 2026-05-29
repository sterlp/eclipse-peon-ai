package org.sterl.llmpeon;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.sterl.llmpeon.agent.AiCompressorAgent;
import org.sterl.llmpeon.ai.ConfiguredModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.streaming.StreamingBridge;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;
import org.sterl.llmpeon.tool.tools.CompactSessionTool;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

public abstract class AbstractChatService {

    /**
     * Conversation memory — holds only {@code UserMessage} and {@code AiMessage} entries.
     * <p>
     * {@code SystemMessage} must NOT be added here. The system prompt and standing orders
     * (selected resource, AGENTS.md, plan hint) are assembled fresh on every call as
     * {@link #buildStaticMessages()} and prepended to the request without touching memory.
     * Keeping system messages out of memory ensures the compressor sees only real
     * conversation turns and that compressed summaries survive subsequent compressions.
     */
    protected final ChatMemory memory = MessageWindowChatMemory.builder()
            .id(this)
            .maxMessages(500000)
            .build();
    protected final ConfiguredModel configuredModel;

    protected final ToolService toolService;
    private final List<ChatMessage> userContextInformations = new ArrayList<>();
    private volatile int contextTokenSize = 0;

    /**
     * One-shot system prompt set by a slash command invocation. When non-null the next call to
     * {@link #buildStaticMessages()} uses this value as the system prompt instead of
     * {@link #getSystemPrompt()} and clears the field. Subsequent calls revert to the base prompt.
     */
    private String oneShotSystemPrompt;

    protected AbstractChatService(ConfiguredModel configuredModel, ToolService toolService,
            SkillService skillService) {
        this.toolService = toolService;
        this.configuredModel = configuredModel;
        updateConfig(configuredModel.getConfig());
    }

    protected abstract String getSystemPrompt();
    protected abstract double getTemperature();
    
    protected Predicate<SmartToolExecutor> getToolFilter() {
        return t -> {
            if (t.getTool() instanceof CompactSessionTool) {
                return contextTokenSize > configuredModel.getConfig().getAutoCompactAfter() * 0.5
                        && getMessages().size() > 5;
            }
            return true;
        };
    }
    
    protected boolean includesMcpTools() { return true; }

    public int tokenContextUsedInPercent() {
        float used = contextTokenSize;
        if (used < 100) return 0;
        return Math.round(used / Math.min(configuredModel.getAutoCompactAfter(), 4000));
    }

    public boolean hasUserText(String message) {
        if (StringUtil.hasNoValue(message)) return true;
        return this.memory.messages().stream()
            .filter(m -> m instanceof UserMessage)
            .map(m -> (UserMessage)m)
            .anyMatch(um -> um.hasSingleText() && um.singleText().contains(message));
    }

    public ChatResponse call(String message, AiMonitor monitor) {
        monitor = AiMonitor.nullSafety(monitor);
        monitor.onCallStart(message);
        // auto compress if we are close to full before we start
        if (StringUtil.hasValue(message)) {
            if (configuredModel.getAutoCompactAfter() < contextTokenSize) compressContext(monitor);
        }

        var start = Instant.now();
        var staticMessages = buildStaticMessages();
        var bridge = new StreamingBridge();
        var response = toolService.executeLoop(
                new ToolLoopRequest(memory, configuredModel.getChatModel(), bridge)
                        .staticMessages(staticMessages)
                        .userContextInformations(userContextInformations)
                        .userMessage(message == null ? null : UserMessage.from(message))
                        .monitor(monitor)
                        .toolFilter(getToolFilter())
                        .includeMcpTools(includesMcpTools())
                        .temperature(getTemperature())
                        .onLoop(this::updateTokenCount));

        updateTokenCount(response);
        monitor.onCallCompleted(response, Duration.between(start, Instant.now()));
        return response;
    }

    public ChatResponse compressContext(AiMonitor monitor) {
        var response = new AiCompressorAgent(
                    configuredModel.getChatModel(), configuredModel.getConfig().getDevTemperature() < 1.0 ? 0.1 : null)
                .call(memory.messages(), monitor);
        memory.clear();
        memory.add(AiMessage.from("[Context summary]\n" + response.aiMessage().text()));
        updateTokenCount(response);
        return response;
    }

    @Deprecated
    public void updateConfig(LlmConfig config) {
        configuredModel.updateConfig(config);
    }

    public List<ChatMessage> getUserContextInformations() {
        return Collections.unmodifiableList(userContextInformations);
    }

    /**
     * added directly above the user input to avoid cache invalidations
     */
    public void setUserContextInformations(List<ChatMessage> orders) {
        this.userContextInformations.clear();
        if (orders != null) this.userContextInformations.addAll(orders);
    }

    public void clear() {
        memory.clear();
        contextTokenSize = 0;
    }

    public void addMessage(ChatMessage message) { memory.add(message); }
    public List<ChatMessage> getMessages() { return memory.messages(); }
    public int getContextSize() { return contextTokenSize; }
    public int getAutoCompactAfter() { return configuredModel.getAutoCompactAfter(); }

    private List<ChatMessage> buildStaticMessages() {
        var messages = new ArrayList<ChatMessage>();
        var override = consumeOneShotSystemPrompt();
        messages.add(SystemMessage.from(override != null ? override : getSystemPrompt()));
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

    private void updateTokenCount(ChatResponse response) {
        TokenUsage usage = response.metadata() != null ? response.metadata().tokenUsage() : null;
        if (usage != null && usage.totalTokenCount() != null) {
            contextTokenSize = usage.totalTokenCount();
        } else {
            contextTokenSize = estimateTokens();
        }
    }

    private int estimateTokens() {
        int chars = 0;
        for (var msg : memory.messages()) chars += charCount(msg);
        return chars / 4;
    }

    private int charCount(ChatMessage msg) {
        if (msg instanceof UserMessage um) return um.singleText().length();
        if (msg instanceof AiMessage am) {
            return am.text() != null ? am.text().length() : 0;
        }
        if (msg instanceof ToolExecutionResultMessage tr) return tr.text().length();
        return 0;
    }
}
