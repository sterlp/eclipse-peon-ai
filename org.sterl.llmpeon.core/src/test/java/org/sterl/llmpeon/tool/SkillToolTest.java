package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.AbstractMemoryFileTest;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.tool.tools.SkillTool;

class SkillToolTest extends AbstractMemoryFileTest {

    /** R1 skill-evolution-loop — same contract text as {@code SkillTool} appends to both read paths. */
    private static final String FOOTER =
            "Report in your answer — helpful? wrong/outdated/incomplete? obsolete?";

    @Test
    void test() throws Exception {
        // GIVEN
        var skillDir = Files.createDirectory(tmp.resolve("foo"));
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                description: Does something useful
                ---
                body haha
                """);
        // AND
        var barDir = Files.createDirectory(skillDir.resolve("refs"));
        Files.writeString(barDir.resolve("bar.md"), "foo in bar");

        var subject = new SkillTool(new SkillService(tmp));
        // WHEN
        assertThat(subject.skillRead("foo")).contains("body haha");
        // THEN
        assertThat(subject.skillReadFile("foo", "refs/bar.md")).contains("foo in bar").endsWith(FOOTER);
        assertThat(subject.skillReadFile("foo", "/refs/bar.md")).contains("foo in bar").endsWith(FOOTER);

        // THEN
        assertThat(subject.skillReadFile("foo", "foo/refs/bar.md")).contains("foo in bar").endsWith(FOOTER);
        assertThat(subject.skillReadFile("foo", "/foo/refs/bar.md")).contains("foo in bar").endsWith(FOOTER);
    }

    @Test
    void skillReadAppendsUsefulnessFooter() throws Exception {
        // GIVEN a skill with a prompt body and a referenced file
        // (distinct dir name: tmp is static per test class, "foo" is used by the other test)
        var skillDir = Files.createDirectory(tmp.resolve("useful"));
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                description: Proves its token value
                ---
                body useful
                """);
        Files.createDirectory(skillDir.resolve("refs"));
        Files.writeString(skillDir.resolve("refs/bar.md"), "foo in bar");
        var subject = new SkillTool(new SkillService(tmp));
        // WHEN an agent reads the skill and reads a file from it
        var read = subject.skillRead("useful");
        var readFile = subject.skillReadFile("useful", "refs/bar.md");
        // THEN both results end with the usefulness report, exactly once
        assertFooterExactlyOnce(read);
        assertFooterExactlyOnce(readFile);
        // AND skillRead still carries the skill body
        assertThat(read).contains("body useful");
        // AND a not-found read stays footer-free (no skill read, nothing to report)
        assertThat(subject.skillRead("missing")).doesNotEndWith(FOOTER);
    }

    private static void assertFooterExactlyOnce(String result) {
        assertThat(result).endsWith(FOOTER);
        assertThat(result.indexOf(FOOTER)).isEqualTo(result.lastIndexOf(FOOTER));
    }
}
