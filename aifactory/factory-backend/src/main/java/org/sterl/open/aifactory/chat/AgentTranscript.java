package org.sterl.open.aifactory.chat;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import org.sterl.open.aifactory.chat.dto.AgentTranscriptDto;
import org.sterl.open.aifactory.chat.dto.TranscriptLineDto;

/**
 * The reading of a member's own memory for the insight (R-WP-26, UC-WP-49): what the core holds becomes a
 * list of lines, and nothing else is added — no author table, no time stamp, no second record of the
 * conversation. The system prompt is not a line, because the user never spoke it.
 *
 * <p>The two caps of D-10 are here and nowhere else. A member's memory carries tool results in the 100 kB
 * range, so an unbounded transcript would be a second way to block a browser; the file area sets the
 * precedent with its own entry and byte limits. Keeping the <b>last</b> lines is the honest choice for a
 * question like "why did Dev build this?" — the beginning of a long run is its least interesting part.</p>
 */
final class AgentTranscript {

    /** Lines of the memory this edge hands out — the newest ones. */
    static final int MAX_TRANSCRIPT_LINES = 500;

    /** Characters of one line; the rest is cut and marked, because a line is not a place for 100 kB. */
    static final int MAX_LINE_CHARS = 8192;

    private static final String CUT_MARKER = "…";
    private static final String KIND_USER = "user";
    private static final String KIND_AGENT = "agent";
    private static final String KIND_TOOL = "tool";

    private AgentTranscript() {
    }

    static AgentTranscriptDto of(String agentName, List<ChatMessage> memory) {
        List<TranscriptLineDto> lines = new ArrayList<>();
        for (ChatMessage message : memory) {
            lines.addAll(linesOf(message));
        }
        boolean truncated = lines.size() > MAX_TRANSCRIPT_LINES;
        List<TranscriptLineDto> kept = truncated
                ? lines.subList(lines.size() - MAX_TRANSCRIPT_LINES, lines.size())
                : lines;
        return new AgentTranscriptDto(agentName, truncated, List.copyOf(kept));
    }

    /**
     * The lines one message is worth. A tool call and its result are each a line of their own — a caller
     * reading the insight has to see both the wish and what came back, which is exactly what Jon's own
     * chat hides (UC-WP-40 keeps the member's work out of it).
     */
    private static List<TranscriptLineDto> linesOf(ChatMessage message) {
        if (message instanceof UserMessage user) {
            String text = textOf(user);
            return text.isBlank() ? List.of() : List.of(line(KIND_USER, text));
        }
        if (message instanceof AiMessage answer) {
            List<TranscriptLineDto> lines = new ArrayList<>();
            if (answer.text() != null && !answer.text().isBlank()) {
                lines.add(line(KIND_AGENT, answer.text()));
            }
            if (answer.hasToolExecutionRequests()) {
                answer.toolExecutionRequests().forEach(request -> lines
                        .add(line(KIND_TOOL, request.name() + "(" + request.arguments() + ")")));
            }
            return lines;
        }
        if (message instanceof ToolExecutionResultMessage result) {
            return List.of(line(KIND_TOOL, result.toolName() + ": " + resultTextOf(result)));
        }
        // a system prompt and any message type newer than this code are no line the user spoke or read
        return List.of();
    }

    private static TranscriptLineDto line(String kind, String text) {
        return new TranscriptLineDto(kind, cut(text));
    }

    /** Cut to {@link #MAX_LINE_CHARS} with a marker — the reader learns about the cut, never guesses it. */
    private static String cut(String text) {
        return text.length() <= MAX_LINE_CHARS ? text : text.substring(0, MAX_LINE_CHARS) + CUT_MARKER;
    }

    /** Only the text parts, joined as the conversation history joins them; an image part is no line. */
    private static String textOf(UserMessage user) {
        StringBuilder text = new StringBuilder();
        for (Content content : user.contents()) {
            if (content instanceof TextContent txt && !txt.text().isBlank()) {
                if (!text.isEmpty()) {
                    text.append(System.lineSeparator());
                }
                text.append(txt.text());
            }
        }
        return text.toString();
    }

    /** {@code text()} throws on a result that is not one text — this edge never fails on a tool's payload. */
    private static String resultTextOf(ToolExecutionResultMessage result) {
        return result.hasSingleText() ? result.text() : "[no text]";
    }
}
