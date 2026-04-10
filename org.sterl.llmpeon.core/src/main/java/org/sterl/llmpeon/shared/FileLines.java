package org.sterl.llmpeon.shared;

public class FileLines {

    /**
     * Returns the full file content with 1-based line numbers prefixed.
     */
    public static String format(String content) {
        if (content == null) return "";
        var lines = content.split("\n", -1);
        var sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            appendLine(sb, i + 1, lines[i]);
        }
        return sb.toString();
    }

    /**
     * Extracts lines [startLine, endLine] (1-based, inclusive) with line numbers.
     * <ul>
     *   <li>0 or negative start/end → treat as 1 / last line respectively</li>
     *   <li>start &gt; end → swapped automatically</li>
     *   <li>Either bound out of range → whole file returned</li>
     * </ul>
     */
    public static String extract(String content, int startLine, int endLine) {
        if (content == null) return "";

        var lines = content.split("\n", -1);
        int total = lines.length;

        int s = startLine <= 0 ? 1 : startLine;
        int e = endLine   <= 0 ? total : endLine;

        if (s > e) { int tmp = s; s = e; e = tmp; }

        if (s > total || e > total || s < 1) {
            return format(content);
        }

        var sb = new StringBuilder();
        for (int i = s - 1; i < e; i++) {
            appendLine(sb, i + 1, lines[i]);
        }
        return sb.toString();
    }

    /**
     * Replaces lines [startLine, endLine] (1-based, inclusive) with {@code replacement}.
     * start &gt; end is swapped. Out-of-range bounds are clamped to the file size.
     */
    public static String replaceLines(String content, int startLine, int endLine, String replacement) {
        if (content == null) return replacement == null ? "" : replacement;

        var lines = new java.util.ArrayList<>(java.util.Arrays.asList(content.split("\n", -1)));
        int total = lines.size();

        int s = startLine <= 0 ? 1 : startLine;
        int e = endLine   <= 0 ? total : endLine;
        if (s > e) { int tmp = s; s = e; e = tmp; }
        s = Math.max(1, Math.min(s, total));
        e = Math.max(1, Math.min(e, total));

        for (int i = e; i >= s; i--) lines.remove(i - 1);

        if (replacement != null && !replacement.isEmpty()) {
            var incoming = replacement.split("\n", -1);
            for (int i = incoming.length - 1; i >= 0; i--) lines.add(s - 1, incoming[i]);
        }

        return String.join("\n", lines);
    }

    private static void appendLine(StringBuilder sb, int n, String line) {
        if      (n <    10) sb.append("   ");
        else if (n <   100) sb.append("  ");
        else if (n <  1000) sb.append(' ');
        sb.append(n).append(": ").append(line).append('\n');
    }
}
