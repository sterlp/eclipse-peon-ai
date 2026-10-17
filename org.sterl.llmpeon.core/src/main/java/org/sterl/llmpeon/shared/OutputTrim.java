package org.sterl.llmpeon.shared;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The single place for tool-output trimming: per-line cap + head/tail char window + disclosure.
 * Composed by {@code ShellTool} (streaming via {@link Window}) and the Grep tools (one-shot via
 * {@link #trim(List, int)}) — one behaviour, one implementation.
 *
 * <p>Head/tail split: head gets {@code maxChars/3}, tail the remaining 2/3 (end first — errors
 * sit at the end of build logs). The body is assembled with literal {@code "\n"} separators
 * (platform-independent, deterministic — Trim/Tail reassembly decision 2026-09-29); the caller's
 * own framing (cwd/exit/filter lines) keeps {@code System.lineSeparator()}.
 *
 * <p>Thread-safety: a {@link Window} is one instance per call, written by a single reader
 * thread; {@link Window#build()} runs after {@code join()} (happens-before), so no internal
 * synchronization is needed. {@link #trim(List, int)} is a pure function.
 */
public final class OutputTrim {

    /** Default total output budget in chars — single source of truth for Shell and Grep. */
    public static final int DEFAULT_MAX_CHARS = 10_000;
    /** Per-line cap in chars; longer lines are cut with a {@code …(+N chars)} marker. */
    public static final int MAX_LINE_CHARS = 500;

    private OutputTrim() {}

    /**
     * One-shot trim over an existing line list (Grep). Equivalent to
     * {@code window + addLine* + build}.
     */
    public static Result trim(List<String> lines, int maxChars) {
        Window window = window(maxChars);
        for (String line : lines) window.addLine(line);
        return window.build();
    }

    /**
     * Streaming window for a line source (Shell reader). One instance per call:
     * {@code addLine} from the reader thread, {@code build()} after {@code join()}.
     * A non-positive {@code maxChars} falls back to {@link #DEFAULT_MAX_CHARS}.
     */
    public static Window window(int maxChars) {
        return new Window(maxChars > 0 ? maxChars : DEFAULT_MAX_CHARS);
    }

    /**
     * Per-line cap: over {@link #MAX_LINE_CHARS} → first 500 chars + {@code …(+N chars)}
     * (N = cut chars, honest about the loss); at or under the cap → unchanged.
     */
    public static String capLine(String line) {
        if (line == null) return "";
        if (line.length() <= MAX_LINE_CHARS) return line;
        return line.substring(0, MAX_LINE_CHARS) + "…(+" + (line.length() - MAX_LINE_CHARS) + " chars)";
    }

    /**
     * Head/tail char window. Head keeps whole lines while the 1/3 budget allows (the first line
     * always, even alone over budget); tail is a ring over the 2/3 budget that evicts the oldest
     * line first and always keeps the newest.
     */
    public static final class Window {
        private final List<String> head = new ArrayList<>();
        private final Deque<String> tail = new ArrayDeque<>();
        private final int headBudget;
        private final int tailBudget;
        private int headCost;
        private int tailCost;
        private int droppedLines;
        private int truncatedLines;
        private int totalLines;
        private long totalChars;
        private boolean headClosed;

        private Window(int maxChars) {
            this.headBudget = maxChars / 3;
            this.tailBudget = maxChars - headBudget;
        }

        /**
         * Feed one raw line: counts its raw chars, applies the per-line cap, places the capped
         * line into head or the tail ring. Writer: the single reader thread.
         */
        public void addLine(String line) {
            if (line == null) line = "";
            totalLines++;
            totalChars += line.length() + 1L;
            String capped = capLine(line);
            if (line.length() > MAX_LINE_CHARS) truncatedLines++;
            int cost = capped.length() + 1;
            if (head.isEmpty() && !headClosed) {
                head.add(capped);
                headCost += cost;
                if (headCost > headBudget) headClosed = true;
            } else if (!headClosed && headCost + cost <= headBudget) {
                head.add(capped);
                headCost += cost;
            } else {
                headClosed = true;
                tail.addLast(capped);
                tailCost += cost;
                while (tailCost > tailBudget && tail.size() > 1) {
                    String evicted = tail.pollFirst();
                    tailCost -= evicted.length() + 1;
                    droppedLines++;
                }
            }
        }

        /** No lines fed yet (Shell timeout-without-output branch). */
        public boolean isEmpty() {
            return totalLines == 0;
        }

        /**
         * Assembles the trimmed result — call after the reader thread has joined.
         * Middle drop → disclosure between head and tail (marks the gap); line caps only →
         * disclosure at the end; nothing trimmed → no disclosure.
         */
        public Result build() {
            if (totalLines == 0) return new Result("", 0, 0, 0, 0, 0, false);
            int shownLines = head.size() + tail.size();
            int shownChars = headCost + tailCost - 1;
            boolean truncated = droppedLines > 0 || truncatedLines > 0;
            String text;
            if (droppedLines > 0) {
                text = String.join("\n", head) + "\n" + note(totalChars, shownChars) + "\n" + String.join("\n", tail);
            } else if (truncatedLines > 0) {
                text = joinAll() + "\n" + note(totalChars, shownChars);
            } else {
                text = joinAll();
            }
            return new Result(text, shownLines, totalLines, totalChars, shownChars, truncatedLines, truncated);
        }

        private String joinAll() {
            List<String> all = new ArrayList<>(head);
            all.addAll(tail);
            return String.join("\n", all);
        }

        private static String note(long totalChars, int shownChars) {
            return "… (truncated: " + totalChars + " chars total, " + shownChars + " shown)";
        }
    }

    /**
     * Trim result.
     * <ul>
     *   <li>{@code text}: body, {@code "\n"}-joined (D6), disclosure note included when trimmed.</li>
     *   <li>{@code totalChars}: Σ raw line+1 over all fed lines (raw, pre-cap) — with a Shell
     *       filter that is the raw chars of the matched lines; the filter's own disclosure
     *       names the total line count separately.</li>
     *   <li>{@code shownChars}: head+tail chars incl. separators, excl. the note.</li>
     * </ul>
     */
    public record Result(String text, int shownLines, int totalLines,
                         long totalChars, int shownChars, int truncatedLines, boolean truncated) {}
}
