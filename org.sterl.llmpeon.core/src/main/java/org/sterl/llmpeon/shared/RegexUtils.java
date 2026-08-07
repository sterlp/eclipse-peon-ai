package org.sterl.llmpeon.shared;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
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

    /**
     * Detects if a query contains common regex operators that indicate regex intent.
     */
    public static boolean isRegexPattern(String query) {
        return query.indexOf('*') != -1 || query.indexOf('|') != -1
                || query.indexOf('+') != -1 || query.indexOf('^') != -1 || query.indexOf('$') != -1;
    }

    /**
     * Counts occurrences of a query in content. Uses regex matching if the query
     * contains regex operators, otherwise falls back to literal matching.
     */
    public static int countOccurrences(String content, String query) {
        if (isRegexPattern(query)) {
            try {
                Pattern pattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(content);
                int count = 0;
                while (matcher.find()) {
                    count++;
                }
                return count;
            } catch (IllegalArgumentException e) {
                return 0;
            }
        } else {
            String lowerContent = content.toLowerCase();
            String lowerQuery = query.toLowerCase();
            int count = 0;
            int idx = 0;
            while ((idx = lowerContent.indexOf(lowerQuery, idx)) != -1) {
                count++;
                idx += lowerQuery.length();
            }
            return count;
        }
    }
}
