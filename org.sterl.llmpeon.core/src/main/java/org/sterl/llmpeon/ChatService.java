package org.sterl.llmpeon;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.sterl.llmpeon.agent.AgentService;
import org.sterl.llmpeon.agent.AiMonitor;
import org.sterl.llmpeon.agent.PeonMode;
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
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
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
    private final AgentService agentService;
    private int tokenSize = 0;

    private List<ChatMessage> standingOrders = Collections.emptyList();
    private final T templateContext;

    private PeonMode mode = PeonMode.DEV;

    public ChatService(LlmConfig config, ToolService toolService, SkillService skillService, T templateContext) {
        this.config = config;
        this.skillService = skillService;
        this.toolService = toolService;
        this.templateContext = templateContext;
        this.agentService = new AgentService(null);
        updateConfig(config);
    }

    public T getTemplateContext() {
        return templateContext;
    }

    /**
     * Sets a list of orders - which will not be added to the chat memory but are
     * added an
     */
    public void setStandingOrders(List<ChatMessage> additions) {
        if (additions == null) additions = Collections.emptyList();
        this.standingOrders = new ArrayList<ChatMessage>(additions);
    }

    public void updateConfig(LlmConfig config) {
        this.config = config;
        agentService.updateModel(config.build());
        try {
            if (config.skillDirectory() != null) {
                this.skillService.refresh(Path.of(config.skillDirectory()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load skills from " + config.skillDirectory(), e);
        }
    }

    public AgentService getAgentService() {
        return agentService;
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

    public PeonMode getMode() {
        return mode;
    }

    /**
     * Switches the agent mode. When switching from PLAN to DEV the plan is handed
     * over automatically: the last AI message becomes the starting context of a
     * fresh dev session.
     */
    public void setMode(PeonMode newMode) {
        if (newMode == this.mode) return;
        if (this.mode == PeonMode.PLAN && newMode == PeonMode.DEV) {
            handoverPlanToDev();
        }
        this.mode = newMode;
    }

    /**
     * Extracts the last AI message (the completed plan), clears memory, and adds
     * the plan as the first user message for the dev agent to implement.
     */
    public void handoverPlanToDev() {
        var messages = memory.messages();
        if (messages.size() < 5) return; // no need for compression

        AiMessage plan = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AiMessage ai && StringUtil.hasValue(ai.text())) {
                plan = ai;
                break;
            }
        }

        if (plan != null) {
            memory.clear();
            memory.add(plan);
        }
    }

    public ChatResponse call(String message, AiMonitor monitor) {
        monitor = AiMonitor.nullSafty(monitor);
        
        if (tokenSize >= config.tokenWindow() * 0.8) {
            compressContext(monitor);
        }

        if (message != null) {
            memory.add(UserMessage.from(message));
        }
        ChatResponse response = null;

        final boolean isPlan = mode == PeonMode.PLAN;
        final var agent = isPlan ? agentService.newPlannerAgent(templateContext) : agentService.newDeveloperAgent(templateContext);

        do {
            var toolSpecs = buildToolSpecs(isPlan);
            var messagesToSend = buildMessagesToSend();

            agent.withTools(toolSpecs);
            response = agent.call(messagesToSend, monitor);
            updateTokenCount(response);

            memory.set(toolService.runAllTools(response, agentService, monitor, memory.messages()));

            if (monitor.isCanceled()) break;

        } while (response.aiMessage().hasToolExecutionRequests()
                || StringUtil.hasNoValue(response.aiMessage().text()));

        if (response != null) {
            System.out.println(response.metadata());
            System.out.println(response.aiMessage().text());
            
            updateTokenCount(response);
        }

        return response;
    }

    private List<ToolSpecification> buildToolSpecs(final boolean isPlan) {
        var toolSpecs = toolService.toolSpecifications(t -> {
            // some local LLMs keep compressing - so we don't always add it
            if (t.getTool() instanceof CompressorAgentTool) {
                return tokenSize > config.tokenWindow() * 0.7 && memory.messages().size() > 5;
            }
            return !isPlan || !t.getTool().isEditTool();
        });
        return toolSpecs;
    }

    private ArrayList<ChatMessage> buildMessagesToSend() {
        var messagesToSend = new ArrayList<ChatMessage>(standingOrders);
        if (skillService.hasSkills()) {
            messagesToSend.add(skillService.skillMessage(templateContext));
        }
        messagesToSend.addAll(memory.messages());
        return messagesToSend;
    }

    public static String trimArgs(String value) {
        if (value == null) return "";
        value = value.strip();
        if (value.length() == 2) return "";
        else if (value.length() <= 150) return value.substring(1, value.length() - 1);
        return value.substring(1, 149);
    }

    /**
     * Compresses the current conversation via CompressorAgent, clears memory,
     * and replaces it with the compressed summary.
     *
     * @return the compressed summary response
     */
    public ChatResponse compressContext(AiMonitor monitor) {
        var compressorAgent = agentService.newCompressorAgent();
        var response = compressorAgent.call(memory.messages(), monitor);
        memory.clear();
        memory.add(response.aiMessage());
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
    }

    /** Simple token estimation: ~4 characters per token */
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

    public List<SkillRecord> getSkills() {
        return this.skillService.getSkills();
    }
}
