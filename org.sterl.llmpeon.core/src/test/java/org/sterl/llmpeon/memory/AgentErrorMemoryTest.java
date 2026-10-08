package org.sterl.llmpeon.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.RateLimitException;

/**
 * R-AEM-1/2 (docs/agent-error-memory.md): a terminal API error lands in the agent's memory
 * exactly once; a cancellation never inserts.
 */
class AgentErrorMemoryTest {

    // UC-AEM-1
    @Test
    void terminalErrorLandsInMemory() {
        // GIVEN a fresh agent memory
        var memory = new ThreadSafeMemory();

        // WHEN a terminal error (after ApiRetry exhaustion) is recorded
        AgentErrorMemory.recordIfTerminal(memory, new RuntimeException("boom"));

        // THEN a compact UserMessage with the error's first line is in the memory
        var copy = memory.getCopy();
        assertThat(copy).hasSize(1);
        assertThat(copy.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) copy.get(0)).singleText()).isEqualTo("API error: boom");
        // ... and only the FIRST line is carried — no second line, no stack trace
        assertThat(AgentErrorMemory.messageFor(new RuntimeException("boom\nsecond line")))
                .isEqualTo("API error: boom");
    }

    // UC-AEM-2
    @Test
    void cancelNeverInserts() {
        // GIVEN a fresh agent memory
        var memory = new ThreadSafeMemory();

        // WHEN a cancellation is recorded — direct and wrapped as a cause
        AgentErrorMemory.recordIfTerminal(memory, new CancellationException());
        AgentErrorMemory.recordIfTerminal(memory, new RuntimeException("outer", new CancellationException()));

        // THEN the memory stays untouched
        assertThat(memory.getCopy()).isEmpty();
    }

    // UC-AEM-3
    @Test
    void duplicateErrorInsertedOnce() {
        // GIVEN a fresh agent memory
        var memory = new ThreadSafeMemory();

        // WHEN the same error is recorded twice
        AgentErrorMemory.recordIfTerminal(memory, new RuntimeException("boom"));
        AgentErrorMemory.recordIfTerminal(memory, new RuntimeException("boom"));

        // THEN exactly one memory line (containsMessage dedup)
        var copy = memory.getCopy();
        assertThat(copy).hasSize(1);
        assertThat(((UserMessage) copy.get(0)).singleText()).isEqualTo("API error: boom");
    }

    @Test
    void rateLimitExhaustedIsTerminal() {
        // GIVEN a fresh agent memory
        var memory = new ThreadSafeMemory();

        // WHEN a rate-limit error after retry exhaustion is recorded
        AgentErrorMemory.recordIfTerminal(memory, new RateLimitException("rate limited"));

        // THEN it is inserted (PO decision: exhausted retry = terminal API error)
        var copy = memory.getCopy();
        assertThat(copy).hasSize(1);
        assertThat(((UserMessage) copy.get(0)).singleText()).isEqualTo("API error: rate limited");
    }

    @Test
    void noMessageFallsBackToClassName() {
        // GIVEN an exception without a message
        var exception = new IllegalStateException();

        // WHEN the message is derived
        var message = AgentErrorMemory.messageFor(exception);

        // THEN the simple class name is used — no null, no "null"
        assertThat(message).isEqualTo("API error: IllegalStateException");
    }
}
