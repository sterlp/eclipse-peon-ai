package org.sterl.llmpeon.shared;

import java.util.LinkedList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

public class ChatMessageUtil {

    /** Marker of a front-capped thinking block: the head was dropped, the end (conclusion) is kept. */
    public static final String THINK_FRONT_CAP_ANCHOR = "…$ ";

    /**
     * Render modes for {@link #toString(ChatMessage, RenderOptions)}.
     * Existing formats stay exactly preserved via {@link #defaults()}; {@link #uncapped()} is the stager's baseline render.
     *
     * @param includeThink          render the thinking block of an AI message at all
     * @param toolMessageSize       head cap (chars) for tool arguments/results; 0 = not rendered at all
     * @param thinkCapChars         cap (chars) for the thinking block; {@link Integer#MAX_VALUE} = no cap
     * @param thinkKeepTail         true = front cap keeping the END of the thinking plus {@link #THINK_FRONT_CAP_ANCHOR};
     *                              false = no capping at all (legacy behaviour)
     * @param perMessageTrimmedTag  append "(trimmed)" to every capped tool message; false = one disclosure at the
     *                              input end instead (compact mode, R-CIB-5)
     * @param renderSystemMessage   render SYSTEM messages like every other type; false = dropped (the compact
     *                              input filters them on list level instead, R-CIB-4)
     */
    public record RenderOptions(boolean includeThink, int toolMessageSize, int thinkCapChars,
                                boolean thinkKeepTail, boolean perMessageTrimmedTag, boolean renderSystemMessage) {

        /** Legacy defaults — the existing overloads delegate here, formats unchanged. */
        public static RenderOptions defaults() {
            return new RenderOptions(true, 6000, Integer.MAX_VALUE, false, true, true);
        }

        /** No caps at all — the stager's baseline render (dedup before caps, R-CIB-2). */
        public static RenderOptions uncapped() {
            return new RenderOptions(true, Integer.MAX_VALUE, Integer.MAX_VALUE, false, false, false);
        }
    }

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
        return charsToTokens(chars);
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
        return len <= 5 ? 1 : charsToTokens(len);
    }

    /** The central chars×2/7 estimator (~3.5 chars per token) — deliberately over-estimating keeps estimates honest. */
    private static int charsToTokens(int chars) {
        return (chars * 2) / 7;
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
        return toString(msg, RenderOptions.defaults());
    }
    
    public static String toString(ChatMessage msg, int maxSize) {
        return toString(msg, new RenderOptions(true, maxSize, Integer.MAX_VALUE, false, true, true));
    }
    
    /**
     * Converts ChatMessages to a simple string.
     * Legacy overload — SYSTEM messages are rendered (ADR-0030 landmine fix, docs/compact.md R-CIB-4).
     */
    public static String toString(ChatMessage msg, boolean includeThink, int toolMessageSize) {
        return toString(msg, new RenderOptions(includeThink, toolMessageSize, Integer.MAX_VALUE, false, true, true));
    }

    /**
     * Converts a ChatMessage to a simple string in the given render mode.
     * CUSTOM messages are always ignored; SYSTEM messages are rendered like every other type
     * unless {@link RenderOptions#renderSystemMessage()} is false.
     */
    public static String toString(ChatMessage msg, RenderOptions options) {
        var nl = System.lineSeparator();
        if (msg.type() == ChatMessageType.CUSTOM) return "";
        if (msg.type() == ChatMessageType.SYSTEM) {
            if (!options.renderSystemMessage()) return "";
            return "SYSTEM:" + nl + ((SystemMessage) msg).text() + nl;
        }

        var result = new StringBuilder();
        result.append(msg.type()).append(":").append(nl);
        if (msg instanceof UserMessage um) {
            um.contents().stream().filter(m -> m instanceof TextContent)
              .map(m -> (TextContent)m)
              .forEach(c -> result.append(c.text()).append(nl));

        } else if (msg instanceof AiMessage m) {
            if (StringUtil.hasValue(m.text())) {
                result.append(m.text()).append(nl);
            }

            if (options.includeThink() && StringUtil.hasValue(m.thinking())) {
                result.append("Think: ").append(capThinking(m.thinking(), options)).append(nl);
            }

            if (options.toolMessageSize() > 0 && m.hasToolExecutionRequests()) {
                for (var tr : m.toolExecutionRequests()) {
                    result.append("tool name: ").append(tr.name()).append(nl)
                          .append("arguments:").append(nl)
                          .append(StringUtil.trimToLength(tr.arguments(), options.toolMessageSize()))
                          .append(nl)
                          .append(trimmedTag(tr.arguments(), options));
                }
            }

        } else if (options.toolMessageSize() > 0 && msg instanceof ToolExecutionResultMessage tr) {
            result.append("tool name: ").append(tr.toolName()).append(nl)
                  .append("result:").append(nl)
                  .append(StringUtil.trimToLength(tr.text(), options.toolMessageSize()))
                  .append(nl)
                  .append(trimmedTag(tr.text(), options));
        }
        return result.toString();
    }

    /** Front cap: keep the END of the thinking (the conclusion), drop the head, mark with the anchor. */
    private static String capThinking(String thinking, RenderOptions options) {
        if (!options.thinkKeepTail() || thinking.length() <= options.thinkCapChars()) return thinking;
        return THINK_FRONT_CAP_ANCHOR + thinking.substring(thinking.length() - options.thinkCapChars());
    }

    private static String trimmedTag(String value, RenderOptions options) {
        return options.perMessageTrimmedTag() && value != null && value.length() > options.toolMessageSize()
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
