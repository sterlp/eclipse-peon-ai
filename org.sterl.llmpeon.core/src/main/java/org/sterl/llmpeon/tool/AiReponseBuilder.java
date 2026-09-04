package org.sterl.llmpeon.tool;

import java.util.List;
import java.util.Map;

import org.sterl.llmpeon.shared.SearchQuery;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.shared.TextFileTypes;

public class AiReponseBuilder {

    public static final int MAX_GREP_FILES = 100;

    public static String searchComplete(List<String> results) {
        return searchComplete(results, null);
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

    public static String grepComplete(Map<String, Integer> matches, SearchQuery query, int maxFiles,
            String extension) {
        String suffix = query.modeHint();
        if (matches.isEmpty() && !StringUtil.hasValue(extension)) {
            suffix += System.lineSeparator() + TextFileTypes.filterHint();
        }
        if (matches.size() >= maxFiles) {
            suffix += System.lineSeparator()
                    + "... result capped at " + maxFiles + " files. Narrow your search path.";
        }
        return grepComplete(matches.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue() + " occurrence(s)").toList(), suffix);
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
