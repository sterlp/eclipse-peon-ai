package org.sterl.llmpeon.tool;

import java.util.ArrayList;
import java.util.List;

import org.sterl.llmpeon.shared.GrepHit;
import org.sterl.llmpeon.shared.SearchQuery;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.shared.TextFileTypes;

public class AiReponseBuilder {

    public static final int MAX_GREP_FILES = 100;
    public static final int MAX_GREP_LINES = 100;

    public static String searchComplete(List<String> results) {
        return searchComplete(results, null);
    }

    /**
     * R-OD: discloses a reached result cap ("capped at N — narrow your search") — a tool must
     * never lie about a limit. Trigger is {@code >=}: exactly at the limit the result is already
     * cropped. {@code limit <= 0} means unlimited → no disclosure.
     */
    public static String searchComplete(List<String> results, int limit, String suffix) {
        if (limit > 0 && results.size() >= limit) {
            suffix = suffix == null
                    ? "capped at " + limit + " — narrow your search"
                    : suffix + System.lineSeparator() + "capped at " + limit + " — narrow your search";
        }
        return searchComplete(results, suffix);
    }
    public static String grepComplete(List<String> results, String suffix) {
        var result = new StringBuilder();
        if (results.isEmpty()) {
            result.append("no matches");
        } else {
            results.forEach(s -> result.append(s).append(System.lineSeparator()));
        }
        if (suffix != null) result.append(System.lineSeparator()).append(suffix);
        return result.toString();
    }

    /**
     * R8: renders grep hits as {@code file:line: text} (1-based, unpadded), replacing the old
     * per-file occurrence counts. Caps: {@code maxFiles} distinct files, {@code maxLines} lines
     * total (cross-file). Honesty stays in the output: mode hint (R2c), type-filter hint
     * (R6a/R6b) and the line-cap disclosure.
     */
    public static String grepComplete(List<GrepHit> hits, SearchQuery query, int maxFiles, int maxLines,
            String extension) {
        int total = hits.size();
        int shown = Math.min(total, maxLines);
        var lines = new ArrayList<String>(shown);
        for (int i = 0; i < shown; i++) {
            var h = hits.get(i);
            lines.add(h.file() + ":" + h.line() + ": " + h.text());
        }

        String suffix = query.modeHint();
        if (total > maxLines) {
            suffix = "showing " + shown + " of " + total + " matched lines — narrow your search"
                    + System.lineSeparator() + suffix;
        }
        if (total == 0 && !StringUtil.hasValue(extension)) {
            suffix += System.lineSeparator() + TextFileTypes.filterHint();
        }
        if (GrepHit.fileCount(hits) >= maxFiles) {
            suffix += System.lineSeparator()
                    + "... result capped at " + maxFiles + " files. Narrow your search path.";
        }
        return grepComplete(lines, suffix);
    }

    public static String searchComplete(List<String> results, String suffix) {
        var result = new StringBuilder();
        if (results.isEmpty()) {
            result.append("No files found.").append("\n")
                  .append("1. Retry with a different, shorter or more generic term (max 3 attempts total).").append(System.lineSeparator())
                  .append("2. After all attempts failed: if the result is critical, ask the user - otherwise continue.");
        } else {
            results.forEach(s -> result.append(s).append(System.lineSeparator()));
        }
        if (suffix != null) result.append(System.lineSeparator()).append(suffix);
        return result.toString();
    }
}
