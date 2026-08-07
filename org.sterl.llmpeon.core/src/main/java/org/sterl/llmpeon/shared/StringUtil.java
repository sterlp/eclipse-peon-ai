package org.sterl.llmpeon.shared;

import java.io.PrintWriter;
import java.io.StringWriter;

public class StringUtil {
	
	/** any /r/n -> /n */
	public static String normelizeEndings(String value) {
		if (value == null || value.isBlank()) return value;
		return value.replace("\r\n", "\n");
	}
    
    public static String getStackTrace(final Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        final StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw, true));
        return sw.toString();
    }
    
    public static String trimToLength(String value, int size) {
        if (value == null || value.length() <= size) return value;
        return value.substring(0, size);
    }

    /**
     * Compact human duration for the sub-agent tool progress lines (see docs/sub-agent-timing.md):
     * whole seconds under a minute ({@code "3s"}, sub-second {@code "0s"}), minutes + seconds from a
     * minute up ({@code "1m 5s"}). Seconds are truncated, not rounded. Negative input counts as zero.
     */
    public static String humanElapsed(long millis) {
        long totalSeconds = millis < 0 ? 0 : millis / 1000;
        if (totalSeconds < 60) return totalSeconds + "s";
        return (totalSeconds / 60) + "m " + (totalSeconds % 60) + "s";
    }
    
    public static String getOrDefault(String value, String defaultValue) {
        if (hasValue(value)) return value;
        return defaultValue;
    }
    
    public static String trimToEmpty(String value) {
        if (value == null) return "";
        return value.trim();
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

    /** Converts a token count to a "k" string, e.g. 131072 → "131k". */
    public static String toK(long tokens) {
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
