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

    /** One answered input — {@code AbstractAgent.doCall} fires it per input, not per {@code call(…)}. */
    record Done(long millis) implements ChatEvent {
    }

    /** The turn failed; the message names the missing field or the error, never a config value. */
    record Failed(String message) implements ChatEvent {
    }
}
