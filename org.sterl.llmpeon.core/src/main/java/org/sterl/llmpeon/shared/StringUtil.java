package org.sterl.llmpeon.shared;

public class StringUtil {
    
    public static String getOrDefault(String value, String defaultValue) {
        if (hasValue(value)) return value;
        return defaultValue;
    }

    public static String strip(String value) {
        if (value == null || value.length() == 0) return value;
        return value.strip();
    }
    
    public static String stripToNull(String value) {
        value = strip(value);
        if (value == null || value.length() == 0) return null;
        return value;
    }
    
    public static boolean hasValue(String value) {
        if (stripToNull(value) == null) return false;
        return true;
    }
    
    public static boolean hasNoValue(String value) {
        return !hasValue(value);
    }

    public static String stripToEmpty(String value) {
        if (value == null) return "";
        return value.strip();
    }

    /** Converts a token count to a "k" string, e.g. 131072 → "131k". */
    public static String toK(int tokens) {
        return (tokens / 1000) + "k";
    }

    /**
     * Appends the context window size in "k" to the model name when maxInputTokens is known.
     * E.g. "mistral-medium-latest" + 131072 → "mistral-medium-latest (131k)".
     */
    public static String formatModelName(String name, Integer maxInputTokens) {
        if (maxInputTokens == null) return name;
        return name + " (" + toK(maxInputTokens) + ")";
    }

    public static String offsetToLine(String value, int offset) {
        if (value == null || offset < 0) return null;
        var lines = value.split("\n");
        if (offset > lines.length - 1) return null;
        return lines[offset];
    }
}
