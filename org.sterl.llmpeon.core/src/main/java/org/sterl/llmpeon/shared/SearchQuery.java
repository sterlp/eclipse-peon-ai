package org.sterl.llmpeon.shared;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public record SearchQuery(String query, Pattern pattern, boolean literal) {

    private static final Map<String, SearchQuery> CACHE = new ConcurrentHashMap<>();
    private static final String LITERAL_HINT = "literal search — query is not a valid regex";

    public static SearchQuery of(String query) {
        return CACHE.computeIfAbsent(query, SearchQuery::compile);
    }

    private static SearchQuery compile(String query) {
        try {
            return new SearchQuery(query, Pattern.compile(query, Pattern.CASE_INSENSITIVE), false);
        } catch (PatternSyntaxException e) {
            return new SearchQuery(query, Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE), true);
        }
    }

    public int count(String content) {
        if (!literal) {
            var matcher = pattern.matcher(content);
            int count = 0;
            while (matcher.find()) count++;
            return count;
        }

        String lowerContent = content.toLowerCase();
        String lowerQuery = query.toLowerCase();
        int count = 0;
        int index = 0;
        while ((index = lowerContent.indexOf(lowerQuery, index)) != -1) {
            count++;
            index += lowerQuery.length();
        }
        return count;
    }

    public boolean matches(String line) {
        return pattern.matcher(line).find();
    }

    public String modeHint() {
        return literal ? LITERAL_HINT : "regex search";
    }
}
