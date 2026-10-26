package org.sterl.llmpeon.shared;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * A single streaming chunk delivered to the UI, in order.
 *
 * @param type            the kind of chunk
 * @param value           the text payload (null for START/END); for TOOL chunks the argument delta, not the tool name
 * @param startedAt       the turn start — identical for every chunk of the turn
 * @param tokenPhaseStart epoch millis of the first partial of the current call, or {@code 0} while the token phase has not started yet
 * @param origin          the core id of the agent whose turn produced this chunk, or {@code null} when no
 *                        agent owns it — set by {@link OriginMonitor}, never guessed (see
 *                        {@link org.sterl.llmpeon.tool.model.SimpleMessage#origin()} for the same rule)
 */
public record OnPartialAiResponse(Type type, String value, Instant startedAt, long tokenPhaseStart,
        @Nullable String origin) {

    /** A chunk without an owner — the form every producer used before origin existed. */
    public OnPartialAiResponse(Type type, String value, Instant startedAt, long tokenPhaseStart) {
        this(type, value, startedAt, tokenPhaseStart, null);
    }

    /** This chunk with {@code origin} set; the receiver keeps its own value. */
    public OnPartialAiResponse withOrigin(String origin) {
        return new OnPartialAiResponse(type, value, startedAt, tokenPhaseStart, origin);
    }

    public enum Type { START, THINK, ANSWER, TOOL, END }
}
