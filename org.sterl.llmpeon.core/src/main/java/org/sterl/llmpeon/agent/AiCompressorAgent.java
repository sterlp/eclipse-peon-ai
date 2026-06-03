package org.sterl.llmpeon.agent;

import java.util.List;

import org.sterl.llmpeon.PromptLoader;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.streaming.StreamingBridge;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

public class AiCompressorAgent {

    private static final SystemMessage COMPRESS_SYSTEM = SystemMessage.systemMessage(PromptLoader.load("compressor.txt"));

    private final StreamingChatModel chatModel;
    
    private final Double temperature;

    /**
     * Not every model supports a different temperature -- leave empty to use model defaults.
     */
    public AiCompressorAgent(StreamingChatModel chatModel, Double temperature) {
        super();
        this.chatModel = chatModel;
        this.temperature = temperature;
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

        if (monitor != null) monitor.onTool("Compressing conversation " + messages.size() + " messages");
        var request = ChatRequest.builder()
                .messages(COMPRESS_SYSTEM, UserMessage.from(msg.toString()));

        if (temperature != null) request.temperature(temperature);

        return new StreamingBridge().call(chatModel, request.build(), monitor);
    }

    String toText(ChatMessage msg) {
        var result = new StringBuilder();
        result.append("\n").append(msg.type()).append(":\n");
        if (msg instanceof UserMessage m && m.hasSingleText()) {
            result.append(m.singleText());
        } else if (msg instanceof AiMessage m) {
            if (StringUtil.hasValue(m.text())) {
                result.append(m.text()).append("\n");
            }
            if (StringUtil.hasValue(m.thinking())) {
                result.append("Think:\n").append(m.thinking()).append("\n");
            }
            if (m.hasToolExecutionRequests()) {
                for (var tr : m.toolExecutionRequests()) {
                    result.append("\ntool name:  ").append(tr.name())
                          .append("\ntool id:    ").append(tr.id())
                          .append("\narguments:  ").append(tr.arguments());
                }
            }
        } else if (msg instanceof ToolExecutionResultMessage tr && tr.hasSingleText()) {
            result.append("\ntool result for id: ").append(tr.id())
                  .append("\n").append(tr.text()).append("\n");
        }
        return result.toString();
    }
}
