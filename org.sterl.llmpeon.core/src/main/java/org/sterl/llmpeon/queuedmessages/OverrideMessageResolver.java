package org.sterl.llmpeon.queuedmessages;

import java.util.Optional;

import org.sterl.llmpeon.command.CommandService;
import org.sterl.llmpeon.command.SlashCommandResolver;
import org.sterl.llmpeon.skill.SkillService;

/**
 * Decides whether a user message is a {@code !}-override (queued-user-messages rule 8):
 * while the agent is working, a message starting with {@code !} bypasses the queue —
 * the {@code !} is stripped and the remainder becomes ONE text injected directly into
 * the agent's memory, visible to the running turn on its next model call.
 *
 * <p>Pure decision function: no agent, no memory, no queue. The input is expected
 * <b>trimmed</b> (the view trims before calling). A lone {@code !} (blank remainder)
 * is a silent no-op. When the remainder starts with {@code /}, the lookup reuses
 * {@link SlashCommandResolver} — a match replaces the slash prefix with the command/skill
 * body and appends the trailing text (no oneTimeOrder, unlike the normal slash path).
 */
public class OverrideMessageResolver {

    /**
     * @param text the trimmed user input
     * @param agentWorking whether the agent is currently processing a call
     * @param commands the command service for slash lookups
     * @param skills the skill service for slash lookups
     * @return the text to inject into the agent memory, or empty when this is not an
     *         override (no leading {@code !}, agent idle, or a lone {@code !})
     */
    public Optional<String> resolve(String text, boolean agentWorking, CommandService commands, SkillService skills) {
        if (text == null || !agentWorking || !text.startsWith("!")) {
            return Optional.empty();
        }
        String rest = text.substring(1);
        if (rest.isBlank()) {
            return Optional.empty(); // lone "!" = silent no-op
        }
        if (rest.startsWith("/")) {
            var slash = new SlashCommandResolver().resolve(rest, commands, skills);
            if (slash.isPresent()) {
                var r = slash.get();
                return Optional.of(r.trailingText().isBlank() ? r.body() : r.body() + " " + r.trailingText());
            }
        }
        return Optional.of(rest);
    }
}
