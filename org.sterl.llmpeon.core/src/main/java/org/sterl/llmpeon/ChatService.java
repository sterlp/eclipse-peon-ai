package org.sterl.llmpeon;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.sterl.llmpeon.agent.AgentService;
import org.sterl.llmpeon.agent.AiMonitor;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.skill.SkillRecord;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

public class ChatService {
    private LlmConfig config;
    private final ToolService toolService;
    private final SkillService skillService;
    private final ChatMemory memory = MessageWindowChatMemory.builder()
            .id(ChatService.class)
            .maxMessages(500000)
            .build();
    private final AgentService agentService;
    private int tokenSize = 0;

    private List<SystemMessage> standingOrders = Collections.emptyList();

    public ChatService(LlmConfig config, ToolService toolService, SkillService skillService) {
        this.config = config;
        this.skillService = skillService;
        this.toolService = toolService;
        this.agentService = new AgentService(null);
        updateConfig(config);
    }

    /**
     * Sets a list of orders - which will not be added to the chat memory but are
     * added an
     */
    public void setStandingOrders(List<SystemMessage> additions) {
        if (additions == null) additions = Collections.emptyList();
        this.standingOrders = additions.stream().filter(s -> StringUtil.hasValue(s.text())).toList();
    }

    public void updateConfig(LlmConfig config) {
        this.config = config;
        agentService.updateModel(config.build());
        try {
            this.skillService.refresh(Path.of(config.skillDirectory()));
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

    public ChatResponse call(String message, AiMonitor monitor) {
        monitor = AiMonitor.nullSafty(monitor);
        // auto-compress at 95%
        if (tokenSize >= config.tokenWindow() * 0.95) {
            compressContext(monitor);
        }

        if (message != null) memory.add(UserMessage.from(message));
        ChatResponse response = null;

        var developerAgent = agentService.newDeveloperAgent();
        do {
            // orders
            var messagesToSend = new ArrayList<ChatMessage>(standingOrders);
            // any skills
            if (skillService.hasSkills()) {
                messagesToSend.addFirst(skillService.skillMessage());
            }
            // history
            messagesToSend.addAll(memory.messages());

            developerAgent.withTools(toolService.toolSpecifications());
            response = developerAgent.call(messagesToSend, monitor);
            updateTokenCount(response);

            // add the AI message if it has any text
            memory.add(response.aiMessage());

            runAllTools(monitor, response);

            if (monitor.isCanceled()) break;
        } while (response.aiMessage().hasToolExecutionRequests()
                || StringUtil.hasNoValue(response.aiMessage().text()));

        if (response != null) {
            System.out.println(response.metadata());
            System.out.println(response.aiMessage().text());
        }

        return response;
    }

    private void runAllTools(AiMonitor monitor, ChatResponse response) {
        if (response.aiMessage().hasToolExecutionRequests()) {
            for (var tr : response.aiMessage().toolExecutionRequests()) {
                String result = toolService.execute(tr, monitor);
                memory.add(ToolExecutionResultMessage.from(tr.id(), tr.name(), result));

                if (monitor.isCanceled()) return;
            }
        }
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
        replaceMemory(response.aiMessage());
        updateTokenCount(response);
        return response;
    }

    /** Clears the current memory and replaces it with a single message. */
    public void replaceMemory(AiMessage msg) {
        memory.clear();
        memory.add(msg);
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

    public List<ChatMessage> getMessages() {
        return memory.messages();
    }

    public List<SkillRecord> getSkills() {
        return this.skillService.getSkills();
    }
}
