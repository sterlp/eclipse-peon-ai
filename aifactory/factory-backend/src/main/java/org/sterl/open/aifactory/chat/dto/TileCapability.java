package org.sterl.open.aifactory.chat.dto;

/**
 * What a tile offers — the single source of the affordances, so the UI branches on this set and never
 * on an agent name (R-WP-7, R-WP-26). A set instead of booleans: only three of the eight boolean
 * combinations are legal (Jon has no insight, Search has nothing), and a set gives each rule one place.
 */
public enum TileCapability {
    /** shows the context number (R-WP-7) — absent for a sub-loop, which owns no memory of its own */
    CONTEXT,
    /** offers Clear (UC-WP-12) */
    CLEAR,
    /** offers Compact (UC-WP-13) */
    COMPACT,
    /** opens the read-only insight into this agent's own chat (R-WP-26, Inc 5c) */
    TRANSCRIPT
}
