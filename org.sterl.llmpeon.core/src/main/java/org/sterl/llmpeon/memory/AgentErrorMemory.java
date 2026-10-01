package org.sterl.llmpeon.memory;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.exception.ExceptionUtil;

import dev.langchain4j.data.message.UserMessage;

/**
 * R-AEM-1/2: a terminal API error (ApiRetry exhausted — the exception that reached the UI)
 * lands in the agent's memory ONCE as a compact UserMessage, so the next turn sees what killed
 * the previous one. A cancellation is never an error (no insertion), and the containsMessage
 * guard keeps the error to a single line per memory (same dedup pattern as the compact hint).
 */
public final class AgentErrorMemory {

    private AgentErrorMemory() {}

    /**
     * {@code "API error: <first line of the message>"} — compact: one line, no stack trace.
     * A null/empty message falls back to the exception's simple class name (never "null").
     */
    public static String messageFor(Exception e) {
        var message = e.getMessage();
        var firstLine = message == null ? "" : message.trim().split("\\R", 2)[0].trim();
        return "API error: " + (firstLine.isEmpty() ? e.getClass().getSimpleName() : firstLine);
    }

    /**
     * Records the error in the given memory when it is terminal: not a cancellation and not
     * already present. Null-safe on both arguments.
     */
    public static void recordIfTerminal(@Nullable ThreadSafeMemory memory, @Nullable Exception e) {
        if (memory == null || e == null) return;
        if (ExceptionUtil.isCanceled(e)) return;
        var message = messageFor(e);
        if (memory.containsMessage(message)) return;
        memory.add(UserMessage.from(message));
    }
}
