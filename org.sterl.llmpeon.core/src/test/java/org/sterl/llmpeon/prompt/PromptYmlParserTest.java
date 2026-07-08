package org.sterl.llmpeon.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.AbstractMemoryFileTest;

class PromptYmlParserTest extends AbstractMemoryFileTest {

    @Test
    void parseYml_plainFileNoFrontmatter() throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("review.md"), "Review the code.");

        // WHEN
        var prompt = PromptYmlParser.parseYml(tmp.resolve("review.md"));

        // THEN
        assertThat(prompt.getName()).isEqualTo("review");
        assertThat(prompt.getDescription()).isNull();
        // AND
        assertThat(prompt.getBody()).isEqualTo("Review the code.");
    }

    @Test
    void parseYml_withDescription() throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("review.md"), """
                ---
                description: Review changes
                ---
                body
                and
                even
                more
                """);

        // WHEN
        var prompt = PromptYmlParser.parseYml(tmp.resolve("review.md"));

        // THEN
        assertThat(prompt.getName()).isEqualTo("review");
        assertThat(prompt.getDescription()).isEqualTo("Review changes");
        // AND
        assertThat(prompt.getBody()).isEqualTo("""
                body
                and
                even
                more""");
    }

    @Test
    void parseYml_withNameOverride() throws Exception {
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
        assertThat(prompt.getName()).isEqualTo("my-command");
        assertThat(prompt.getDescription()).isEqualTo("desc");
    }

    @Test
    void parseYml_quotedValues() throws Exception {
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
        assertThat(prompt.getName()).isEqualTo("quoted-name");
        assertThat(prompt.getDescription()).isEqualTo("quoted desc");
    }

    @Test
    void parseYml_plainFileWithBody() throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("only-body.md"), "just body, no frontmatter");

        // WHEN
        var prompt = PromptYmlParser.parseYml(tmp.resolve("only-body.md"));

        // THEN
        assertThat(prompt.getName()).isEqualTo("only-body");
        assertThat(prompt.getBody()).isEqualTo("just body, no frontmatter");
    }

    @Test
    void parseYml_nameOnlyNoDescription() throws Exception {
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
        assertThat(prompt.getName()).isEqualTo("only-name");
        assertThat(prompt.getDescription()).isNull();
    }

    @Test
    void parseYml_nameFromDirectoryWhenSkillMd() throws Exception {
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
        assertThat(prompt.getName()).isEqualTo("eclipse-tool");
        assertThat(prompt.getDescription()).isEqualTo("Eclipse stuff");
    }

    @Test
    void stripFrontmatter_withFrontmatter() throws IOException {
        // GIVEN
        Files.writeString(tmp.resolve("foo.md"), "---\ndesc: x\n---\nbody");

        // WHEN
        var result = PromptYmlParser.parseYml(tmp.resolve("foo.md"));

        // THEN
        assertThat(result.getBody()).isEqualTo("body");
    }

    @Test
    void stripFrontmatter_noFrontmatter() throws IOException {
        // GIVEN
        Files.writeString(tmp.resolve("foo.md"), "plain text");

        // WHEN
        var result = PromptYmlParser.parseYml(tmp.resolve("foo.md"));

        // THEN
        assertThat(result.getBody()).isEqualTo("plain text");
    }

    @Test
    void stripFrontmatter_leadingWhitespace() throws IOException {
        // GIVEN
        Files.writeString(tmp.resolve("foo.md"), "\n\n---\ndesc: x\n---\nbody");

        // WHEN
        var result = PromptYmlParser.parseYml(tmp.resolve("foo.md"));

        // THEN
        assertThat(result.getBody()).isEqualTo("body");
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
    void parseFrontmatter_blockList() throws Exception {
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
        var fm = PromptYmlParser.parseYml(file);

        // THEN
        assertThat(fm.get("name")).containsExactly("docs");
        assertThat(fm.get("tools")).containsExactly("grep", "read_", "*");
    }
    
    @Test
    void only_star_tools() throws Exception {
        // GIVEN
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, """
                ---
                name: docs
                tools: *
                ---
                foo bar
                """);

        // WHEN
        var fm = PromptYmlParser.parseYml(file);

        // THEN
        assertThat(fm.get("name")).containsExactly("docs");
        assertThat(fm.get("tools")).containsExactly("*");
    }

    @Test
    void parseFrontmatter_inlineCsvTools() throws Exception {
        // GIVEN
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, """
                ---
                tools: grep, read_
                ---
                body
                """);

        // WHEN — inline value kept as one raw entry, split happens in toolAllowlist
        var tools = PromptYmlParser.toolAllowlist(PromptYmlParser.parseYml(file).get("tools"));

        // THEN
        assertThat(tools).containsExactly("grep", "read_");
    }

    @Test
    void toolAllowlist_absentMeansNull() {
        assertThat(PromptYmlParser.toolAllowlist(null)).isNull();
    }

    @Test
    void setFrontmatterValue_overwritesExistingKey() throws Exception {
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
        var cmd = PromptYmlParser.parseYml(file);
        cmd.setValue("model", "new-model");
        cmd.save();

        // THEN
        var content = Files.readString(file);
        assertThat(content).contains("model: new-model");
        assertThat(content).doesNotContain("old-model");
        assertThat(content).contains("name: docs");
        assertThat(content).contains("body text");
    }

    @Test
    void setFrontmatterValue_insertsMissingKey() throws Exception {
        // GIVEN
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, """
                ---
                name: docs
                ---
                body text
                """);

        // WHEN
        var cmd = PromptYmlParser.parseYml(file);
        cmd.setValue("model", "some-model");
        cmd.save();

        // THEN
        cmd = PromptYmlParser.parseYml(file);
        assertThat(cmd.get("model")).containsExactly("some-model");
        assertThat(cmd.get("name")).containsExactly("docs");
        assertThat(Files.readString(file)).contains("body text");
    }

    @Test
    void setFrontmatterValue_prependsBlockWhenNone() throws Exception {
        // GIVEN
        var file = tmp.resolve("AGENT.md");
        Files.writeString(file, "just a body");

        // WHEN
        var cmd = PromptYmlParser.parseYml(file);
        cmd.setValue("model", "m1");
        cmd.save();

        // THEN
        cmd = PromptYmlParser.parseYml(file);
        assertThat(cmd.get("model")).containsExactly("m1");
        assertThat(cmd.getBody()).isEqualTo("just a body");
        // AND
        assertThat(Files.readString(file)).contains("just a body");
        assertThat(Files.readString(file)).contains("model: m1");
        assertThat(Files.readString(file)).contains("---");
    }
}