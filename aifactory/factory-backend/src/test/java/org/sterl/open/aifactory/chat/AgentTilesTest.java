package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.open.aifactory.chat.AgentTiles.Seat;
import org.sterl.open.aifactory.chat.dto.AgentTileDto;
import org.sterl.open.aifactory.chat.dto.AgentTileState;
import org.sterl.open.aifactory.chat.dto.TileCapability;

/**
 * The tile rules are pure functions on purpose (R-WP-7): UC-WP-11's display rules and UC-WP-13's own
 * Compact state are then provable without a model and without a time window. A live snapshot that races a
 * poll proves nothing about a state that lasts milliseconds — so the precedence, the seat rules and the
 * context format are proven here, and {@code AgentActionTest} only proves what outlives a call.
 */
class AgentTilesTest {

    @Test
    // UC-WP-11
    void everyTileCarriesThePluginContextNumber() {
        AgentTileDto tile = AgentTiles.of("Dev", 12_345L, false, 12, 100_000L, false, false, false,
                AgentTiles.capabilitiesOf(Seat.MEMBER));

        assertThat(tile.context())
                .as("the tile shows the plugin header's own number: the core formats it, the factory"
                        + " invents no second format")
                .isEqualTo(StringUtil.estimateAware(false, StringUtil.toK(12_345L)));
        assertThat(tile.tokens()).isEqualTo(12_345L);
        assertThat(tile.contextPercent())
                .as("the percentage is the core's too — against the auto-compact budget, not a guess")
                .isEqualTo(12);
        assertThat(tile.budget()).isEqualTo(100_000L);
        assertThat(tile.state()).isEqualTo(AgentTileState.IDLE);

        assertThat(AgentTiles.of("Jon", 4_200L, true, 4, 100_000L, false, false, false,
                AgentTiles.capabilitiesOf(Seat.JON)).context())
                .as("an estimated value stays marked as one — a tile never shows a guess as a count")
                .isEqualTo("~4k (estimate)");
    }

    @Test
    // UC-WP-11
    void errorOnlyWhileIdle() {
        assertThat(AgentTiles.stateOf(true, false, true))
                .as("while an agent works, his tile shows the work — the failure of the turn before must"
                        + " not hide the state of this one")
                .isEqualTo(AgentTileState.WORKING);
        assertThat(AgentTiles.stateOf(false, false, true))
                .as("once he is idle again, the failed turn is what the user is told")
                .isEqualTo(AgentTileState.ERROR);
        assertThat(AgentTiles.stateOf(false, false, false)).isEqualTo(AgentTileState.IDLE);
    }

    @Test
    // UC-WP-13
    void compactingWinsOverWorking() {
        assertThat(AgentTiles.stateOf(true, true, true))
                .as("a Compact is the fact worth seeing — plugin parity, and UC-WP-13 asks for the"
                        + " Compact as the tile's own state")
                .isEqualTo(AgentTileState.COMPACTING);
    }

    @Test
    // UC-WP-11
    void searchTileCarriesNothingButNameAndState() {
        AgentTileDto search = AgentTiles.of("Search", 0L, false, 0, 0L, true, false, false,
                AgentTiles.capabilitiesOf(Seat.SUB_LOOP));

        assertThat(search.capabilities())
                .as("a sub-loop owns no memory between calls, so it offers neither Clear nor Compact")
                .isEmpty();
        assertThat(search.context())
                .as("no core value exists for a sub-loop — the UI shows a dash instead of a made-up number")
                .isNull();
        assertThat(search.state())
                .as("his live lines are the only proof he runs, and that proof is enough for a state")
                .isEqualTo(AgentTileState.WORKING);
    }

    @Test
    // UC-WP-11
    void onlyMembersOpenTheInsight() {
        assertThat(AgentTiles.capabilitiesOf(Seat.MEMBER))
                .as("Plan, Dev and Review carry everything, the insight included (R-WP-26)")
                .containsExactlyInAnyOrder(TileCapability.values());
        assertThat(AgentTiles.capabilitiesOf(Seat.JON))
                .as("Jon's conversation is the chat itself — his tile has no second way into it")
                .containsExactlyInAnyOrder(TileCapability.CONTEXT, TileCapability.CLEAR, TileCapability.COMPACT);
        assertThat(AgentTiles.capabilitiesOf(Seat.SUB_LOOP)).isEmpty();

        assertThat(AgentTiles.of("Jon", 4_000L, false, 4, 100_000L, false, false, false,
                AgentTiles.capabilitiesOf(Seat.JON)).capabilities())
                .as("a tile cannot offer more than its seat offers — the UI reads this set and nothing else")
                .doesNotContain(TileCapability.TRANSCRIPT);
    }
}
