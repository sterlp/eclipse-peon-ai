package org.sterl.llmpeon.agent;

import java.util.List;

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

    /**
     * Not every model supports a different temperature -- leave empty to use model defaults.
     */
    public AiCompressorAgent(ConfiguredChatModel chatModel) {
        super();
        this.chatModel = chatModel;
    }

    /**
     * Compresses the given conversation messages into a structured briefing.
     * {@code SystemMessage} entries are intentionally ignored — they represent the
     * static system prompt and standing orders, which are injected fresh on every call
     * and must not be stored in or read from memory.
     */
    public ChatResponse call(List<ChatMessage> messages, AiMonitor monitor) {
        var msg = new StringBuilder();
        messages.stream().forEach(m -> msg.append(toText(m)).append("\n\n"));

        var cfg = chatModel.getConfig();
        var modelName = cfg.getCompactModel();
        if (monitor != null) monitor.onTool("Compressing conversation " + messages.size()
            + " messages" + (modelName == null ? "" : " using " + modelName));

        // Model, temperature and think come from the compact ModelConfig (no tools for compaction).
        var request = ChatRequest.builder()
                .messages(COMPRESS_SYSTEM, UserMessage.from(msg.toString()))
                .parameters(cfg.compactAgentConfig().newRequestParameters(null));

        monitor.onChatMessage(1, request);
        var result = chatModel.callBlocking(request.build(), monitor);
        ToSimpleMessage.INSTANCE.convert(result.aiMessage()).forEach(monitor::onChatResponse);
        
        return result;
    }

    String toText(ChatMessage msg) {
        var result = new StringBuilder();
        result.append("\n").append(msg.type()).append(":\n");
        result.append(ChatMessageUtil.toString(msg, false, false));
        return result.toString();
    }
}
