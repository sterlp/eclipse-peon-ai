package org.sterl.llmpeon;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.sterl.llmpeon.agent.AiCompressorAgent;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.skill.SkillRecord;
import org.sterl.llmpeon.skill.SkillService;
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
import dev.langchain4j.model.chat.ChatModel;
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
    protected ChatModel chatModel;
    protected LlmConfig config;
    protected final TemplateContext templateContext;
    protected final SkillService skillService;
    protected final ToolService toolService;
    private List<ChatMessage> standingOrders = Collections.emptyList();
    private int tokenSize = 0;

    protected AbstractChatService(LlmConfig config, ToolService toolService,
            SkillService skillService, TemplateContext templateContext) {
        this.toolService = toolService;
        this.skillService = skillService;
        this.templateContext = templateContext;
        updateConfig(config);
    }

    protected abstract String getSystemPrompt();
    protected abstract double getTemperature();
    protected abstract Predicate<SmartToolExecutor> getToolFilter();
    protected boolean includesMcpTools() { return true; }

    public ChatResponse call(String message, AiMonitor monitor) {
        monitor = AiMonitor.nullSafety(monitor);
        if (StringUtil.hasValue(message)) memory.add(UserMessage.from(message));

        var staticMessages = buildStaticMessages();
        var response = toolService.executeLoop(
                new ToolLoopRequest(memory, chatModel)
                        .staticMessages(staticMessages)
                        .monitor(monitor)
                        .toolFilter(getToolFilter())
                        .includeMcpTools(includesMcpTools())
                        .temperature(getTemperature())
                        .onLoop(this::updateTokenCount));

        updateTokenCount(response);
        return response;
    }

    public ChatResponse compressContext(AiMonitor monitor) {
        var response = new AiCompressorAgent(chatModel).call(memory.messages(), monitor);
        memory.clear();
        memory.add(UserMessage.from("[Context summary]\n" + response.aiMessage().text()));
        updateTokenCount(response);
        return response;
    }

    public void updateConfig(LlmConfig config) {
        this.config = config;
        this.chatModel = config.build();
        if (config.skillDirectory() != null) {
            this.templateContext.setSkillDirectory(config.skillDirectory());
            try {
                this.skillService.refresh(Path.of(config.skillDirectory()));
            } catch (Exception e) {
                throw new RuntimeException("Failed to load skills from " + config.skillDirectory(), e);
            }
        }
        templateContext.setTokenWindow(config.tokenWindow());
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
    public int getTokenWindow() { return config.tokenWindow(); }
    public LlmConfig getConfig() { return config; }
    public ChatModel getChatModel() { return chatModel; }
    public TemplateContext getTemplateContext() { return templateContext; }
    public List<SkillRecord> getSkills() { return skillService.getSkills(); }

    private List<ChatMessage> buildStaticMessages() {
        var messages = new ArrayList<ChatMessage>();
        messages.add(SystemMessage.from(getSystemPrompt()));
        
        messages.addAll(standingOrders);
        if (skillService.hasSkills()) {
            messages.add(skillService.skillMessage(templateContext));
        }
        return messages;
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
