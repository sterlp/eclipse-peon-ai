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

    @Test
    void parseFrontmatter_blockList(@TempDir Path tmp) throws Exception {
        // GIVEN
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, """
                ---
                name: docs
                tools:
                  - grep
                  - read_
                  - "*"
                ---
                body
                """);

        // WHEN
        var fm = PromptYmlParser.parseFrontmatter(file);

        // THEN
        assertThat(fm.get("name")).containsExactly("docs");
        assertThat(fm.get("tools")).containsExactly("grep", "read_", "*");
    }

    @Test
    void parseFrontmatter_inlineCsvTools(@TempDir Path tmp) throws Exception {
        // GIVEN
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, """
                ---
                tools: grep, read_
                ---
                body
                """);

        // WHEN — inline value kept as one raw entry, split happens in toolAllowlist
        var tools = PromptYmlParser.toolAllowlist(PromptYmlParser.parseFrontmatter(file).get("tools"));

        // THEN
        assertThat(tools).containsExactly("grep", "read_");
    }

    @Test
    void toolAllowlist_absentMeansNull() {
        assertThat(PromptYmlParser.toolAllowlist(null)).isNull();
    }

    @Test
    void setFrontmatterValue_overwritesExistingKey(@TempDir Path tmp) throws Exception {
        // GIVEN
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, """
                ---
                name: docs
                model: old-model
                ---
                body text
                """);

        // WHEN
        PromptYmlParser.setFrontmatterValue(file, "model", "new-model");

        // THEN
        var content = Files.readString(file);
        assertThat(content).contains("model: new-model");
        assertThat(content).doesNotContain("old-model");
        assertThat(content).contains("name: docs");
        assertThat(content).contains("body text");
    }

    @Test
    void setFrontmatterValue_insertsMissingKey(@TempDir Path tmp) throws Exception {
        // GIVEN
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, """
                ---
                name: docs
                ---
                body text
                """);

        // WHEN
        PromptYmlParser.setFrontmatterValue(file, "model", "some-model");

        // THEN
        var fm = PromptYmlParser.parseFrontmatter(file);
        assertThat(fm.get("model")).containsExactly("some-model");
        assertThat(fm.get("name")).containsExactly("docs");
        assertThat(Files.readString(file)).contains("body text");
    }

    @Test
    void setFrontmatterValue_prependsBlockWhenNone(@TempDir Path tmp) throws Exception {
        // GIVEN
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, "just a body");

        // WHEN
        PromptYmlParser.setFrontmatterValue(file, "model", "m1");

        // THEN
        var fm = PromptYmlParser.parseFrontmatter(file);
        assertThat(fm.get("model")).containsExactly("m1");
        assertThat(Files.readString(file)).contains("just a body");
    }
}