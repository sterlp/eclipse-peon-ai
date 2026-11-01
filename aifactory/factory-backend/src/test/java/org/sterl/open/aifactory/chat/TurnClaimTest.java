package org.sterl.open.aifactory.chat;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;

import org.sterl.open.aifactory.chat.dto.AgentAction;

/**
 * The claim of one project without a model and without a Spring context (BUG-1): a turn and an action
 * answer the same object, so they are never started beside each other — and the price for that is a
 * <b>waiting</b> turn rather than a refused send, because an input the user sent during an action is still
 * wanted after it (R-WP-10, UC-WP-14).
 *
 * <p>Every wait is a deadline on a latch: a block proves itself by <em>not</em> arriving within two seconds
 * and the release by arriving after it. No sleep stands in for either.</p>
 */
class TurnClaimTest {

    @Test
    void aTurnWaitsForTheRunningAction() throws Exception {
        TurnClaim claim = new TurnClaim();
        claim.claimAction("Dev", AgentAction.COMPACT, false);
        CountDownLatch reachedTheCore = new CountDownLatch(1);
        Thread turn = Thread.ofVirtual().start(() -> {
            claim.awaitNoAction();
            reachedTheCore.countDown();
        });

        assertThat(reachedTheCore.await(2, SECONDS))
                .as("the turn task stands in front of the claimed action instead of writing over its memory")
                .isFalse();

        claim.releaseAction();
        assertThat(reachedTheCore.await(5, SECONDS))
                .as("and the release is what lets it go — the input is answered, never dropped")
                .isTrue();
        turn.join(SECONDS.toMillis(5));
    }

    @Test
    void anActionIsRefusedWhileATurnIsInFlight() {
        TurnClaim claim = new TurnClaim();
        assertThat(claim.openTurn())
                .as("the send that finds nothing running opens the turn")
                .isTrue();

        assertThatThrownBy(() -> claim.claimAction("Dev", AgentAction.CLEAR, false))
                .as("a Clear into a running turn is refused instead of queued behind it (UC-WP-12)")
                .isInstanceOf(IllegalStateException.class);
        assertThat(claim.action())
                .as("and the refusal left no claim behind — the tiles cannot light up for nothing")
                .isNull();
    }

    @Test
    void anActionIsRefusedWhileTheTeamWorks() {
        TurnClaim claim = new TurnClaim();

        assertThatThrownBy(() -> claim.claimAction("Dev", AgentAction.COMPACT, true))
                .as("a delegation this object cannot see itself still makes the project busy")
                .isInstanceOf(IllegalStateException.class);
        assertThat(claim.action()).as("and nothing was claimed").isNull();

        assertThatCode(() -> claim.claimAction("Dev", AgentAction.COMPACT, false))
                .as("the same action is taken as soon as nobody works")
                .doesNotThrowAnyException();
        assertThat(claim.action())
                .as("and the tiles read exactly this claim, name and action together")
                .isEqualTo(new TurnClaim.Action("Dev", AgentAction.COMPACT));
    }

    @Test
    void onlyOneSendOpensTheTurn() {
        TurnClaim claim = new TurnClaim();
        assertThat(claim.openTurn()).isTrue();
        assertThat(claim.openTurn())
                .as("a second input is taken and opens nothing: it owns neither the reset nor the end of the turn")
                .isFalse();

        claim.endTurn();
        assertThat(claim.openTurn())
                .as("after the end the next send opens a turn of its own again")
                .isTrue();
    }

    @Test
    void aStopIsTakenOnlyByATurnThatStandsAlone() {
        TurnClaim claim = new TurnClaim();
        assertThat(claim.turnIsStoppable())
                .as("nothing runs, so nothing is stoppable — the edge answers 409")
                .isFalse();

        claim.claimAction("Dev", AgentAction.COMPACT, false);
        claim.openTurn();
        assertThat(claim.turnIsStoppable())
                .as("a send during an action is taken (R-WP-10), but the waiting turn shares that action's "
                        + "one cancel flag — stopping it would cut the Compact in half (D-17)")
                .isFalse();

        claim.releaseAction();
        assertThat(claim.turnIsStoppable())
                .as("the same click is taken as soon as the action has released the project")
                .isTrue();
    }
}
