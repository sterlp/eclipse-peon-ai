package org.sterl.open.aifactory.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * The one mapping of the core's memory to the lines of Jon's conversation (R-WP-11, R-WP-47): running a
 * turn and reading a history are two reasons to change, so only one of them is the runtime's.
 *
 * <p>A plain class on purpose — no Spring, no model, nothing stored: the whole input is the memory the
 * core already keeps, which is what makes <em>no second store</em> (UC-WP-39, UC-WP-15) a fact of this
 * signature rather than a promise. Reached through {@link ProjectRuntime#history()} by the edge and by
 * every test.</p>
 */
final class ConversationRestore {

    private final AgentNames names;
    private final String jonName;

    ConversationRestore(AgentNames names, String jonName) {
        this.names = names;
        this.jonName = jonName;
    }

    /**
     * The chat as the memory has it: user text, Jon's answers, the final answer of a team member, the
     * failure line of a turn that did not run, and <b>one step per tool he used</b> (R-WP-47) — a system
     * prompt, the text of a message that only asks for a tool, and an empty message are no lines. A queued
     * input keeps the core's {@code [Queued Message]} marker, it is reality and not the factory's to hide
     * (Q6).
     *
     * <p>Every line states its author (R-WP-4, UC-WP-39): Jon by name, the member by name, the typed input
     * by the name his message carries (R-WP-25). So the whole conversation is read back out of the one
     * history file the core already keeps — no second store, and nothing read out of a text that is not
     * the factory's own: the single thing parsed here is the prefix this component wrote in front of a
     * send, by {@link UserName}, at the two positions that one writes it at (D-6-3). The failure line is
     * the one line that names no one — nothing in the team spoke it (R-WP-34).</p>
     *
     * <p>A step stands where its <b>call</b> stood — "in the order of the memory" means for a step the
     * place of the wish, not the place where the answer landed (UC-WP-82 G1); its result only fills in the
     * excerpt, matched by the call id, never by a remembered position.</p>
     */
    List<ChatLine> linesOf(List<ChatMessage> memory) {
        List<ChatLine> lines = new ArrayList<>();
        Map<String, Integer> stepOfCall = new HashMap<>();
        for (ChatMessage message : memory) {
            if (message instanceof UserMessage typed) {
                UserName.Parsed sender = UserName.parse(textOf(typed));
                if (!sender.text().isBlank()) {
                    lines.add(ChatLine.user(sender.text(), sender.name()));
                }
            } else if (message instanceof AiMessage answer && TurnFailure.isFailure(answer.text())) {
                // ahead of the Jon-branch: a failure is an AiMessage as well, and it is not his answer
                lines.add(ChatLine.error(TurnFailure.reason(answer.text())));
            } else if (message instanceof AiMessage answer && answer.hasToolExecutionRequests()) {
                // ahead of the Jon-branch as well: a message that only asks for a tool spoke no answer
                for (ToolExecutionRequest request : answer.toolExecutionRequests()) {
                    if (request.id() != null) {
                        stepOfCall.put(request.id(), lines.size());
                    }
                    lines.add(ChatLine.tool("", authorOrTool(request.name())));
                }
            } else if (message instanceof AiMessage answer
                    && answer.text() != null && !answer.text().isBlank()) {
                lines.add(ChatLine.jon(answer.text(), jonName));
            } else if (message instanceof ToolExecutionResultMessage result) {
                addResult(lines, stepOfCall, result);
            }
        }
        return List.copyOf(lines);
    }

    /**
     * A result is either <b>a member's answer</b> (UC-WP-40 G3) or <b>Jon's own tool coming back</b>
     * (R-WP-47), told apart by the tool name alone — the author table, and nothing else, knows which tool
     * is a member (R-WP-4).
     */
    private void addResult(List<ChatLine> lines, Map<String, Integer> stepOfCall,
            ToolExecutionResultMessage result) {
        ChatLine memberAnswer = finalTeamAnswerOf(result);
        if (memberAnswer != null) {
            lines.add(memberAnswer);
            return;
        }
        if (names.authorOf(result.toolName()) != null) {
            // a member's result that is not one text stays no line, as it always was — and its step keeps
            // its empty excerpt rather than a marker the chat never owned
            return;
        }
        // the core writes "[no text]" for a payload that is not one text into its own transcript; here the
        // step is an excerpt of a conversation, so an excerpt of nothing is empty and not a word
        String excerpt = result.hasSingleText() ? AgentTranscript.cut(result.text()) : "";
        Integer call = stepOfCall.remove(result.id());
        ChatLine step = ChatLine.tool(excerpt, result.toolName());
        if (call == null) {
            // a result whose call the memory does not carry is still a step that happened (UC-WP-82 G2)
            lines.add(step);
        } else {
            lines.set(call, step);
        }
    }

    /** Whose tool this is: the member a delegation speaks for, else the core's own tool name (R-WP-47). */
    private String authorOrTool(String toolName) {
        String author = names.authorOf(toolName);
        return author == null ? toolName : author;
    }

    /**
     * The final answer a member handed Jon: the result of the tool that is its delegation, told apart
     * from an ordinary tool result by its <b>tool name</b> alone. Two delegations in one answer of Jon's
     * are two result messages, each carrying its own tool name — which is why reading them back needs
     * neither the tool-call id nor a place to remember an order (ADR-0078 Decision 6).
     *
     * <p>The text is the result <b>verbatim</b>, statistics line included: it is the same text the model
     * reads, and shortening it would be the factory's guess about what matters. That includes a failed
     * tool — a failure is a line with an author like any other (UC-WP-40), so nothing here filters by
     * text. A tool this table does not know stays no line, and neither does a result that is not one
     * text ({@code text()} would throw on it). Its delegation's step stays an arrival: the same words
     * would otherwise stand twice in one conversation (UC-WP-82).</p>
     */
    private ChatLine finalTeamAnswerOf(ToolExecutionResultMessage result) {
        String author = names.authorOf(result.toolName());
        if (author == null || !result.hasSingleText()) {
            return null;
        }
        return ChatLine.jon(result.text(), author);
    }

    /** Only the text parts of a user message, joined as the core joins them; image parts are no chat line. */
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
}
