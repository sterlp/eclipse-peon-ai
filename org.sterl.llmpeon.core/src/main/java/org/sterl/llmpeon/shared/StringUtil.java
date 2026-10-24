package org.sterl.llmpeon.shared;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StringUtil {

    /** The one local wall-clock pattern for human-facing lines (R-TD-1, R-OD-8). */
    public static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    /** A stack frame inside an exception message — never rendered in a one-line report (R-OD-8). */
    private static final Pattern STACK_FRAME = Pattern.compile("\\s*at\\s+\\S.*");

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
     * Compact human duration for the sub-agent tool progress lines (see org.sterl.llmpeon.core/docs/sub-agent-timing.md):
     * whole seconds under a minute ({@code "3s"}, sub-second {@code "0s"}), minutes + seconds from a
     * minute up ({@code "1m 5s"}). Seconds are truncated, not rounded. Negative input counts as zero.
     */
    public static String humanElapsed(long millis) {
        long totalSeconds = millis < 0 ? 0 : millis / 1000;
        if (totalSeconds < 60) return totalSeconds + "s";
        return (totalSeconds / 60) + "m " + (totalSeconds % 60) + "s";
    }

    /**
     * Size disclosure for tool completion lines (R-RS-1, org.sterl.llmpeon.core/docs/tool-result-size.md):
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
     * One disclosure format for every context-counter surface (R-CC-6, org.sterl.llmpeon.core/docs/compact.md):
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
     * (R5a, org.sterl.llmpeon.core/docs/token-usage.md; Issue #154). Under 1 million: whole thousands, plain integer (max
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

    /**
     * The one tool-failure line for the human chat (R-OD-8,
     * org.sterl.llmpeon.core/docs/tool-output-disclosure.md): tool name, local time, exception and cause
     * chain — never a stack, the chat must not flood.
     *
     * <p>{@code buildWithDev failed (14:32): IllegalStateException: closed ← IOException: Connection reset — details logged.}
     */
    public static String toolFailureLine(String toolName, Throwable e, Clock clock) {
        return toolName + " failed (" + LocalTime.now(clock).format(HH_MM) + "): " + exceptionChain(e)
                + " — details logged.";
    }

    /**
     * The exception and its causes, outermost first, as {@code Klasse: message} links joined by {@code ←}
     * (R-OD-8). Simple class names only — the fully qualified one says nothing the message does not.
     * A link whose message only restates its cause is dropped: the cause renders the same fact, so the
     * wrapper would only double the line (that is how tool exceptions reach us).
     */
    public static String exceptionChain(Throwable e) {
        var chain = new ArrayList<Throwable>();
        for (var current = e; current != null; current = current.getCause()) {
            chain.add(current);
        }
        var line = new StringBuilder();
        for (var index = 0; index < chain.size(); index++) {
            var current = chain.get(index);
            if (isRedundantWrapper(current, index + 1 < chain.size() ? chain.get(index + 1) : null)) continue;
            if (!line.isEmpty()) line.append(" ← ");
            line.append(current.getClass().getSimpleName()).append(": ").append(messageForLine(current));
        }
        return line.toString();
    }

    /**
     * A wrapper that restates its cause and adds nothing: its message is the cause's message verbatim
     * (langchain4j does this) or the cause's whole {@code toString()} (the JDK {@code RuntimeException(cause)}
     * does this). The cause renders the fact itself, so the wrapper would only double it.
     */
    private static boolean isRedundantWrapper(Throwable current, Throwable cause) {
        if (cause == null || current.getMessage() == null) return false;
        return current.getMessage().equals(cause.getMessage()) || current.getMessage().equals(cause.toString());
    }

    /**
     * The message as one line (R-OD-8): stack-frame lines are dropped — the stack lives in the log and in
     * the tool result for the model — remaining whitespace collapses to a single space, and a message
     * without prose renders as {@code (no message)} instead of {@code null}.
     */
    private static String messageForLine(Throwable e) {
        var message = e.getMessage();
        if (hasNoValue(message)) return "(no message)";
        var prose = Arrays.stream(message.split("\\R"))
                .filter(frame -> !STACK_FRAME.matcher(frame).matches())
                .collect(Collectors.joining(" "))
                .replaceAll("\\s+", " ")
                .strip();
        return hasNoValue(prose) ? "(no message)" : prose;
    }
}
