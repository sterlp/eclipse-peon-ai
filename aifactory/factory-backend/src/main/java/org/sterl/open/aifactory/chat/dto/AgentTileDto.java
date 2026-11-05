package org.sterl.open.aifactory.chat.dto;

import java.util.Set;

/**
 * One agent's tile — a snapshot of the core's live state, never a history and never a second copy of
 * a conversation (R-WP-11: nothing here survives a restart).
 *
 * @param name           the core's own agent name (ADR-0079), Search is this factory's label for its
 *                       search sub-loop
 * @param context        the context number formatted by the core, identical to the plugin header;
 *                       {@code null} exactly when {@code capabilities} lacks {@code CONTEXT}
 * @param tokens         the raw number behind {@code context}, for a bar or tooltip
 * @param estimate       whether {@code tokens} is an estimate — honest display, not a rounding hint
 * @param contextPercent the core's percentage against the compact budget
 * @param budget         that budget ({@code autoCompactAfter}), shown beside the percentage
 * @param state          live state, {@code COMPACTING} winning over {@code WORKING}
 * @param capabilities   the affordances — see {@link TileCapability}
 */
public record AgentTileDto(String name, String context, long tokens, boolean estimate, int contextPercent,
        long budget, AgentTileState state, Set<TileCapability> capabilities) {
}
