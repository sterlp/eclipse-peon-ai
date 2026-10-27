package org.sterl.llmpeon.tool.model;

import org.jspecify.annotations.Nullable;

/**
 * A line for the chat.
 *
 * @param origin the core id of the agent that produced this line, or {@code null} when no agent owns it
 *               (a user line, a line emitted outside an agent turn). The emitter sets it; nobody
 *               downstream is meant to guess it. A viewer decides presentation and visibility, the
 *               core does not filter.
 */
public record SimpleMessage(Type role, String message, @Nullable String origin) {

    /** A line without an owner — valid for every producer that has no agent context. */
    public SimpleMessage(Type role, String message) {
        this(role, message, null);
    }

    /** This line with {@code origin} set; the receiver keeps its own value. */
    public SimpleMessage withOrigin(String origin) {
        return new SimpleMessage(role, message, origin);
    }

    public enum Type {
        AI,
        USER,
        PROBLEM,
        TOOL,
        THINK,
        QUESTION,
        DIFF,
    }
}
