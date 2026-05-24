package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PromptYmlParserTest {

    @Test
    void parseCommand_plainFileNoFrontmatter(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("review.md"), "Review the code.");

        // WHEN
        var cmd = PromptYmlParser.parseCommand(tmp.resolve("review.md"));

        // THEN
        assertThat(cmd).isNotNull();
        assertThat(cmd.name()).isEqualTo("review");
        assertThat(cmd.description()).isNull();
    }

    @Test
    void parseCommand_withDescription(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("review.md"), """
                ---
                description: Review changes
                ---
                body
                """);

        // WHEN
        var cmd = PromptYmlParser.parseCommand(tmp.resolve("review.md"));

        // THEN
        assertThat(cmd).isNotNull();
        assertThat(cmd.name()).isEqualTo("review");
        assertThat(cmd.description()).isEqualTo("Review changes");
    }

    @Test
    void parseCommand_withNameOverride(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("file.md"), """
                ---
                name: my-command
                description: desc
                ---
                body
                """);

        // WHEN
        var cmd = PromptYmlParser.parseCommand(tmp.resolve("file.md"));

        // THEN
        assertThat(cmd).isNotNull();
        assertThat(cmd.name()).isEqualTo("my-command");
        assertThat(cmd.description()).isEqualTo("desc");
    }

    @Test
    void parseCommand_quotedValues(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("cmd.md"), """
                ---
                name: "quoted-name"
                description: 'quoted desc'
                ---
                body
                """);

        // WHEN
        var cmd = PromptYmlParser.parseCommand(tmp.resolve("cmd.md"));

        // THEN
        assertThat(cmd).isNotNull();
        assertThat(cmd.name()).isEqualTo("quoted-name");
        assertThat(cmd.description()).isEqualTo("quoted desc");
    }

    @Test
    void parseSkill_subdirectory(@TempDir Path tmp) throws Exception {
        // GIVEN
        var skillDir = Files.createDirectory(tmp.resolve("my-skill"));
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: my-skill
                description: Does something useful
                ---
                body
                """);

        // WHEN
        var skill = PromptYmlParser.parseSkill(skillDir.resolve("SKILL.md"));

        // THEN
        assertThat(skill).isNotNull();
        assertThat(skill.name()).isEqualTo("my-skill");
        assertThat(skill.description()).isEqualTo("Does something useful");
        assertThat(skill.readBody()).contains("Path: " + skillDir);
    }

    @Test
    void parseSkill_flatFile(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("my-skill.md"), """
                ---
                name: my-skill
                description: Flat file skill
                ---
                body
                """);

        // WHEN
        var skill = PromptYmlParser.parseSkill(tmp.resolve("my-skill.md"));

        // THEN
        assertThat(skill).isNotNull();
        assertThat(skill.name()).isEqualTo("my-skill");
        assertThat(skill.description()).isEqualTo("Flat file skill");
    }

    @Test
    void parseSkill_missingNameAndDescription(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("bad.md"), "just body, no frontmatter");

        // WHEN
        var skill = PromptYmlParser.parseSkill(tmp.resolve("bad.md"));

        // THEN
        assertThat(skill).isNull();
    }

    @Test
    void parseSkill_nameOnlyNoDescription(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("bad.md"), """
                ---
                name: only-name
                ---
                body
                """);

        // WHEN
        var skill = PromptYmlParser.parseSkill(tmp.resolve("bad.md"));

        // THEN
        assertThat(skill).isNull();
    }

    @Test
    void parseSkill_nameFromDirectoryWhenSkillMd(@TempDir Path tmp) throws Exception {
        // GIVEN
        var skillDir = Files.createDirectory(tmp.resolve("eclipse-tool"));
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                description: Eclipse stuff
                ---
                body
                """);

        // WHEN
        var skill = PromptYmlParser.parseSkill(skillDir.resolve("SKILL.md"));

        // THEN
        assertThat(skill).isNotNull();
        assertThat(skill.name()).isEqualTo("eclipse-tool");
        assertThat(skill.description()).isEqualTo("Eclipse stuff");
    }

    @Test
    void stripFrontmatter_withFrontmatter() {
        // GIVEN
        var raw = "---\ndesc: x\n---\nbody";

        // WHEN
        var result = PromptYmlParser.stripFrontmatter(raw);

        // THEN
        assertThat(result).isEqualTo("body");
    }

    @Test
    void stripFrontmatter_noFrontmatter() {
        // GIVEN
        var raw = "plain text";

        // WHEN
        var result = PromptYmlParser.stripFrontmatter(raw);

        // THEN
        assertThat(result).isEqualTo("plain text");
    }

    @Test
    void stripFrontmatter_leadingWhitespace() {
        // GIVEN
        var raw = "\n\n---\ndesc: x\n---\nbody";

        // WHEN
        var result = PromptYmlParser.stripFrontmatter(raw);

        // THEN
        assertThat(result).isEqualTo("body");
    }

    @Test
    void stripYamlValue_unquoted() {
        assertThat(PromptYmlParser.stripYamlValue(" hello ")).isEqualTo("hello");
    }

    @Test
    void stripYamlValue_doubleQuoted() {
        assertThat(PromptYmlParser.stripYamlValue("\"hello\"")).isEqualTo("hello");
    }

    @Test
    void stripYamlValue_singleQuoted() {
        assertThat(PromptYmlParser.stripYamlValue("'hello'")).isEqualTo("hello");
    }

    @Test
    void stripYamlValue_empty() {
        assertThat(PromptYmlParser.stripYamlValue("")).isNull();
    }

    @Test
    void stripYamlValue_null() {
        assertThat(PromptYmlParser.stripYamlValue(null)).isNull();
    }
}