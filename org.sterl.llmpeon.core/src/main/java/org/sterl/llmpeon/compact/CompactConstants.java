package org.sterl.llmpeon.compact;

/**
 * Named markers of the compact lifecycle (ADR-0056.4) — single home, no scattered literals.
 */
public final class CompactConstants {

    /**
     * Marker of the re-inserted resume UserMessage after a compact (R-CC-9). Re-inserted
     * messages are STATE: never "real user text" for the stage-1 reduction, never a re-trigger
     * for a new compact.
     */
    public static final String REINSERT_MARKER = "Session compacted:";

    private CompactConstants() {}
}
