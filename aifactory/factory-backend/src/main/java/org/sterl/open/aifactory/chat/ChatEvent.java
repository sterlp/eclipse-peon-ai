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
     * The <b>final</b> answer a team member handed back to Jon — the one member line the wire may show,
     * and it is named because the conversation belongs to Jon and this text does not (R-WP-4, UC-WP-39).
     *
     * <p>It comes through its own core callback and not through the {@code SseTurnMonitor} filter: a
     * member's answer is not a line of the conversation, and its thinking and its tool lines are
     * filtered away — so without this event no channel could carry it live. The same text is in the
     * history, which is why the view shows it once: reloading replaces the live lines (one truth, two
     * channels).</p>
     */
    record Delegated(String origin, String text) implements ChatEvent {
    }

    /** One answered input — {@code AbstractAgent.doCall} fires it per input, not per {@code call(…)}. */
    record Done(long millis) implements ChatEvent {
    }

    /** The turn failed; the message names the missing field or the error, never a config value. */
    record Failed(String message) implements ChatEvent {
    }
}
