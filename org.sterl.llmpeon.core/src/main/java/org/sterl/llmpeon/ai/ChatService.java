package org.sterl.llmpeon.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.sterl.llmpeon.agent.AiCompressorAgent;
import org.sterl.llmpeon.agent.AiDeveloperAgent;
import org.sterl.llmpeon.agent.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

public class ChatService {
    private LlmConfig config;
    private final ToolService toolService;
    private final ChatMemory memory = MessageWindowChatMemory.withMaxMessages(500000);
    private ChatModel model;
    private int tokenSize = 0;
    
    private List<ChatMessage> standingOrders = Collections.emptyList();

    public ChatService(LlmConfig config, ToolService toolService) {
        this.config = config;
        this.toolService = toolService;
        updateConfig(config);
    }
    
    /**
     * Sets a list of orders - which will not be added to the chat memory but are
     * added an
     */
    public void setStandingOrders(List<ChatMessage> additions) {
        if (additions == null) additions = Collections.emptyList();
        this.standingOrders = new ArrayList<>(additions);
    }

    public void updateConfig(LlmConfig config) {
        this.config = config;
        this.model = config.build();
        this.toolService.updateSkillDirectory(config.getSkillDirectory());
    }

    public ToolService getToolService() {
        return toolService;
    }

    public LlmConfig getConfig() {
        return config;
    }

    public int getTokenWindow() {
        return config.getTokenWindow();
    }

    public int getTokenSize() {
        return tokenSize;
    }

    public ChatResponse call(String message, AiMonitor monitor) {
        
        // auto-compress at 95%
        if (tokenSize >= config.getTokenWindow() * 0.95) {
            compressContext(monitor);
        }
        
        if (message != null) memory.add(UserMessage.from(message));
        ChatResponse response;

        var developerAgent = new AiDeveloperAgent(model);
        do {
            var messages = new ArrayList<ChatMessage>(standingOrders);
            // any skills first
            var skills = toolService.skillMessage();
            if (skills != null) {
                messages.addFirst(skills);
            }
            // history
            messages.addAll(memory.messages());
            
            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(toolService.toolSpecifications())
                    .build();

            response = developerAgent.call(request, monitor);
            updateTokenCount(response);

            // add the AI message if it has any text
            if (StringUtil.hasValue(response.aiMessage().text())) memory.add(response.aiMessage());

            if (response.aiMessage().hasToolExecutionRequests()) {
                for (var tr : response.aiMessage().toolExecutionRequests()) {
                    String result = runTool(monitor, tr);
                    memory.add(ToolExecutionResultMessage.from(tr.id(), tr.name(), result));
                }
            }

        } while (response.aiMessage().hasToolExecutionRequests());

        System.err.println(response.metadata());
        System.err.println(response.aiMessage().text());

        return response;
    }

    private String runTool(AiMonitor monitor, ToolExecutionRequest tr) {
        String result;
        var executor = toolService.getExecutor(tr.name());
        if (executor != null) {
            try {
                result = executor.run(tr, monitor);
            } catch (IllegalArgumentException e) {
                result = e.getMessage();
                if (monitor != null) monitor.onProblem(tr.name() + ": " + e.getMessage());
            }
        } else {
            result = "Error: unknown tool '" + tr.name() + "' check spelling";
            if (monitor != null) monitor.onProblem(result);
        }
        return result;
    }
    
    public static String trimArgs(String value) {
        if (value == null) return "";
        value = value.strip();
        if (value.length() == 2) return "";
        else if (value.length() <= 150) return value.substring(1, value.length() - 1);
        return value.substring(1, 149);
        
    }

    /**
     * Compresses the current conversation via CompressAgent, clears memory,
     * and adds only the compressed summary as a new starting point.
     * @return the compressed summary text, or empty string if nothing to compress
     */
    public void compressContext(AiMonitor monitor) {
        var messages = memory.messages();
        if (messages.size() < 2) return;

        var compressorAgent = new AiCompressorAgent(model);
        // send all messages with compress system prompt, no tools
        if (monitor != null) monitor.onAction("Compressing conversation");
        var response = compressorAgent.call(messages, monitor);

        memory.clear();
        memory.add(response.aiMessage());

        updateTokenCount(response);
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
}
