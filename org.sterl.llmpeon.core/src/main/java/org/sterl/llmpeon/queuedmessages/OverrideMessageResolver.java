package org.sterl.llmpeon.queuedmessages;

import java.util.Optional;

import org.sterl.llmpeon.command.CommandService;
import org.sterl.llmpeon.command.SlashCommandResolver;
import org.sterl.llmpeon.skill.SkillService;

/**
 * Decides whether a user message is a {@code !}-override (queued-user-messages rule 8):
 * while the agent is working, a message starting with {@code !} bypasses the normal lane —
 * the {@code !} is stripped and the remainder becomes ONE urgent queue entry.
 *
 * <p><b>Present semantics:</b> {@code isPresent()} means the input is <em>consumed as an
 * override</em> — the view must clear the input and skip the normal send path. The
 * {@link OverrideCommand#text()} is what gets queued: non-blank for a real override,
 * blank for a lone {@code !} (consumed, nothing queued — a silent no-op).
 * {@code isEmpty()} means this is not an override (no leading {@code !}, agent idle,
 * null input) — the normal send path handles the message.
 *
 * <p>Pure decision function: no agent, no memory, no queue. The input is expected
 * <b>trimmed</b> (the view trims before calling). When the remainder starts with
 * {@code /}, the lookup reuses {@link SlashCommandResolver} — a match replaces the
 * slash prefix with the command/skill body and appends the trailing text (no
 * oneTimeOrder, unlike the normal slash path).
 */
public class OverrideMessageResolver {

    /** The resolved override: the text to queue as an urgent entry (blank = consumed, nothing queued). */
    public record OverrideCommand(String text) {}

    /**
     * @param text the trimmed user input
     * @param agentWorking whether the agent is currently processing a call
     * @param commands the command service for slash lookups
     * @param skills the skill service for slash lookups
     * @return present when the input is consumed as an override (see the class-level
     *         present semantics); empty when the normal send path handles the message
     */
    public Optional<OverrideCommand> resolve(String text, boolean agentWorking, CommandService commands, SkillService skills) {
        if (text == null || !agentWorking || !text.startsWith("!")) {
            return Optional.empty();
        }
        String rest = text.substring(1);
        if (rest.isBlank()) {
            return Optional.of(new OverrideCommand("")); // lone "!" = consumed, nothing injected
        }
        if (rest.startsWith("/")) {
            var slash = new SlashCommandResolver().resolve(rest, commands, skills);
            if (slash.isPresent()) {
                var r = slash.get();
                var resolved = r.trailingText().isBlank() ? r.body() : r.body() + " " + r.trailingText();
                return Optional.of(new OverrideCommand(resolved));
            }
        }
        return Optional.of(new OverrideCommand(rest));
    }
}
