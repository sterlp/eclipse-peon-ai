package org.sterl.llmpeon.shared;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.agent.model.TaggedResult;

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

    /** Tag prefixes — the compact stager classifies on these (R-TR-7), no instanceof for capping. */
    public static final String THINK_PREFIX = "THINK:" + System.lineSeparator();
    public static final String TOOL_REQUEST_PREFIX = "TOOL REQUEST:" + System.lineSeparator();
    public static final String TOOL_RESULT_PREFIX = "TOOL RESULT:" + System.lineSeparator();
    public static final String SYSTEM_PREFIX = "SYSTEM:" + System.lineSeparator();
    /** Body marker of a TOOL RESULT: block — the result text starts after the LAST occurrence. */
    public static final String TOOL_RESULT_BODY_MARKER = "result:" + System.lineSeparator();
    /** Body marker of a TOOL REQUEST: block — the arguments start after the (structural) occurrence. */
    public static final String TOOL_REQUEST_BODY_MARKER = "arguments:";
    /** Marker of a front-capped thinking block: the head was dropped, the end (conclusion) is kept. */
    public static final String THINK_FRONT_CAP_ANCHOR = "…$ ";

    /**
     * The CONTEXT SIZE of the given prompt/response in tokens: the provider's INPUT token count
     * when available, otherwise the chars/3 estimate of the messages. The total token count
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
     * {@code length / 3}. A coarse chars/3 (~3 chars per token) — deliberately
     * over-estimating a bit keeps the live rate honest; never a real provider count.
     */
    public static int estimateTokens(@Nullable String text) {
        if (text == null || text.isEmpty()) return 0;
        int len = text.length();
        return len <= 5 ? 1 : charsToTokens(len);
    }

    /** The central chars/3 estimator (~3 chars per token) — deliberately over-estimating keeps estimates honest. */
    private static int charsToTokens(int chars) {
        return chars / 3;
    }

    /**
     * Inverse of the central chars/3 estimator ({@link #charsToTokens(int)}) — one place for the
     * token math (R-CC-13, docs/compact.md). The multiplication is clamped against integer overflow.
     */
    public static int tokensToChars(int tokens) {
        return (int) Math.min(Integer.MAX_VALUE, (long) tokens * 3);
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
    
    public static String readChatMessage(List<ChatMessage> msg) {
        var result = new StringBuilder();
        for (ChatMessage chatMessage : msg) {
            result.append(toString(chatMessage)).append(System.lineSeparator());
        }
        return result.toString();
    }
    
    /**
     * The single message renderer (R-TR-8): the {@link #extractMessages(ChatMessage)} entries
     * joined with a line separator, UNCAPPED — capping is the stager's job. A message without
     * renderable content renders as the empty string.
     */
    public static String toString(ChatMessage msg) {
        var entries = extractMessages(msg);
        return entries.isEmpty() ? "" : String.join(System.lineSeparator(), entries) + System.lineSeparator();
    }

    /**
     * Converts a ChatMessage to a unique collection of strings, keeps {@link TaggedResult}
     * as is and adds tags for any other message. Entries carry NO trailing line separator.
     *
     * <p>CUSTOM messages are always ignored; every other type is rendered UNCAPPED (R-TR-8 —
     * always think, always tools). SYSTEM messages are rendered like every other type — the
     * compact stager filters them on block level (ADR-0030).</p>
     */
    @NonNull
    public static Collection<String> extractMessages(@NonNull ChatMessage msg) {
        var result = new LinkedList<String>();
        var nl = System.lineSeparator();
        var t = msg.type();
        if (msg.type() == ChatMessageType.CUSTOM) return result;
        if (msg instanceof SystemMessage sm) {
            result.add(SYSTEM_PREFIX + sm.text());
        } else {
            if (msg instanceof UserMessage um) {
                um.contents().stream().filter(m -> m instanceof TextContent)
                    .map(m -> (TextContent)m)
                    .forEach(c -> result.add(tagged(c.text(), t)));
                
            } else if (msg instanceof AiMessage m) {
                if (StringUtil.hasValue(m.text())) {
                    result.add(tagged(m.text(), t));
                }
                
                if (StringUtil.hasValue(m.thinking())) {
                    result.add(THINK_PREFIX + m.thinking());
                }
                
                if (m.hasToolExecutionRequests()) {
                    for (var tr : m.toolExecutionRequests()) {
                        result.add(TOOL_REQUEST_PREFIX
                             + "tool name: " + tr.name() + nl
                             + "arguments:" + tr.arguments());
                    }
                }
                
            } else if (msg instanceof ToolExecutionResultMessage tr) {
                result.add(TOOL_RESULT_PREFIX
                        + "tool name: " + tr.toolName() + nl
                        + "result:" + nl + tr.text());
            }
        }

        return result;
    }
    
    /**
     * @returns build the correct info tag for a {@link ChatMessage} content taking {@link TaggedResult} in 
     */
    private static String messageTag(String msg, ChatMessageType type) {
        if (StringUtil.hasNoValue(msg)) return "";
        if (TaggedResult.isTagged(msg)) return "";
        return type + ":" + System.lineSeparator();
    }
    
    private static String tagged(String msg, ChatMessageType type) {
        return messageTag(msg, type) + msg;
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
