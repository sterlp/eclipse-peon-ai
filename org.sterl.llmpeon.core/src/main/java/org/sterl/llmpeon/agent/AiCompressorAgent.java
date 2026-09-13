package org.sterl.llmpeon.agent;

import java.util.LinkedHashSet;
import java.util.List;

import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.model.ToSimpleMessage;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

public class AiCompressorAgent {

    private static final SystemMessage COMPRESS_SYSTEM = SystemMessage.systemMessage(PromptLoader.load("compressor.txt"));

    private final ConfiguredChatModel chatModel;

    public AiCompressorAgent(ConfiguredChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * Compresses the given conversation messages into a structured briefing.
     * {@code SystemMessage} entries are intentionally ignored — they represent the
     * static system prompt and standing orders, which are injected fresh on every call
     * and must not be stored in or read from memory.
     */
    public ChatResponse call(List<ChatMessage> messages, AiMonitor monitor) {
        monitor = AiMonitor.nullSafety(monitor);
        var stringMessages = new LinkedHashSet<String>();
        for (var m : messages) {
            var msg = toText(m);
            if (msg.length() > 0) stringMessages.add(msg);
        }

        var cfg = chatModel.getConfig();
        var modelName = cfg.modelConfigFor(AgentModelConfig.COMPACT).model();
        monitor.onTool("Compressing conversation " 
            + messages.size() + " messages "
            + ChatMessageUtil.estimateTokens(messages) + " tokens"
            + (modelName == null ? "" : " using " + modelName)
        );

        // Model, temperature and think come from the compact ModelConfig (no tools for compaction).
        var request = ChatRequest.builder()
                .messages(COMPRESS_SYSTEM, UserMessage.from(
                        String.join(System.lineSeparator(), stringMessages)))
                .parameters(cfg.compactAgentConfig().newRequestParameters(null));

        monitor.onChatMessage(1, request);
        var result = chatModel.callBlocking(request.build(), cfg.compactAgentConfig(), monitor);
        if (result == null) {
            throw new IllegalStateException("AI call returned null — streaming failed without a response");
        }
        ToSimpleMessage.INSTANCE.convert(result.aiMessage()).forEach(monitor::onChatResponse);

        return result;
    }

    String toText(ChatMessage msg) {
        var result = new StringBuilder();
        result.append(msg.type()).append(":").append(System.lineSeparator());
        result.append(ChatMessageUtil.toString(msg, false, 4000));
        return result.toString();
    }
}
