package org.sterl.llmpeon.shared;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class RegexUtils {

    private RegexUtils() {}

    private static final Map<String, Pattern> GLOB_CACHE = new ConcurrentHashMap<>();

    /**
     * Compiles a simple glob into a cached, anchored, case-insensitive {@link Pattern}. Only {@code *}
     * is special — it matches any run of characters including {@code /}; every other character is
     * matched literally. The pattern is cached per glob string, so repeated calls reuse the instance.
     */
    public static Pattern globToPattern(String glob) {
        return GLOB_CACHE.computeIfAbsent(glob, RegexUtils::compileGlob);
    }

    private static Pattern compileGlob(String glob) {
        String[] parts = glob.split("\\*", -1);
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(".*");
            if (!parts[i].isEmpty()) sb.append(Pattern.quote(parts[i]));
        }
        sb.append("$");
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    public static int countOccurrences(String content, String query) {
        return SearchQuery.of(query).count(content);
    }
}
