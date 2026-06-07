package org.sterl.llmpeon.shared;

import java.util.LinkedList;
import java.util.List;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.response.ChatResponse;

public class ChatMessageUtil {
    
    public static int getTokenCount(ChatResponse response, List<ChatMessage> messages) {
        var tokenUsage = response.tokenUsage();
        if (tokenUsage == null) tokenUsage = response.metadata() != null ? response.metadata().tokenUsage() : null;

        if (tokenUsage != null && tokenUsage.totalTokenCount() != null) {
            return tokenUsage.totalTokenCount();
        } else {
            return estimateTokens(messages);
        }
    }
    private static int estimateTokens(List<ChatMessage> messages) {
        int chars = 0;
        for (var msg : messages) chars += charCount(msg);
        return chars / 4;
    }
    private static int charCount(ChatMessage msg) {
        return toString(msg).length();
    }
    
    /**
     * 1. System-Messages nur am Anfang erlaubt
     * 2. Tool-Messages NUR nach Assistant-Messages MIT tool_calls erlaubt
     * 3. Rollen müssen alternieren: user/assistant/user/assistant
     * 4. Nach User/System darf KEIN Tool kommen!
     */
    public static void addMessageToMemory(ChatMemory memory, ChatMessage message) {
        synchronized (memory) {
            if (message instanceof UserMessage num 
                    && (!memory.messages().isEmpty() && memory.messages().getLast() instanceof UserMessage lum)) {
                var messages = memory.messages();
                memory.set(UserMessage.replaceLast(messages, ChatMessageUtil.join(lum, num)));
            } else {
                memory.add(message); 
            }
        }
    }

    public static UserMessage join(UserMessage m1, UserMessage m2) {
        List<Content> data = new LinkedList<>();
        data.addAll(toContent(m1));
        data.addAll(toContent(m2));
        return UserMessage.from(data);
    }

    private static List<Content> toContent(UserMessage m1) {
        List<Content> data = new LinkedList<Content>();
        if (m1.hasSingleText()) data.add(new TextContent(m1.singleText()));
        else data.addAll(m1.contents());
        return data;
    }
    
    public static String toString(ChatMessage msg) {
        var result = new StringBuilder();
        if (msg instanceof UserMessage m) {
            if (m.hasSingleText()) {
                result.append(m.singleText());
            } else {
                m.contents().stream()
                    .filter(s -> s instanceof TextContent)
                    .map(s -> ((TextContent)s))
                    .forEach(s -> result.append(s.text()).append("\n"));
            }
        } else if (msg instanceof AiMessage m) {
            if (StringUtil.hasValue(m.text())) {
                result.append(m.text()).append("\n");
            }
            if (StringUtil.hasValue(m.thinking())) {
                result.append("AI thinking:\n").append(m.thinking()).append("\n");
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
        return result.toString().strip();
    }
}
