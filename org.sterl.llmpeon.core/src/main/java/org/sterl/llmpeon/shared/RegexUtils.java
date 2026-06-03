package org.sterl.llmpeon.shared;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RegexUtils {

    private RegexUtils() {}

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
