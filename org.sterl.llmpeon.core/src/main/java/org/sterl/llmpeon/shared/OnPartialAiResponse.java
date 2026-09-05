package org.sterl.llmpeon.shared;

import java.time.Instant;

/**
 * A single streaming chunk delivered to the UI, in order.
 *
 * @param type            the kind of chunk
 * @param value           the text payload (null for START/END); for TOOL chunks the argument delta, not the tool name
 * @param startedAt       the turn start — identical for every chunk of the turn
 * @param tokenPhaseStart epoch millis of the first partial of the current call, or {@code 0} while the token phase has not started yet
 */
public record OnPartialAiResponse(Type type, String value, Instant startedAt, long tokenPhaseStart) {

    public enum Type { START, THINK, ANSWER, TOOL, END }
}
