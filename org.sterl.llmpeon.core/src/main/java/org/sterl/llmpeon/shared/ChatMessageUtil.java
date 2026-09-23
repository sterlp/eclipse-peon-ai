package org.sterl.llmpeon.shared;

import java.util.LinkedList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

public class ChatMessageUtil {
    
    /**
     * The CONTEXT SIZE of the given prompt/response in tokens: the provider's INPUT token count
     * when available, otherwise the chars×2/7 estimate of the messages. The total token count
     * (input + output = COST) never flows into this value (R-CC-1, docs/adr/0055-context-counter-input-not-cost.md).
     */
    public static int getTokenCount(ChatResponse response, List<ChatMessage> messages) {
        var tokenUsage = tokenUsage(response);
        if (tokenUsage != null && tokenUsage.inputTokenCount() != null) {
            return tokenUsage.inputTokenCount();
        } else {
            return estimateTokens(messages);
        }
    }

    /**
     * The real provider {@link TokenUsage} of a response, checking the response and its metadata,
     * or {@code null} when the provider returned none. Never estimates.
     */
    public static TokenUsage tokenUsage(ChatResponse response) {
        if (response == null) return null;
        var tokenUsage = response.tokenUsage();
        if (tokenUsage == null) tokenUsage = response.metadata() != null ? response.metadata().tokenUsage() : null;
        return tokenUsage;
    }
    public static int estimateTokens(List<ChatMessage> messages) {
        int chars = 0;
        for (var msg : messages) chars += charCount(msg);
        return (chars * 2) / 7;
    }

    /**
     * Estimates the token count of a single text snippet (a streaming text delta or a
     * tool-argument slice): {@code null} or empty → 0, up to 5 chars → 1, otherwise
     * {@code length × 2 / 7}. A coarse chars×2/7 (~3.5 chars per token) — deliberately
     * over-estimating a bit keeps the live rate honest; never a real provider count.
     */
    public static int estimateTokens(@Nullable String text) {
        if (text == null || text.isEmpty()) return 0;
        int len = text.length();
        return len <= 5 ? 1 : (len * 2) / 7;
    }

    private static int charCount(ChatMessage msg) {
        return toString(msg, Integer.MAX_VALUE).length();
    }

    public static UserMessage join(UserMessage m1, UserMessage m2) {
        List<Content> data = new LinkedList<>();
        data.addAll(toContent(m1));
        data.addAll(toContent(m2));
        return UserMessage.from(data);
    }

    private static List<Content> toContent(UserMessage message) {
        List<Content> data = new LinkedList<Content>();
        if (message.hasSingleText()) data.add(TextContent.from(message.singleText()));
        else data.addAll(message.contents());
        return data;
    }
    
    public static String readChatMessage(List<ChatMessage> msg) {
        var result = new StringBuilder();
        for (ChatMessage chatMessage : msg) {
            result.append(toString(chatMessage)).append(System.lineSeparator());
        }
        return result.toString();
    }
    
    public static String toString(ChatMessage msg) {
        return toString(msg, true, 6000);
    }
    
    public static String toString(ChatMessage msg, int maxSize) {
        return toString(msg, true, maxSize);
    }
    
    /**
     * Converts ChatMessages to a simple string.
     * SYSTEM messages are ignored!!
     */
    public static String toString(ChatMessage msg, boolean includeThink, int toolMessageSize) {
        if (msg.type() == ChatMessageType.SYSTEM) return "";
        if (msg.type() == ChatMessageType.CUSTOM) return "";

        var result = new StringBuilder();
        var nl = System.lineSeparator();
        result.append(msg.type()).append(":").append(nl);
        if (msg instanceof UserMessage um) {
            um.contents().stream().filter(m -> m instanceof TextContent)
              .map(m -> (TextContent)m)
              .forEach(c -> result.append(c.text()).append(nl));

        } else if (msg instanceof AiMessage m) {
            if (StringUtil.hasValue(m.text())) {
                result.append(m.text()).append(nl);
            }

            if (includeThink && StringUtil.hasValue(m.thinking())) {
                result.append("Think: ").append(m.thinking()).append(nl);
            }

            if (toolMessageSize > 0 && m.hasToolExecutionRequests()) {
                for (var tr : m.toolExecutionRequests()) {
                    result.append("tool name: ").append(tr.name()).append(nl)
                          .append("arguments:").append(nl)
                          .append(StringUtil.trimToLength(tr.arguments(), toolMessageSize))
                          .append(nl)
                          .append(trimmedTag(tr.arguments(), toolMessageSize));
                }
            }

        } else if (toolMessageSize > 0 && msg instanceof ToolExecutionResultMessage tr) {
            result.append("tool name: ").append(tr.toolName()).append(nl)
                  .append("result:").append(nl)
                  .append(StringUtil.trimToLength(tr.text(), toolMessageSize))
                  .append(nl)
                  .append(trimmedTag(tr.text(), toolMessageSize));
        }
        return result.toString();
    }
    
    private static String trimmedTag(String value, int toolMessageSize) {
        return value != null && value.length() > toolMessageSize 
                ? "(trimmed)" + System.lineSeparator() 
                : "";
    }
    
    public static String toString(List<Content> contents) {
        var result = new StringBuilder();
        if (contents == null || contents.isEmpty()) return "";
        contents.stream()
            .filter(s -> s instanceof TextContent)
            .map(s -> ((TextContent)s))
            .forEach(s -> result.append(s.text()).append(System.lineSeparator()));
        return result.toString();
    }
}
