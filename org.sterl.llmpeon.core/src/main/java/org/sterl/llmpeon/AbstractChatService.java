package org.sterl.llmpeon;

import java.nio.file.Path;
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
import org.sterl.llmpeon.skill.Skill;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.streaming.StreamingBridge;
import org.sterl.llmpeon.template.TemplateContext;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

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

    protected final TemplateContext templateContext;
    @Deprecated // skills should be slash actions
    protected final SkillService skillService;
    protected final ToolService toolService;
    private List<ChatMessage> standingOrders = Collections.emptyList();
    private int tokenSize = 0;

    /**
     * One-shot system prompt set by a slash command invocation. When non-null the next call to
     * {@link #buildStaticMessages()} uses this value as the system prompt instead of
     * {@link #getSystemPrompt()} and clears the field. Subsequent calls revert to the base prompt.
     */
    private String oneShotSystemPrompt;

    protected AbstractChatService(ConfiguredModel configuredModel, ToolService toolService,
            SkillService skillService, TemplateContext templateContext) {
        this.toolService = toolService;
        this.skillService = skillService;
        this.templateContext = templateContext;
        this.configuredModel = configuredModel;
        updateConfig(configuredModel.getConfig());
    }

    protected abstract String getSystemPrompt();
    protected abstract double getTemperature();
    protected abstract Predicate<SmartToolExecutor> getToolFilter();
    protected boolean includesMcpTools() { return true; }

    public int tokenWindowUsedInPercent() {
        float used = tokenSize;
        if (used < 100) return 0;
        return Math.round(used / Math.min(configuredModel.getTokenWindow(), 4000));
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
        // auto compress if we are close to full
        if (StringUtil.hasValue(message)) {
            if (tokenWindowUsedInPercent() >= 95) compressContext(monitor);
            memory.add(UserMessage.from(message));
        }

        var start = Instant.now();
        var staticMessages = buildStaticMessages();
        var bridge = new StreamingBridge();
        var response = toolService.executeLoop(
                new ToolLoopRequest(memory, configuredModel.getChatModel(), bridge)
                        .staticMessages(staticMessages)
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
        var response = new AiCompressorAgent(configuredModel.getChatModel()).call(memory.messages(), monitor);
        memory.clear();
        memory.add(UserMessage.from("[Context summary]\n" + response.aiMessage().text()));
        updateTokenCount(response);
        return response;
    }

    @Deprecated
    public void updateConfig(LlmConfig config) {
        configuredModel.updateConfig(config);
        if (config.getSkillDirectory() != null) {
            this.templateContext.setSkillDirectory(config.getSkillDirectory());
            try {
                this.skillService.refresh(Path.of(config.getSkillDirectory()));
            } catch (Exception e) {
                throw new RuntimeException("Failed to load skills from " + config.getSkillDirectory(), e);
            }
        }
        templateContext.setTokenWindow(getTokenWindow());
    }

    public List<ChatMessage> getStandingOrders() {
        return Collections.unmodifiableList(standingOrders);
    }

    public void setStandingOrders(List<ChatMessage> orders) {
        this.standingOrders = orders == null ? Collections.emptyList() : new ArrayList<>(orders);
    }

    public void clear() {
        memory.clear();
        tokenSize = 0;
        templateContext.setTokenSize(0);
    }

    public void addMessage(ChatMessage message) { memory.add(message); }
    public List<ChatMessage> getMessages() { return memory.messages(); }
    public int getTokenSize() { return tokenSize; }
    public int getTokenWindow() { return configuredModel.getTokenWindow(); }
    public TemplateContext getTemplateContext() { return templateContext; }
    public List<Skill> getSkills() { return skillService.getSkills(); }

    private List<ChatMessage> buildStaticMessages() {
        var messages = new ArrayList<ChatMessage>();
        var override = consumeOneShotSystemPrompt();
        messages.add(SystemMessage.from(override != null ? override : getSystemPrompt()));

        messages.addAll(standingOrders);
        // Skills are still advertised even when a slash command overrode the base prompt; the
        // command body and the skill catalog are orthogonal.
        var skillMsg = skillService.skillMessage(templateContext);
        if (skillMsg != null) messages.add(skillMsg);
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
            tokenSize = usage.totalTokenCount();
        } else {
            tokenSize = estimateTokens();
        }
        templateContext.setTokenSize(tokenSize);
    }

    private int estimateTokens() {
        int chars = 0;
        for (var msg : memory.messages()) chars += charCount(msg);
        return chars / 4;
    }

    private int charCount(ChatMessage msg) {
        if (msg instanceof UserMessage um) return um.singleText().length();
        if (msg instanceof AiMessage am) return am.text() != null ? am.text().length() : 0;
        if (msg instanceof ToolExecutionResultMessage tr) return tr.text().length();
        return 0;
    }
}
