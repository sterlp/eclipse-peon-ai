package org.sterl.llmpeon;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiCompressorAgent;
import org.sterl.llmpeon.agent.AiMonitor;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.skill.SkillRecord;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.template.TemplateContext;
import org.sterl.llmpeon.tool.CompressorAgentTool;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.agent.tool.ToolSpecification;
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

public class ChatService<T extends TemplateContext> {
    private LlmConfig config;
    private final ToolService toolService;
    private final SkillService skillService;
    private final ChatMemory memory = MessageWindowChatMemory.builder()
            .id(ChatService.class)
            .maxMessages(500000)
            .build();
    private ChatModel chatModel;
    private int tokenSize = 0;

    private List<ChatMessage> standingOrders = Collections.emptyList();
    private final T templateContext;

    public ChatService(LlmConfig config, ToolService toolService, SkillService skillService, T templateContext) {
        this.config = config;
        this.skillService = skillService;
        this.toolService = toolService;
        this.templateContext = templateContext;
        updateConfig(config);
    }

    public T getTemplateContext() {
        return templateContext;
    }
    
    public void setTemplatedStandingOrders(List<String> messages) {
        this.standingOrders.clear();
        if (messages == null || messages.isEmpty()) return;
        for (String m : messages) {
            this.standingOrders.add(SystemMessage.from(templateContext.process(m)));
        }
    }

    public void setStandingOrders(List<ChatMessage> additions) {
        if (additions == null) additions = Collections.emptyList();
        this.standingOrders = new ArrayList<ChatMessage>(additions);
    }

    public void updateConfig(LlmConfig config) {
        this.config = config;
        this.chatModel = config.build();
        try {
            if (config.skillDirectory() != null) {
                this.templateContext.setSkillDirectory(config.skillDirectory());
                this.skillService.refresh(Path.of(config.skillDirectory()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load skills from " + config.skillDirectory(), e);
        }
        templateContext.setTokenWindow(config.tokenWindow());
    }

    public ChatModel getChatModel() {
        return chatModel;
    }

    public ToolService getToolService() {
        return toolService;
    }

    public LlmConfig getConfig() {
        return config;
    }

    public int getTokenWindow() {
        return config.tokenWindow();
    }

    public int getTokenSize() {
        return tokenSize;
    }

    public ChatResponse call(AiAgent agent, String message, AiMonitor monitor) {
        monitor = AiMonitor.nullSafety(monitor);

        if (tokenSize >= config.tokenWindow() * 0.8) {
            compressContext(monitor);
        }

        if (message != null) {
            memory.add(UserMessage.from(message));
        }
        ChatResponse response = null;

        do {
            var toolSpecs = buildToolSpecs(agent);
            var messagesToSend = buildMessagesToSend();

            agent.withTools(toolSpecs);
            response = agent.call(messagesToSend, monitor);
            updateTokenCount(response);

            memory.set(toolService.runAllTools(response, chatModel, monitor, memory.messages()));

            if (monitor.isCanceled()) break;

        } while (response.aiMessage().hasToolExecutionRequests()
                || StringUtil.hasNoValue(response.aiMessage().text()));

        if (response != null) {
            updateTokenCount(response);
        }

        return response;
    }

    private List<ToolSpecification> buildToolSpecs(AiAgent agent) {
        return toolService.toolSpecifications(t -> {
            if (t.getTool() instanceof CompressorAgentTool) {
                return tokenSize > config.tokenWindow() * 0.7 && memory.messages().size() > 5;
            }
            return !agent.isReadOnly() || !t.getTool().isEditTool();
        });
    }

    private ArrayList<ChatMessage> buildMessagesToSend() {
        var messagesToSend = new ArrayList<ChatMessage>(standingOrders);
        if (skillService.hasSkills()) {
            messagesToSend.add(skillService.skillMessage(templateContext));
        }
        messagesToSend.addAll(memory.messages());
        return messagesToSend;
    }

    /**
     * Compresses the current conversation via CompressorAgent, clears memory,
     * and replaces it with the compressed summary.
     */
    public ChatResponse compressContext(AiMonitor monitor) {
        var response = new AiCompressorAgent(chatModel).call(memory.messages(), monitor);
        memory.clear();
        memory.add(SystemMessage.from(response.aiMessage().text()));
        updateTokenCount(response);
        return response;
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
        for (var msg : memory.messages()) {
            chars += charCount(msg);
        }
        return chars / 4;
    }

    private int charCount(ChatMessage msg) {
        if (msg instanceof UserMessage um) {
            return um.singleText().length();
        } else if (msg instanceof AiMessage am) {
            return am.text() != null ? am.text().length() : 0;
        } else if (msg instanceof ToolExecutionResultMessage tr) {
            return tr.text().length();
        }
        return 0;
    }

    public void clear() {
        memory.clear();
    }

    public List<ChatMessage> getMessages() {
        return memory.messages();
    }

    public void addMessage(ChatMessage message) {
        this.memory.add(message);
    }

    public List<SkillRecord> getSkills() {
        return this.skillService.getSkills();
    }
}
