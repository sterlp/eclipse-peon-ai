package org.sterl.open.aifactory.chat;

import java.util.EnumSet;
import java.util.Set;

import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.open.aifactory.chat.dto.AgentTileDto;
import org.sterl.open.aifactory.chat.dto.AgentTileState;
import org.sterl.open.aifactory.chat.dto.TileCapability;

/**
 * The one place a tile is derived: the state, the context number and the capabilities of a seat. The
 * numbers come from the core and are formatted by the core's own chain
 * ({@link StringUtil#estimateAware} over {@link StringUtil#toK}) so a tile reads exactly like the plugin
 * header (UC-WP-11) — the factory never invents a format and never recomputes a percentage.
 *
 * <p>Pure on purpose: {@link #stateOf} and {@link #of} take plain values, so the precedence and the
 * seat rules are provable without a model and without a time window.</p>
 */
final class AgentTiles {

    /** What a name sits on — the only thing that decides a tile's affordances (D-14). */
    enum Seat {
        /** the project's Jon: context, Clear, Compact — no insight, his conversation is the chat */
        JON,
        /** Plan, Dev, Review: everything, including the insight into their own chat (R-WP-26) */
        MEMBER,
        /** the search sub-loop: a name and a state, nothing else — it owns no memory between calls */
        SUB_LOOP
    }

    private AgentTiles() {
    }

    static Set<TileCapability> capabilitiesOf(Seat seat) {
        return switch (seat) {
            case JON -> EnumSet.of(TileCapability.CONTEXT, TileCapability.CLEAR, TileCapability.COMPACT);
            case MEMBER -> EnumSet.allOf(TileCapability.class);
            case SUB_LOOP -> EnumSet.noneOf(TileCapability.class);
        };
    }

    /**
     * Precedence {@code COMPACTING > WORKING > ERROR > IDLE}. A tile shows <b>this</b> agent's own state,
     * not the plugin's leaf rule („Jon dark while a slave works"): the plugin header has one dot for the
     * whole team, a tile has one agent, and „Jon arbeitet" is the truth while he waits for a delegation.
     * A failure is only shown once the agent is idle again, so a finished turn's error does not hide the
     * next one's state.
     */
    static AgentTileState stateOf(boolean working, boolean compacting, boolean failed) {
        if (compacting) {
            return AgentTileState.COMPACTING;
        }
        if (working) {
            return AgentTileState.WORKING;
        }
        return failed ? AgentTileState.ERROR : AgentTileState.IDLE;
    }

    /**
     * The display value of the context number — {@code null} for a seat without
     * {@link TileCapability#CONTEXT}, which the UI renders as „—". Derived here and never passed in, so a
     * tile cannot carry a number its own capabilities forbid.
     */
    static AgentTileDto of(String name, long tokens, boolean estimate, int percent, long budget,
            boolean working, boolean compacting, boolean failed, Set<TileCapability> capabilities) {
        String context = capabilities.contains(TileCapability.CONTEXT) ? contextOf(tokens, estimate) : null;
        return new AgentTileDto(name, context, tokens, estimate, percent, budget,
                stateOf(working, compacting, failed), capabilities);
    }

    /** The core's display value for an agent — the same two calls the plugin header makes. */
    static String contextOf(long tokens, boolean estimate) {
        return StringUtil.estimateAware(estimate, StringUtil.toK(tokens));
    }
}
