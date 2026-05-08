package org.sterl.llmpeon.agent;

import java.util.List;

import org.sterl.llmpeon.PromptLoader;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.streaming.StreamingBridge;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

public class AiCompressorAgent {

    private static final SystemMessage COMPRESS_SYSTEM = SystemMessage.systemMessage(PromptLoader.load("compressor.txt"));

    private final StreamingChatModel chatModel;

    public AiCompressorAgent(StreamingChatModel chatModel) {
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
        messages.stream().forEach(m -> msg.append(toText(m)));

        if (monitor != null) monitor.onTool("Compressing conversation " + messages.size() + " messages");
        var request = ChatRequest.builder()
                .temperature(0.1)
                .messages(COMPRESS_SYSTEM, UserMessage.from(msg.toString()))
                .build();
        return new StreamingBridge().call(chatModel, request, monitor);
    }

    String toText(ChatMessage msg) {
        var result = new StringBuilder();
        if (msg instanceof UserMessage m && m.hasSingleText()) {
            result.append(m.type()).append(":\n").append(m.singleText());
        } else if (msg instanceof AiMessage m) {
            if (StringUtil.hasValue(m.text())) {
                result.append(m.type()).append(":\n").append(m.text());
            }
            if (m.hasToolExecutionRequests()) {
                for (var tr : m.toolExecutionRequests()) {
                    result.append("\n").append(m.type())
                          .append(" tool call").append(tr.name()).append(":")
                          .append(tr.arguments());
                }
            }
        }
        return result.toString();
    }
}
