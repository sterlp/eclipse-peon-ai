package org.sterl.open.aifactory.config;

import org.sterl.llmpeon.provider.ThinkSupport;

/**
 * The think input a provider's own form allows (R-WP-23, UC-WP-67): the core's sealed
 * {@link ThinkSupport} named for the wire, so the page builds a control instead of a text box.
 *
 * <p>{@link #UNKNOWN} is the core's own honest answer for a provider whose form is not known <em>yet</em>
 * — it is not the claim "no think input", which is {@link #NONE}. The five names are the wire contract;
 * the field values stay verbatim either way (ADR-0064).</p>
 */
public enum ThinkForm {

    /** unset / `true` / `false`. */
    TOGGLE,
    /** one of {@code thinkValues}. */
    VALUES,
    /** any text the provider passes on. */
    FREE,
    /** the provider has no per-request think input. */
    NONE,
    /** foreign or new provider — the form is not known yet. */
    UNKNOWN;

    /** The form of one core answer. A new core form has to be named here, not arrive as free text. */
    public static ThinkForm of(ThinkSupport support) {
        return switch (support) {
            case ThinkSupport.Toggle ignored -> TOGGLE;
            case ThinkSupport.Values ignored -> VALUES;
            case ThinkSupport.FreeString ignored -> FREE;
            case ThinkSupport.None ignored -> NONE;
            case ThinkSupport.Unknown ignored -> UNKNOWN;
        };
    }
}
