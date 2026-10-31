package org.sterl.open.aifactory.chat;

/**
 * The inner event language of a turn: {@link SseTurnMonitor} translates the core's
 * {@code AiMonitor} callbacks into these, {@link ChatStreamRegistry} is the only one who knows what
 * they look like on the wire. Sealed on purpose — the translation table in the monitor has to be
 * complete, and an event nobody maps is a compile error, not a silently missing live line.
 */
public sealed interface ChatEvent {

    /** A streamed delta, {@code kind} is {@code think} or {@code answer}. Live only. */
    record Partial(String kind, String text) implements ChatEvent {
    }

    /** One tool or problem line. Live only — the history never holds these (R-WP-11). */
    record ToolLine(String text) implements ChatEvent {
    }

    /**
     * One turn returned — the queue drain of that turn included, so an input that arrived while Jon was
     * busy is closed by this event too and never by one of its own. {@code ProjectRuntime#submit} answers
     * the turn it started; the monitor has no part in it (ADR-0078 Decision 6).
     */
    record Done(long millis) implements ChatEvent {
    }

    /** The turn failed; the message names the missing field or the error, never a config value. */
    record Failed(String message) implements ChatEvent {
    }
}
