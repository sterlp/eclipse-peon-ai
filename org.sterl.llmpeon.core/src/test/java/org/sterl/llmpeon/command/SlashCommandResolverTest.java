package org.sterl.llmpeon.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.AbstractMemoryFileTest;
import org.sterl.llmpeon.prompt.model.SimplePromptFile;
import org.sterl.llmpeon.skill.SkillPromptFile;
import org.sterl.llmpeon.skill.SkillService;

class SlashCommandResolverTest extends AbstractMemoryFileTest {

    private final SlashCommandResolver resolver = new SlashCommandResolver();

    @Test
    void commandWithTrailingText() throws Exception {
        // GIVEN
        var cmdDir = fs.getPath("/" + UUID.randomUUID());
        Files.createDirectory(cmdDir);
        Files.writeString(cmdDir.resolve("review.md"), "Review the code");
        var commands = new CommandService();
        commands.refresh(cmdDir);
        var skills = new SkillService();

        // WHEN
        var result = resolver.resolve("/review fix the bug", commands, skills);

        // THEN
        assertThat(result).isPresent();
        var r = result.get();
        assertThat(r.name()).isEqualTo("review");
        assertThat(r.isSkill()).isFalse();
        assertThat(r.body()).isEqualTo("Review the code");
        assertThat(r.trailingText()).isEqualTo("fix the bug");
    }

    @Test
    void commandWithSpaceInName() throws Exception {
        // GIVEN
        var cmdDir = fs.getPath("/" + UUID.randomUUID());
        Files.createDirectory(cmdDir);
        Files.writeString(cmdDir.resolve("code review.md"), "Review code thoroughly");
        var commands = new CommandService();
        commands.refresh(cmdDir);
        var skills = new SkillService();

        // WHEN
        var result = resolver.resolve("/code review fix the bug", commands, skills);

        // THEN
        assertThat(result).isPresent();
        var r = result.get();
        assertThat(r.name()).isEqualTo("code review");
        assertThat(r.isSkill()).isFalse();
        assertThat(r.trailingText()).isEqualTo("fix the bug");
    }

    @Test
    void commandOnlyNoTrailingText() throws Exception {
        // GIVEN
        var cmdDir = fs.getPath("/" + UUID.randomUUID());
        Files.createDirectory(cmdDir);
        Files.writeString(cmdDir.resolve("review.md"), "Review the code");
        var commands = new CommandService();
        commands.refresh(cmdDir);
        var skills = new SkillService();

        // WHEN
        var result = resolver.resolve("/review", commands, skills);

        // THEN
        assertThat(result).isPresent();
        var r = result.get();
        assertThat(r.name()).isEqualTo("review");
        assertThat(r.trailingText()).isEmpty();
    }

    @Test
    void skillWithTrailingText() throws Exception {
        // GIVEN
        var skillDir = fs.getPath("/" + UUID.randomUUID());
        Files.createDirectory(skillDir);
        Files.writeString(skillDir.resolve("my-skill.md"), """
                ---
                name: my-skill
                ---
                Skill body content
                """);
        var commands = new CommandService();
        var skills = new SkillService();
        skills.refresh(skillDir);

        // WHEN
        var result = resolver.resolve("/my-skill do something", commands, skills);

        // THEN
        assertThat(result).isPresent();
        var r = result.get();
        assertThat(r.name()).isEqualTo("my-skill");
        assertThat(r.isSkill()).isTrue();
        assertThat(r.body()).contains("Skill body content");
        assertThat(r.body()).contains("Execute this skill on the following instruction");
        assertThat(r.trailingText()).isEqualTo("do something");
    }

    @Test
    void unknownCommandReturnsEmpty() throws Exception {
        // GIVEN
        var commands = new CommandService();
        var skills = new SkillService();

        // WHEN
        var result = resolver.resolve("/unknown help", commands, skills);

        // THEN
        assertThat(result).isEmpty();
    }

    @Test
    void longestPrefixWins() throws Exception {
        // GIVEN
        var cmdDir = fs.getPath("/" + UUID.randomUUID());
        Files.createDirectory(cmdDir);
        Files.writeString(cmdDir.resolve("code.md"), "Code command");
        Files.writeString(cmdDir.resolve("code review.md"), "Code review command");
        var commands = new CommandService();
        commands.refresh(cmdDir);
        var skills = new SkillService();

        // WHEN
        var result = resolver.resolve("/code review x", commands, skills);

        // THEN
        assertThat(result).isPresent();
        var r = result.get();
        assertThat(r.name()).isEqualTo("code review");
        assertThat(r.body()).isEqualTo("Code review command");
        assertThat(r.trailingText()).isEqualTo("x");
    }

    @Test
    void nonSlashInputReturnsEmpty() throws Exception {
        // GIVEN
        var commands = new CommandService();
        var skills = new SkillService();

        // WHEN
        var result = resolver.resolve("hello world", commands, skills);

        // THEN
        assertThat(result).isEmpty();
    }

    @Test
    void nullInputReturnsEmpty() throws Exception {
        // GIVEN
        var commands = new CommandService();
        var skills = new SkillService();

        // WHEN
        var result = resolver.resolve(null, commands, skills);

        // THEN
        assertThat(result).isEmpty();
    }

    @Test
    void caseInsensitiveLookup() throws Exception {
        // GIVEN
        var cmdDir = fs.getPath("/" + UUID.randomUUID());
        Files.createDirectory(cmdDir);
        Files.writeString(cmdDir.resolve("Review.md"), "Review body");
        var commands = new CommandService();
        commands.refresh(cmdDir);
        var skills = new SkillService();

        // WHEN
        var result = resolver.resolve("/REVIEW fix", commands, skills);

        // THEN
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Review");
    }

    @Test
    void commandTakesPrecedenceOverSkill() throws Exception {
        // GIVEN
        var cmdDir = fs.getPath("/" + UUID.randomUUID());
        Files.createDirectory(cmdDir);
        Files.writeString(cmdDir.resolve("test.md"), "Command body");

        var skillDir = fs.getPath("/" + UUID.randomUUID());
        Files.createDirectory(skillDir);
        Files.writeString(skillDir.resolve("test.md"), """
                ---
                name: test
                ---
                Skill body
                """);

        var commands = new CommandService();
        commands.refresh(cmdDir);
        var skills = new SkillService();
        skills.refresh(skillDir);

        // WHEN
        var result = resolver.resolve("/test do", commands, skills);

        // THEN
        assertThat(result).isPresent();
        var r = result.get();
        assertThat(r.isSkill()).isFalse();
        assertThat(r.body()).isEqualTo("Command body");
    }
}
