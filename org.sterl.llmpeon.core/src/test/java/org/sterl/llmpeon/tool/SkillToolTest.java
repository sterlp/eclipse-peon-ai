package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import dev.langchain4j.agent.tool.Tool;
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

        var skillService = new SkillService();
        skillService.refresh(tmp);
        var subject = new SkillTool(skillService);
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
        var skillService = new SkillService();
        skillService.refresh(tmp);
        var subject = new SkillTool(skillService);
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

    // project-skills R18
    @Test
    void skillListReloadsSkillsFromDisk() throws Exception {
        // GIVEN loaded skills and a skill written afterwards
        var dir = Files.createDirectory(tmp.resolve("r18-list"));
        writeSkill(dir, "old", "v1");
        var service = new SkillService();
        service.refresh(dir);
        var subject = new SkillTool(service);
        assertThat(subject.skillList()).contains("old").doesNotContain("fresh");
        writeSkill(dir, "fresh", "v1");
        // WHEN
        var result = subject.skillList();
        // THEN
        assertThat(result).contains("fresh").contains("old");
        // AND the description tells the agent about the reload
        var tool = SkillTool.class.getMethod("skillList").getAnnotation(Tool.class);
        assertThat(String.join(" ", tool.value())).contains("Reloads skills from disk");
    }

    // project-skills R18
    @Test
    void skillReadReloadsWhenQuietPeriodIsZero() throws Exception {
        // GIVEN quiet period 0, frozen clock, skill already read
        var dir = Files.createDirectory(tmp.resolve("r18-zero"));
        writeSkill(dir, "foo", "v1");
        var service = new SkillService();
        service.refresh(dir);
        var subject = new SkillTool(service, Duration.ZERO, new MutableClock());
        assertThat(subject.skillRead("foo")).contains("v1");
        // WHEN the body changes on disk
        writeSkill(dir, "foo", "v2");
        // THEN
        assertThat(subject.skillRead("foo")).contains("v2");
    }

    // project-skills R18
    @Test
    void skillReadKeepsOldBodyWithinQuietPeriod() throws Exception {
        // GIVEN quiet period 10 min, skill just read
        var dir = Files.createDirectory(tmp.resolve("r18-quiet"));
        writeSkill(dir, "foo", "v1");
        var service = new SkillService();
        service.refresh(dir);
        var clock = new MutableClock();
        var subject = new SkillTool(service, Duration.ofMinutes(10), clock);
        assertThat(subject.skillRead("foo")).contains("v1");
        // WHEN the body changes and 9 minutes pass
        writeSkill(dir, "foo", "v2");
        clock.advance(Duration.ofMinutes(9));
        // THEN the old body is still served
        assertThat(subject.skillRead("foo")).contains("v1");
        // AND exactly at the quiet period (>=) the new one
        clock.advance(Duration.ofMinutes(1));
        assertThat(subject.skillRead("foo")).contains("v2");
    }

    // project-skills R18
    @Test
    void unknownNameTriggersOneReloadBeforeNotFound() throws Exception {
        // GIVEN quiet period 10 min, frozen clock, timestamp set by a first read
        var dir = Files.createDirectory(tmp.resolve("r18-unknown"));
        writeSkill(dir, "foo", "v1");
        var service = new SkillService();
        service.refresh(dir);
        var subject = new SkillTool(service, Duration.ofMinutes(10), new MutableClock());
        assertThat(subject.skillRead("foo")).contains("v1");
        // WHEN a skill appears on disk afterwards
        writeSkill(dir, "late", "late body");
        // THEN skillRead finds it
        assertThat(subject.skillRead("late")).contains("late body").endsWith(FOOTER);
        // AND skillReadFile finds a skill created even later (not loaded by the reload above)
        writeSkill(dir, "late2", "v1");
        Files.createDirectory(dir.resolve("late2/refs"));
        Files.writeString(dir.resolve("late2/refs/x.md"), "x content");
        assertThat(subject.skillReadFile("late2", "refs/x.md")).contains("x content").endsWith(FOOTER);
        // AND a really missing name is still not found, footer-free, on both paths
        assertThat(subject.skillRead("nope")).startsWith("No skill with the name nope").doesNotEndWith(FOOTER);
        assertThat(subject.skillReadFile("nope", "refs/x.md")).startsWith("No skill with the name nope").doesNotEndWith(FOOTER);
    }

    // project-skills R18
    @Test
    void skillListShowsLastStateAndNamesReloadFailure() throws Exception {
        // Jimfs does not enforce posix permissions -> real temp dir (see SkillServiceTest.failedRefreshKeepsPreviousState)
        var dir = Files.createTempDirectory("skill-r18");
        try {
            writeSkill(dir, "kept", "v1");
            var service = new SkillService();
            service.refresh(dir);
            var subject = new SkillTool(service);
            assertThat(subject.skillList()).contains("kept");
            // WHEN the root is no longer listable
            Files.setPosixFilePermissions(dir, Set.of());
            Assumptions.assumeTrue(!Files.isReadable(dir), "environment does not enforce directory permissions");
            var result = subject.skillList();
            // THEN last state is listed and the failure named
            assertThat(result).contains("Skill reload failed").contains("showing last loaded state").contains("kept");
        } finally {
            Files.setPosixFilePermissions(dir, Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
            Files.deleteIfExists(dir.resolve("kept/SKILL.md"));
            Files.deleteIfExists(dir.resolve("kept"));
            Files.deleteIfExists(dir);
        }
    }

    // project-skills R18
    @Test
    void failedReloadIsRetriedAndNamedAgainOnNextRead() throws Exception {
        var dir = Files.createTempDirectory("skill-r18-retry");
        var rwx = Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
        try {
            // GIVEN quiet period 10 min, skill with a file, successful list at t=0
            writeSkill(dir, "kept", "v1");
            Files.createDirectory(dir.resolve("kept/refs"));
            Files.writeString(dir.resolve("kept/refs/x.md"), "x content");
            var service = new SkillService();
            service.refresh(dir);
            var clock = new MutableClock();
            var subject = new SkillTool(service, Duration.ofMinutes(10), clock);
            assertThat(subject.skillList()).doesNotContain("Skill reload failed");
            // WHEN the root becomes unreadable and the reload fails at +1 min
            Files.setPosixFilePermissions(dir, Set.of());
            Assumptions.assumeTrue(!Files.isReadable(dir), "environment does not enforce directory permissions");
            clock.advance(Duration.ofMinutes(1));
            assertThat(subject.skillList()).contains("Skill reload failed");
            // THEN a read at +2 min (inside the quiet period of the last success) retries, names the error, serves the old body
            clock.advance(Duration.ofMinutes(1));
            assertThat(subject.skillRead("kept")).contains("Skill reload failed").contains("v1");
            // AND the file read names the reload failure in its error
            assertThatThrownBy(() -> subject.skillReadFile("kept", "refs/x.md"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Skill reload failed")
                    .hasMessageContaining("File not found");
            // AND once the root is readable again the next read reloads and no longer names the error
            Files.setPosixFilePermissions(dir, rwx);
            writeSkill(dir, "kept", "v2");
            assertThat(subject.skillRead("kept")).contains("v2").doesNotContain("Skill reload failed");
        } finally {
            Files.setPosixFilePermissions(dir, rwx);
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private static void writeSkill(Path root, String name, String body) throws Exception {
        var skillDir = root.resolve(name);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\ndescription: desc " + name + "\n---\n" + body + "\n");
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-11-09T08:00:00Z");
        void advance(Duration d) { instant = instant.plus(d); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    private static void assertFooterExactlyOnce(String result) {
        assertThat(result).endsWith(FOOTER);
        assertThat(result.indexOf(FOOTER)).isEqualTo(result.lastIndexOf(FOOTER));
    }
}
