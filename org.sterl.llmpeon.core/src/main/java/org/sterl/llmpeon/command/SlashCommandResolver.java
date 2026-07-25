package org.sterl.llmpeon.command;

import java.util.Locale;
import java.util.Optional;

import org.sterl.llmpeon.skill.SkillService;

/**
 * Resolves slash-prefixed input into a command or skill using longest-prefix matching.
 *
 * <p>Supports commands with spaces (e.g. {@code /code review fix this} matches command
 * "code review" with trailing "fix this"). Case-insensitive lookup.</p>
 */
public class SlashCommandResolver {

    public record SlashResult(String name, boolean isSkill, String body, String trailingText) {}

    /**
     * Resolves the raw input against commands and skills.
     *
     * @param raw the raw user input
     * @param commands the command service
     * @param skills the skill service
     * @return a result if a command or skill matched, empty otherwise
     */
    public Optional<SlashResult> resolve(String raw, CommandService commands, SkillService skills) {
        if (raw == null || !raw.stripLeading().startsWith("/")) {
            return Optional.empty();
        }

        String afterSlash = raw.stripLeading().substring(1);

        // Longest-prefix match: try from the full string down to the first character
        for (int end = afterSlash.length(); end > 0; end--) {
            char ch = afterSlash.charAt(end - 1);

            // Only stop at word boundaries (whitespace) or at the very end of the string
            if (!Character.isWhitespace(ch) && end < afterSlash.length()) {
                continue;
            }

            String candidate = afterSlash.substring(0, end).stripTrailing();
            if (candidate.isBlank()) {
                continue;
            }

            // Try command first
            var cmd = commands.get(candidate);
            if (cmd.isPresent()) {
                String trailing = afterSlash.substring(end).stripLeading();
                return Optional.of(new SlashResult(cmd.get().getName(), false, cmd.get().getBody(), trailing));
            }

            // Then try skill
            var skill = skills.get(candidate);
            if (skill.isPresent()) {
                String trailing = afterSlash.substring(end).stripLeading();
                String body = skill.get().getBody()
                        + "\n\nExecute this skill on the following instruction - full body was loaded.";
                return Optional.of(new SlashResult(skill.get().getName(), true, body, trailing));
            }
        }

        return Optional.empty();
    }
}
