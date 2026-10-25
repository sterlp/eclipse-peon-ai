package org.sterl.llmpeon.tool.tools;

import org.sterl.llmpeon.agent.model.TaggedResult;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * The rolling hot block of the search agent — its content, limits and disclosure are the SOLL of
 * R-LSR-1…6 (org.sterl.llmpeon.core/docs/search-agent-tool.md, ADR-0075); this class only implements it.
 *
 * <p>Every access is synchronized: one instance belongs to one {@link SearchAgentTool}, hence to one
 * chat view, and all agents of that view reach it — no single-threaded assumption holds here.
 *
 * <p>The block is framed by {@link TaggedResult} like every other block the models are shown, so the
 * start/end format has exactly one implementation in the repo.
 */
final class RollingSearchContext {

    static final int MAX_CHARS = 10_000;

    private final StringBuilder pairs = new StringBuilder();
    private boolean trimmed;

    /** Appends one search pair; a blank side leaves the block untouched. */
    synchronized void append(String prompt, String answer) {
        if (StringUtil.hasNoValue(prompt) || StringUtil.hasNoValue(answer)) return;
        // literal \n: pair text the cap counts char-wise, so the block must not follow the platform
        pairs.append("Frage: ").append(prompt.strip()).append('\n')
                .append("Antwort: ").append(answer.strip()).append('\n');
        if (pairs.length() > MAX_CHARS) {
            pairs.delete(0, pairs.length() - MAX_CHARS);
            trimmed = true;
        }
    }

    /** The snapshot one sub-loop call sees — {@code ""} while empty (never null), else the framed block. */
    synchronized String render() {
        if (pairs.isEmpty()) return "";
        var body = new StringBuilder(pairs);
        body.setLength(body.length() - 1);           // TaggedResult places the newline before its end line
        if (trimmed) body.append(TaggedResult.LN).append("//(older context dropped to fit cap)");
        return new TaggedResult("last-search-rolling", body.toString()).toString();
    }
}
