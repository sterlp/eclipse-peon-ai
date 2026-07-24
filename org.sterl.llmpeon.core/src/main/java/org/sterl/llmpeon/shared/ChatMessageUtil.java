package org.sterl.llmpeon.shared;

import java.util.LinkedList;
import java.util.List;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

public class ChatMessageUtil {
    
    public static int getTokenCount(ChatResponse response, List<ChatMessage> messages) {
        var tokenUsage = tokenUsage(response);
        if (tokenUsage != null && tokenUsage.totalTokenCount() != null) {
            return tokenUsage.totalTokenCount();
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
    private static int estimateTokens(List<ChatMessage> messages) {
        int chars = 0;
        for (var msg : messages) chars += charCount(msg);
        return chars / 4;
    }
    private static int charCount(ChatMessage msg) {
        return toString(msg).length();
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
    
    public static String toString(ChatMessage msg) {
        return toString(msg, true, 4000);
    }
    
    public static String toString(ChatMessage msg, boolean includeThink, int toolMessageSize) {
        var result = new StringBuilder();
        result.append(msg.type()).append(": ");
        if (msg instanceof UserMessage um) {
            um.contents().stream().filter(m -> m instanceof TextContent)
                .map(m -> (TextContent)m)
                .forEach(c -> result.append(c.text()).append(System.lineSeparator()));
        } else if (msg instanceof AiMessage m) {

            if (StringUtil.hasValue(m.text())) {
                result.append(m.text()).append(System.lineSeparator());
            }
            if (includeThink && StringUtil.hasValue(m.thinking())) {
                result.append("AI think: ").append(m.thinking()).append(System.lineSeparator());
            }

            if (toolMessageSize > 0 && m.hasToolExecutionRequests()) {
                for (var tr : m.toolExecutionRequests()) {
                    result.append(StringUtil.trimToLength(tr.toString(), toolMessageSize))
                          .append(System.lineSeparator());
                }
            }

            result.append(System.lineSeparator());
        } else if (toolMessageSize > 0 && msg instanceof ToolExecutionResultMessage tr) {
            result.append(StringUtil.trimToLength(tr.toString(), toolMessageSize))
                  .append(System.lineSeparator());
        }
        return result.toString();
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
