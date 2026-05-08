package org.sterl.llmpeon.streaming;

import java.util.ArrayDeque;

/**
 * Rolling line buffer for live thinking output.
 * Accumulates streaming text, splits on newlines, and retains only the last
 * {@code lineCount} non-empty lines so the UI preview stays bounded.
 */
public class ThinkingBuffer {

    private final int lineCount;
    private final ArrayDeque<String> lines;
    private final StringBuilder partial = new StringBuilder();

    public ThinkingBuffer(int lineCount) {
        this.lineCount = lineCount;
        this.lines = new ArrayDeque<>(lineCount + 1);
    }

    /**
     * Appends a streaming chunk, flushes complete lines into the rolling window,
     * and returns the current display string (lines joined by {@code \n}).
     * Returns the partial buffer when no complete line exists yet.
     */
    public String append(String value) {
        partial.append(value);
        int idx;
        while ((idx = partial.indexOf("\n")) >= 0) {
            String line = partial.substring(0, idx).trim();
            partial.delete(0, idx + 1);
            if (!line.isEmpty()) {
                lines.addLast(line);
                if (lines.size() > lineCount) lines.pollFirst();
            }
        }
        String result = String.join("\n", lines);
        return result.isEmpty() ? partial.toString() : result;
    }

    /** Resets state — call at the start of each new request. */
    public void clear() {
        lines.clear();
        partial.setLength(0);
    }
}
