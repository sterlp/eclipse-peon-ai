package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.shared.model.SimplePromptFile;

class PromptYmlParserTest {

    @Test
    void parseYml_plainFileNoFrontmatter(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("review.md"), "Review the code.");

        // WHEN
        var prompt = PromptYmlParser.parseYml(tmp.resolve("review.md"));

        // THEN
        assertThat(prompt.name()).isEqualTo("review");
        assertThat(prompt.description()).isNull();
    }

    @Test
    void parseYml_withDescription(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("review.md"), """
                ---
                description: Review changes
                ---
                body
                """);

        // WHEN
        var prompt = PromptYmlParser.parseYml(tmp.resolve("review.md"));

        // THEN
        assertThat(prompt.name()).isEqualTo("review");
        assertThat(prompt.description()).isEqualTo("Review changes");
    }

    @Test
    void parseYml_withNameOverride(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("file.md"), """
                ---
                name: my-command
                description: desc
                ---
                body
                """);

        // WHEN
        var prompt = PromptYmlParser.parseYml(tmp.resolve("file.md"));

        // THEN
        assertThat(prompt.name()).isEqualTo("my-command");
        assertThat(prompt.description()).isEqualTo("desc");
    }

    @Test
    void parseYml_quotedValues(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("cmd.md"), """
                ---
                name: "quoted-name"
                description: 'quoted desc'
                ---
                body
                """);

        // WHEN
        var prompt = PromptYmlParser.parseYml(tmp.resolve("cmd.md"));

        // THEN
        assertThat(prompt.name()).isEqualTo("quoted-name");
        assertThat(prompt.description()).isEqualTo("quoted desc");
    }

    @Test
    void parseYml_plainFileWithBody(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("only-body.md"), "just body, no frontmatter");

        // WHEN
        var prompt = PromptYmlParser.parseYml(tmp.resolve("only-body.md"));

        // THEN
        assertThat(prompt.name()).isEqualTo("only-body");
        assertThat(prompt.readBody()).isEqualTo("just body, no frontmatter");
    }

    @Test
    void parseYml_nameOnlyNoDescription(@TempDir Path tmp) throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("only-name.md"), """
                ---
                name: only-name
                ---
                body
                """);

        // WHEN
        var prompt = PromptYmlParser.parseYml(tmp.resolve("only-name.md"));

        // THEN
        assertThat(prompt.name()).isEqualTo("only-name");
        assertThat(prompt.description()).isNull();
    }

    @Test
    void parseYml_nameFromDirectoryWhenSkillMd(@TempDir Path tmp) throws Exception {
        // GIVEN
        var skillDir = Files.createDirectory(tmp.resolve("eclipse-tool"));
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                description: Eclipse stuff
                ---
                body
                """);

        // WHEN
        var prompt = PromptYmlParser.parseYml(skillDir.resolve("SKILL.md"));

        // THEN
        assertThat(prompt.name()).isEqualTo("eclipse-tool");
        assertThat(prompt.description()).isEqualTo("Eclipse stuff");
    }

    @Test
    void stripFrontmatter_withFrontmatter() {
        // GIVEN
        var raw = "---\ndesc: x\n---\nbody";

        // WHEN
        var result = SimplePromptFile.stripFrontmatter(raw);

        // THEN
        assertThat(result).isEqualTo("body");
    }

    @Test
    void stripFrontmatter_noFrontmatter() {
        // GIVEN
        var raw = "plain text";

        // WHEN
        var result = SimplePromptFile.stripFrontmatter(raw);

        // THEN
        assertThat(result).isEqualTo("plain text");
    }

    @Test
    void stripFrontmatter_leadingWhitespace() {
        // GIVEN
        var raw = "\n\n---\ndesc: x\n---\nbody";

        // WHEN
        var result = SimplePromptFile.stripFrontmatter(raw);

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