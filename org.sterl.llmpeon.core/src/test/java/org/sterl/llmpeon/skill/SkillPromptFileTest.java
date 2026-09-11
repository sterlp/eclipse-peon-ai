package org.sterl.llmpeon.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Bug proofs for {@link SkillPromptFile#readRelativeFile(String)}: dead skill-qualified-path fallback
 * (false "Path traversal") + parentless-skillDir NPE — fixed via ancestor resolve inside skillDir, inc-1 8b2431e.
 */
class SkillPromptFileTest {

    @TempDir
    Path tmp;

    @Test
    void bug_readRelativeFileWithSkillQualifiedPathAlwaysThrows() throws IOException {
        // GIVEN — a skill dir tmp/skills/my-skill holding file.txt, and the skill-qualified
        // relative path the fallback branch is documented to accept
        var skillDir = tmp.resolve("skills").resolve("my-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("file.txt"), "content");
        var subject = new SkillPromptFile(Map.of(), "body", skillDir.resolve("SKILL.md"), skillDir);

        // WHEN + THEN — the existing file is readable via its skill-qualified path
        assertThat(subject.readRelativeFile("skills/my-skill/file.txt")).isEqualTo("content");
    }

    @Test
    void bug_readRelativeFileWithParentlessSkillDirThrowsNpe() {
        // GIVEN — a relative skillDir without a parent segment
        var subject = new SkillPromptFile(Map.of(), "body", Path.of("SKILL.md"), Path.of("my-skill"));

        // WHEN + THEN — a missing file yields the documented "File not found" IAE, never an NPE
        assertThatThrownBy(() -> subject.readRelativeFile("my-skill/other.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File not found");
    }

    @Test
    void readRelativeFileRejectsPathTraversal() throws IOException {
        // GIVEN — a skill dir with a file, so a successful read would be distinguishable
        var skillDir = tmp.resolve("skills").resolve("my-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("file.txt"), "content");
        var subject = new SkillPromptFile(Map.of(), "body", skillDir.resolve("SKILL.md"), skillDir);

        // WHEN + THEN — paths escaping the skill dir are rejected as traversal, not "File not found"
        assertThatThrownBy(() -> subject.readRelativeFile("../escape.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path traversal");
        assertThatThrownBy(() -> subject.readRelativeFile("./sub/../../out.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path traversal");
    }

    @Test
    void readRelativeFileResolvesPlainRelativePath() throws IOException {
        // GIVEN — sanity: the plain (non-qualified) path works today
        var skillDir = tmp.resolve("skills").resolve("my-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("file.txt"), "content");
        var subject = new SkillPromptFile(Map.of(), "body", skillDir.resolve("SKILL.md"), skillDir);

        // WHEN + THEN
        assertThat(subject.readRelativeFile("file.txt")).isEqualTo("content");
    }
}
