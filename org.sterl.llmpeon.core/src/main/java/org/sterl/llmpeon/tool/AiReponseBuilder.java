package org.sterl.llmpeon.tool;

import java.util.List;

public class AiReponseBuilder {

    public static String searchComplete(List<String> results) {
        return searchComplete(results, null);
    }
    public static String searchComplete(List<String> results, String suffix) {
        var result = new StringBuilder();
        if (results.isEmpty()) {
            result.append("No files found.").append("\n")
                  .append("1. Retry with a different, shorter or more generic term (max 3 attempts total).").append("\n")
                  .append("2. After all attempts failed: if the result is critical, ask the user - otherwise continue.");
        } else {
            results.forEach(s -> result.append(s).append("\n"));
        }
        if (suffix != null) result.append("\n").append(suffix);
        return result.toString();
    }
}
