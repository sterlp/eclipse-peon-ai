package org.sterl.llmpeon.shared;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.NumberFormat;

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

    /**
     * Size disclosure for tool completion lines (R-RS-1, docs/tool-result-size.md):
     * the exact character count of the tool's return string as {@code "(N chars)"}.
     * A {@code null} result counts as zero — honest, never omitted.
     */
    public static String charsSuffix(String result) {
        return "(" + (result == null ? 0 : result.length()) + " chars)";
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

    /**
     * One disclosure format for every context-counter surface (R-CC-6, docs/compact-context-counter.md):
     * estimate values render as {@code ~N (estimate)}, exact provider values render plain.
     */
    public static String estimateAware(boolean isEstimate, String value) {
        return isEstimate ? "~" + value + " (estimate)" : value;
    }

    /** @see #toK(long) */
    public static String toK(int tokens) {
        return toK((long) tokens);
    }

    /**
     * Compact token-count display for the session header, the Compact button and the model-name window
     * (R5a, docs/token-usage.md; Issue #154). Under 1 million: whole thousands, plain integer (max
     * {@code "999k"}, grouping never needed) — 131072 → {@code "131k"}. From 1 million up: millions with
     * up to 2 fraction digits, the decimal separator and integer grouping following the default locale —
     * 1_000_000 → {@code "1M"}, 1_840_000 → {@code "1,84M"} (DE) / {@code "1.84M"} (US),
     * 999_999_999 → {@code "1.000M"} (DE) / {@code "1,000M"} (US).
     */
    public static String toK(long tokens) {
        if (tokens < 1_000_000) {
            return (tokens / 1000) + "k";
        }
        NumberFormat format = NumberFormat.getInstance();
        format.setMaximumFractionDigits(2);
        return format.format(tokens / 1_000_000.0) + "M";
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
