package org.sterl.llmpeon.shared;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The live status readout shown in the status bar while a turn streams: a human-readable
 * {@code state} line plus a token rate in tokens/second.
 * <p>
 * Pure and clock-independent: the caller supplies {@code nowMillis} and the values already stamped
 * on the chunk. The turn start ({@link OnPartialAiResponse#startedAt()}) drives the elapsed time,
 * the token-phase start ({@link OnPartialAiResponse#tokenPhaseStart()}) drives the rate. Nothing
 * here touches a Timer or the system clock, so it is trivially unit-testable.
 * <p>
 * The 4th parameter {@code lastTokenPhaseStart} is the widget's tracked value from the previous
 * chunk; when it differs from the chunk's {@code tokenPhaseStart} the chunk is the first token of
 * a new call (R22) and the rate is {@code 0}.
 */
public record LiveStatus(String state, double tokPerSec) {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    public static LiveStatus of(OnPartialAiResponse chunk, long tokens, long nowMillis, long lastTokenPhaseStart) {
        long elapsed = nowMillis - chunk.startedAt().toEpochMilli();
        String state = switch (chunk.type()) {
            case START -> "Started " + hhmm(chunk.startedAt()) + " · waiting for AI...";
            case THINK -> "Started " + hhmm(chunk.startedAt()) + " · working since " + formatElapsed(elapsed) + " | thinking...";
            case ANSWER -> "Started " + hhmm(chunk.startedAt()) + " · working since " + formatElapsed(elapsed) + " | responding...";
            case TOOL -> "Started " + hhmm(chunk.startedAt()) + " · working since " + formatElapsed(elapsed) + " | using tools...";
            case END -> "AI done.";
        };
        return new LiveStatus(state, rate(tokens, nowMillis, chunk.tokenPhaseStart(), lastTokenPhaseStart));
    }

    /** The turn start as a local wall-clock time, e.g. {@code 09:07} (leading zero). */
    private static String hhmm(Instant instant) {
        return HH_MM.withZone(ZoneId.systemDefault()).format(instant);
    }

    /**
     * Tokens/second over the token phase; {@code 0} while the phase has not started
     * ({@code tokenPhaseStart == 0}) or on the first token of a new call
     * ({@code tokenPhaseStart != lastTokenPhaseStart}, R22).
     */
    private static double rate(long tokens, long nowMillis, long tokenPhaseStart, long lastTokenPhaseStart) {
        if (tokenPhaseStart == 0) return 0;
        if (tokenPhaseStart != lastTokenPhaseStart) return 0;
        long elapsed = Math.max(1, nowMillis - tokenPhaseStart);
        return tokens / (elapsed / 1000.0);
    }

    /** {@code 45s} / {@code 3m 15s} / {@code 1h 4m 5s} — no zero-padding, matching the old {@code Xs} style. */
    private static String formatElapsed(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) return hours + "h " + minutes + "m " + seconds + "s";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }
}
