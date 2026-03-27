package org.sterl.llmpeon.agent;

import java.util.List;

import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

public class AiCompressorAgent {

    private static final SystemMessage COMPRESS_SYSTEM = SystemMessage.systemMessage("""
            Compress the conversation into a structured briefing. Output exactly:

            WHAT: <Feature Name>
            - Goal and requirements
            - Decisions made and their rationale
            
            HOW: <Design or Plan>
            - Solution design
            - Work done so far; files modified and why
            - Component responsibilities
            - Last implementation plan (if not yet executed)
            
            Preserve: key decisions + rationale, pending work, file paths, code references, relevant tool results.
            Remove: duplicates, verbose tool output, superseded decisions, filler.
            
            Be short but complete enough to continue work without re-reading the conversation.
            Preserve paths to key files - to avoid searches.
            """);
    
    private final ChatModel chatModel;

    public AiCompressorAgent(ChatModel chatModel) {
        super();
        this.chatModel = chatModel;
    }
    
    public ChatResponse call(List<ChatMessage> messages, AiMonitor monitor) {
        var msg = new StringBuilder();
        messages.stream().forEach(m -> msg.append(toText(m)));

        if (monitor != null) monitor.onTool("Compressing conversation " + messages.size() + " messages");
        return chatModel.chat(ChatRequest.builder()
                .temperature(0.1)
                .messages(COMPRESS_SYSTEM, UserMessage.from(msg.toString()))
                .build());
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
