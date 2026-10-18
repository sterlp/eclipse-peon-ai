package org.sterl.llmpeon.queuedmessages;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.AbstractMemoryFileTest;
import org.sterl.llmpeon.command.CommandService;
import org.sterl.llmpeon.skill.SkillService;

class OverrideMessageResolverTest extends AbstractMemoryFileTest {

    private final OverrideMessageResolver resolver = new OverrideMessageResolver();

    private CommandService commandsWith(String fileName, String body) throws Exception {
        var cmdDir = fs.getPath("/" + UUID.randomUUID());
        Files.createDirectory(cmdDir);
        Files.writeString(cmdDir.resolve(fileName), body);
        var commands = new CommandService();
        commands.refresh(cmdDir);
        return commands;
    }

    private SkillService skillsWith(String fileName, String content) throws Exception {
        var skillDir = fs.getPath("/" + UUID.randomUUID());
        Files.createDirectory(skillDir);
        Files.writeString(skillDir.resolve(fileName), content);
        var skills = new SkillService();
        skills.refresh(skillDir);
        return skills;
    }

    @Test
    void working_exclamation_stripsBang_returnsText() throws Exception {
        // GIVEN — agent working, plain !-message
        // WHEN
        var result = resolver.resolve("!bitte abbrechen", true, new CommandService(), new SkillService());
        // THEN — the ! is stripped, the text goes to the memory as-is
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("bitte abbrechen");
    }

    @Test
    void working_slashCommand_bodyReplacesPrefix() throws Exception {
        // GIVEN — agent working, command with body "instruction content"
        var commands = commandsWith("my-command.md", "instruction content");
        // WHEN
        var result = resolver.resolve("!/my-command rest text", true, commands, new SkillService());
        // THEN — the slash prefix is replaced by the body, the trailing text appended — one text
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("instruction content rest text");
    }

    @Test
    void idle_exclamation_returnsEmpty() throws Exception {
        // GIVEN — agent idle: the !-rule does not apply (normal turn with the text as typed)
        // WHEN
        var result = resolver.resolve("!hello", false, new CommandService(), new SkillService());
        // THEN — no override, the normal path handles the message
        assertThat(result).isEmpty();
    }

    @Test
    void working_slashNotFound_keepsRawText() throws Exception {
        // GIVEN — agent working, unknown slash command
        var commands = commandsWith("review.md", "Review the code");
        // WHEN
        var result = resolver.resolve("!/nichtexistent rest", true, commands, new SkillService());
        // THEN — not found: the raw text (bang stripped) goes to the memory
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("/nichtexistent rest");
    }

    @Test
    void working_slashSkill_bodyReplacesPrefix() throws Exception {
        // GIVEN — agent working, a skill exists
        var skills = skillsWith("my-skill.md", """
                ---
                name: my-skill
                ---
                Skill body content
                """);
        // WHEN
        var result = resolver.resolve("!/my-skill do it", true, new CommandService(), skills);
        // THEN — the skill body (with its execution suffix) replaces the prefix, trailing appended
        assertThat(result).isPresent();
        assertThat(result.get()).startsWith("Skill body content");
        assertThat(result.get()).endsWith(" do it");
        assertThat(result.get()).doesNotContain("/my-skill");
    }

    @Test
    void working_doubleBang_keepsRawRest() throws Exception {
        // GIVEN — agent working, double bang: the remainder starts with !, not /
        // WHEN
        var result = resolver.resolve("!!/cmd x", true, new CommandService(), new SkillService());
        // THEN — no slash resolution, the raw remainder goes to the memory
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("!/cmd x");
    }

    @Test
    void working_bangOnly_blankRest_returnsEmpty() throws Exception {
        // GIVEN — agent working, a lone ! (no payload)
        // WHEN
        var result = resolver.resolve("!", true, new CommandService(), new SkillService());
        // THEN — silent no-op: nothing to inject, no queue entry, no turn
        assertThat(result).isEmpty();
    }

    @Test
    void working_slashCommand_noTrailing_bodyOnly() throws Exception {
        // GIVEN — agent working, slash command without trailing text
        var commands = commandsWith("review.md", "Review the code");
        // WHEN
        var result = resolver.resolve("!/review", true, commands, new SkillService());
        // THEN — the body alone goes to the memory
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("Review the code");
    }

    @Test
    void noExclamation_returnsEmpty() throws Exception {
        // GIVEN — agent working, message without a leading !
        // WHEN
        var result = resolver.resolve("hello", true, new CommandService(), new SkillService());
        // THEN — no override
        assertThat(result).isEmpty();
    }

    @Test
    void nullText_returnsEmpty() throws Exception {
        // GIVEN — null input
        // WHEN
        var result = resolver.resolve(null, true, new CommandService(), new SkillService());
        // THEN — no override
        assertThat(result).isEmpty();
    }
}
