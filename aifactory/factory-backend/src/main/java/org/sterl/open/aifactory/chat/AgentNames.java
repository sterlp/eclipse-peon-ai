package org.sterl.open.aifactory.chat;

import java.util.HashMap;
import java.util.Map;

import org.sterl.llmpeon.poagent.AiPoAgent;

/**
 * Two questions about an author, answered in one place and nowhere else.
 *
 * <p><b>Whose conversation is this?</b> {@link #isJon(String)} compares the id the core stamped on a
 * live line with {@link AiPoAgent#NAME}. Comparing against the constant and not a literal is what keeps
 * the rename of ADR-0079 a core-side change: the answer follows the id, nothing here has to move with
 * it.</p>
 *
 * <p><b>Who wrote this tool result?</b> A history line carries no author of its own — its author is the
 * <b>tool</b> that produced it, so the table is keyed by tool name ({@code @Tool(name = …)}, the core's
 * contract) and names the member in the factory's own words. A tool nobody in this table owns is not a
 * member speaking, and gets no line.</p>
 *
 * <p>{@code null} is nobody: neither question invents an author (R-WP-4). Immutable; the runtime keeps one
 * instance for its author table.</p>
 */
final class AgentNames {

    private final Map<String, String> authorsByTool;

    private AgentNames(Map<String, String> authorsByTool) {
        this.authorsByTool = Map.copyOf(authorsByTool);
    }

    /** The empty table — a runtime adds the authors of the team it actually built. */
    static AgentNames empty() {
        return new AgentNames(Map.of());
    }

    /** @return the table plus this author — the receiver is never mutated, building it stays one chain */
    AgentNames withTool(String toolName, String author) {
        Map<String, String> withAuthor = new HashMap<>(authorsByTool);
        withAuthor.put(toolName, author);
        return new AgentNames(withAuthor);
    }

    /** The member that speaks through this tool, or {@code null} when the tool is not a member. */
    String authorOf(String toolName) {
        // an immutable Map NPEs on a null key — a result without a tool name is nobody's
        return toolName == null ? null : authorsByTool.get(toolName);
    }

    /** The agent the one conversation of a project belongs to — the only author the live stream shows. */
    static boolean isJon(String origin) {
        return AiPoAgent.NAME.equals(origin);
    }
}
