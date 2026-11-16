package org.sterl.llmpeon.agent.model;

import java.time.Instant;
import java.util.Objects;

/**
 * An entry of the user message queue (queued-user-messages Regel 13). {@code urgent} names the
 * lane only; an urgent entry never carries attachments, they would reach the model via a result.
 */
public record QueuedInput(UserInput input, Instant queuedAt, boolean urgent) {

    public QueuedInput {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(queuedAt, "queuedAt");
        if (urgent && !input.attachments().isEmpty()) {
            throw new IllegalArgumentException("an urgent queue entry cannot carry attachments");
        }
    }
}
