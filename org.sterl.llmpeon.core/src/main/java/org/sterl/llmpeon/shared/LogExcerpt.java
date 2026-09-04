package org.sterl.llmpeon.shared;

import java.util.Arrays;

public record LogExcerpt(String text, int shown, int matching, int total, SearchQuery grep) {

    public static LogExcerpt of(String content, int lines, SearchQuery grep) {
        if (content.isEmpty()) return new LogExcerpt("", 0, 0, 0, grep);

        int limit = Math.max(1, lines);
        String[] contentLines = content.split("\n", -1);
        int total = contentLines.length;
        if (grep == null) {
            return new LogExcerpt(FileLines.tail(content, limit), Math.min(limit, total), total, total, null);
        }

        var matchingLines = Arrays.stream(contentLines).filter(grep::matches).toList();
        int matching = matchingLines.size();
        int shown = Math.min(limit, matching);
        String text = String.join("\n", matchingLines.subList(matching - shown, matching));
        return new LogExcerpt(text, shown, matching, total, grep);
    }

    public boolean filtered() {
        return grep != null;
    }

    public String header(String source) {
        if (filtered()) {
            return "showing " + shown + " of " + matching + " matching lines (console: " + source
                    + ", total " + total + ") · " + grep.modeHint();
        }
        return "showing " + shown + " of " + total + " lines (console: " + source + ")";
    }
}
