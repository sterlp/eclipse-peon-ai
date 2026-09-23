package org.sterl.llmpeon.agent;

/**
 * Outcome of an {@link AiAgent#compact(AiMonitor)} call. The two legacy {@code false} cases are
 * fundamentally different and are no longer reported as the same thing (R-CC-3, ADR-0055).
 */
public enum CompactResult {

    /** The compact succeeded — the memory is already reset and re-seeded with the summary. */
    COMPACTED,

    /**
     * The memory is too small to compact (fewer than 3 messages). Legitimate and not an error —
     * nothing to do, the memory stays untouched.
     */
    SKIPPED_SMALL,

    /**
     * The compressor returned no usable summary. An error — the memory is untouched and the
     * caller reports it via {@code onProblem}.
     */
    FAILED_EMPTY
}
