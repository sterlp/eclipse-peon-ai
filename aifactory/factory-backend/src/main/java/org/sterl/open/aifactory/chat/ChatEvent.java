package org.sterl.open.aifactory.chat;

import java.util.List;

import org.sterl.open.aifactory.chat.dto.AgentTileDto;

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

    /**
     * One tool line of a turn — a step that worked. Live only, the history never holds these (R-WP-11).
     * A report about a tool that <b>refused</b> is not here: {@link Problem} is its own event (R-WP-61).
     *
     * <p>{@code origin} is the core's stamp of the agent that produced the line and is null where the core
     * names none: a line it passes unstamped, or one the factory itself wrote whose text already names its
     * agent. The browser shows a step's name only where one stands on the wire — it invents none
     * (R-WP-37, UC-WP-72).</p>
     */
    record ToolLine(String text, String origin) implements ChatEvent {
    }

    /**
     * One problem line: the core's own report that a tool refused or failed
     * ({@code SmartToolExecutor.reportProblem}), drawn as its own timeline entry and never as a step
     * (R-WP-61, UC-WP-98). Live only, like a tool line — the history keeps the tool's own answer instead
     * (R-WP-47).
     *
     * <p>{@code origin} follows the same rule as a step's: the core's stamp where it named a producer,
     * {@code null} where it named none, and the browser then names nobody (R-WP-37).</p>
     */
    record Problem(String text, String origin) implements ChatEvent {
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

    /**
     * The team's tiles as one snapshot — the whole roster every time, never a delta, so the UI replaces
     * its list instead of accumulating (R-WP-7). Live only: like every other event it is not stored, and
     * a reconnect reads the roster again over {@code GET …/agents}.
     */
    record Agents(List<AgentTileDto> tiles) implements ChatEvent {
    }

    /**
     * One file a turn changed: the unified diff of that file (R-WP-36). Live only — like a tool line it
     * never enters the history, so a reload loses it (R-WP-11). Carries no origin: {@code AiFileUpdate}
     * has none, and every producer of the project writes into the same chat (UC-WP-68).
     */
    record Diff(String file, String diff) implements ChatEvent {
    }

    /**
     * One delegation of a turn, typed: {@code working} says whether it began or ended, {@code member} names
     * the team member it went to in both cases. A timeline entry and not a voice — the member's own words
     * stay a history line (R-WP-4).
     *
     * <p>No order text, on purpose: the core's two listeners carry {@code (CallStats, AiAgent)} and no
     * prompt ({@code PoDelegateTool.startListener/endListener}), so the task keeps the wording of the tool
     * line above it and this class parses nothing out of anybody's text (R-WP-40, UC-WP-72).</p>
     */
    record Delegation(String member, boolean working) implements ChatEvent {
    }

}
