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

    /** One matched line: 1-based line number plus the raw line text. */
    public record LineHit(int line, String text) {}

    /**
     * R8: all lines that match, 1-based over the full file content, in file order.
     * Uses the same regex-first / literal-fallback mode as {@link #matches(String)}.
     */
    public java.util.List<LineHit> matchingLines(String content) {
        if (content == null || content.isEmpty()) return java.util.List.of();
        var lines = content.split(FileUtils.dominantLineEnding(content), -1);
        var hits = new java.util.ArrayList<LineHit>();
        for (int i = 0; i < lines.length; i++) {
            if (matches(lines[i])) hits.add(new LineHit(i + 1, lines[i]));
        }
        return hits;
    }

    public String modeHint() {
        return literal ? LITERAL_HINT : "regex search";
    }
}
