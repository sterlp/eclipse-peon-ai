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
 * BUG-PROOF tests (no fix yet, core-cleanup-2026-09-11) for
 * {@link SkillPromptFile#readRelativeFile(String)}:
 * <ul>
 * <li>the "accept SKILL path in the relative path" fallback is dead code — the
 *     {@code target.startsWith(skillDir)} guard runs AFTER the fallback resolved below
 *     {@code skillDir.getParent()}, so every legitimate skill-qualified path is rejected
 *     as "Path traversal not allowed" (false negative: the file exists).</li>
 * <li>a parentless relative skillDir NPEs in {@code skillDir.getParent().resolve(...)}
 *     instead of the documented "File not found" error.</li>
 * </ul>
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
