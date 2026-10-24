package org.sterl.llmpeon.tool.tools;

import org.sterl.llmpeon.shared.StringUtil;

/**
 * The rolling hot block `last-search-rolling` (R-LSR-1…6,
 * org.sterl.llmpeon.core/docs/search-agent-tool.md § Rolling Search Context): the prompts and final
 * answers of the previous complete searches of one chat view, append-only and raw-cut to the last
 * {@value #MAX_CHARS} characters (ADR-0075 — a mechanical char cut, fragments accepted).
 *
 * <p>One instance per {@link SearchAgentTool}, which is one per chat view (R-LSR-6). Every access is
 * synchronized: the block is shared by all agents of the view and no single-threaded assumption holds.
 *
 * <p>Lines are built with literal {@code "\n"} — this is model-facing text, not file output, so it
 * renders identically on every platform (same decision as the trim/tail reassembly).
 */
final class RollingSearchContext {

    static final String START = "//-- Start last-search-rolling";
    static final String END = "//-- End last-search-rolling";
    static final String DISCLOSURE = "//(older context dropped to fit cap)";
    static final int MAX_CHARS = 10_000;

    private final StringBuilder pairs = new StringBuilder();
    private boolean trimmed;

    /**
     * R-LSR-1/2: one pair per completed search — a blank side never touches the block, so a failed,
     * canceled or empty search cannot leave a half entry behind.
     */
    synchronized void append(String prompt, String answer) {
        if (StringUtil.hasNoValue(prompt) || StringUtil.hasNoValue(answer)) return;
        pairs.append("Frage: ").append(prompt.strip()).append('\n')
                .append("Antwort: ").append(answer.strip()).append('\n');
        if (pairs.length() > MAX_CHARS) {
            pairs.delete(0, pairs.length() - MAX_CHARS);
            trimmed = true;
        }
    }

    /**
     * R-LSR-3/4/5: the snapshot one sub-loop call sees — {@code ""} while empty (never null), else the
     * pair content (already cut to the cap, so the decoration never shifts the limit) framed by the
     * headers, plus the disclosure line only where a cut actually happened.
     */
    synchronized String render() {
        if (pairs.isEmpty()) return "";
        return START + "\n" + pairs + (trimmed ? DISCLOSURE + "\n" : "") + END;
    }
}
