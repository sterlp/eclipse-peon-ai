package org.sterl.llmpeon.shared;

//https://github.com/sterlp/eclipse-peon-ai/pull/58
//https://github.com/sterlp/eclipse-peon-ai/issues/57
public class FileLines {

    /**
     * Returns the full file content with 1-based line numbers prefixed.
     */
    public static String format(String content) {
        return format(content, 1);
    }

    /**
     * Returns the file content with line numbers prefixed, starting from {@code startLine}.
     */
    public static String format(String content, int startLine) {
        if (content == null) return "";
        var lineEnding = FileUtils.dominantLineEnding(content);
        var lines = content.split(lineEnding, -1);
        var sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            appendLine(sb, startLine + i, lines[i], lineEnding);
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
    // TODO: use LIST
    public static String extract(String content, int startLine, int endLine) {
        if (content == null) return "";
        if (startLine <= 0 && endLine <= 0) return content;
        
        var lineEnding = FileUtils.dominantLineEnding(content);
        var lines = content.split(lineEnding, -1);
        int total = lines.length;

        int s = startLine <= 0 ? 1 : startLine;
        int e = endLine   <= 0 ? total : Math.min(endLine, total);

        // check order - end for start
        if (s > e) { int tmp = s; s = e; e = tmp; }

        // something is messy
        if (s > total || e > total) {
            return format(content);
        }

        var sb = new StringBuilder();
        for (int i = s - 1; i < e; i++) {
            appendLine(sb, i + 1, lines[i], lineEnding);
        }
        return sb.toString();
    }

    /**
     * Replaces lines [startLine, endLine] (1-based, inclusive) with {@code replacement}.
     * start &gt; end is swapped. Out-of-range bounds are clamped to the file size.
     */
    public static String replaceLines(String content, int startLine, int endLine, String replacement) {
        if (content == null) return replacement == null ? "" : replacement;

        var lineEnding = FileUtils.dominantLineEnding(content);
        var lines = new java.util.ArrayList<>(java.util.Arrays.asList(content.split(lineEnding, -1)));
        int total = lines.size();

        int s = startLine <= 0 ? 1 : startLine;
        int e = endLine   <= 0 ? total : endLine;
        if (s > e) { int tmp = s; s = e; e = tmp; }
        s = Math.max(1, Math.min(s, total));
        e = Math.max(1, Math.min(e, total));

        for (int i = e; i >= s; i--) lines.remove(i - 1);

        if (replacement != null && !replacement.isEmpty()) {
            var incoming = replacement.split(lineEnding, -1);
            for (int i = incoming.length - 1; i >= 0; i--) lines.add(s - 1, incoming[i]);
        }

        return String.join(lineEnding, lines);
    }

    /**
     * Inserts {@code newContent} after the given 1-based {@code afterLine}.
     * <ul>
     *   <li>{@code afterLine} null, 0 or negative → append at end of file</li>
     *   <li>{@code afterLine} &gt;= line count → append at end of file</li>
     *   <li>{@code afterLine} == 0 semantics for "before first line" are not supported; use replaceLines</li>
     * </ul>
     */
    public static String insertLines(String content, Integer afterLine, String newContent) {
        if (newContent == null || newContent.isEmpty()) return content == null ? "" : content;
        if (content == null || content.isEmpty()) return newContent;

        var lineEnding = FileUtils.dominantLineEnding(content);
        var lines = new java.util.ArrayList<>(java.util.Arrays.asList(content.split(lineEnding, -1)));
        int total = lines.size();

        int at = (afterLine == null || afterLine <= 0 || afterLine > total) ? total : afterLine;

        var incoming = java.util.Arrays.asList(newContent.split(lineEnding, -1));
        lines.addAll(at, incoming);

        return String.join(lineEnding, lines);
    }

    private static void appendLine(StringBuilder sb, int n, String line, String lineEnding) {
        if      (n <    10) sb.append("   ");
        else if (n <   100) sb.append("  ");
        else if (n <  1000) sb.append(' ');
        sb.append(n).append(": ").append(line).append(lineEnding);
    }
}
