package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;

/**
 * The one rule of a stopped turn, without a turn: a cancel is the user's own end and must not be reported
 * as a failure (UC-WP-50, D-16).
 *
 * <p>Pure on purpose. The core cancels a model stream that no test can interrupt at a chosen moment —
 * {@code MockLlmServer} writes a streamed answer in one piece — while it reports every cancel as this one
 * exception type, so the type test is the part that can be pinned without a race.</p>
 */
class TurnOutcomeTest {

    @Test
    // UC-WP-50
    void aCancelIsNotAFailure() {
        assertThat(TurnOutcome.of(new CancellationException("stream cancelled")))
                .as("the user stopped this turn himself — that is his end, not an error")
                .isEqualTo(TurnOutcome.CANCELED);
        assertThat(TurnOutcome.of(new CompletionException(new CancellationException("cancelled"))))
                .as("a cancel arriving through a future keeps being a cancel")
                .isEqualTo(TurnOutcome.CANCELED);
        assertThat(TurnOutcome.of(new ExecutionException(new IllegalStateException(
                new CancellationException("nested cancel")))))
                .as("and it is found at any depth of the cause chain")
                .isEqualTo(TurnOutcome.CANCELED);
    }

    @Test
    // UC-WP-50
    void anythingElseIsAFailure() {
        assertThat(TurnOutcome.of(new IllegalStateException("model gone")))
                .as("a plain IllegalStateException is exactly what a stop is not")
                .isEqualTo(TurnOutcome.FAILED);
        assertThat(TurnOutcome.of(new RuntimeException("boom")))
                .isEqualTo(TurnOutcome.FAILED);
    }
}
