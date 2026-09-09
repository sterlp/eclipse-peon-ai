package org.sterl.llmpeon.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.AbstractMemoryFileTest;
import org.sterl.llmpeon.prompt.PromptYmlParser;
import org.sterl.llmpeon.shared.FileUtils;

class SkillServiceTest extends AbstractMemoryFileTest {

    static final SkillService subject = new SkillService();

    @BeforeAll
    static void beforeAll() throws Exception {
        var skillsDir = Files.createDirectory(tmp.resolve("skills"));
        writeSkill(skillsDir, "eclipse-ifile-paths", "Eclipse IFile paths", "use toPortableString");
        subject.refresh(skillsDir);
    }

    @BeforeEach
    void before() {
        subject.setEnabled(true);
        subject.setAllSkillsEnabled(true);
    }

    @Test
    void parseYml_subdirectory() throws Exception {
        // GIVEN
        var skillDir = Files.createDirectory(tmp.resolve("my-skill"));
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: my-skill-name
                description: Does something useful
                ---
                body
                """);
        
        Files.writeString(skillDir.resolve("foo.md"), "Foo", StandardOpenOption.CREATE);
        Files.createDirectories(skillDir.resolve("bar"));
        Files.writeString(skillDir.resolve("bar/baaar.md"), "Bar", StandardOpenOption.CREATE);

        // WHEN
        var promt = PromptYmlParser.parseYml(skillDir.resolve("SKILL.md"));
        var skill = SkillPromptFile.from(promt, tmp.resolve("my-skill"));

        // THEN
        assertThat(skill).isNotNull();
        assertThat(skill.getName()).isEqualTo("my-skill-name");
        assertThat(skill.getDescription()).isEqualTo("Does something useful");
        assertThat(skill.renderBody()).contains(Path.of("foo.md").toString());
        assertThat(skill.renderBody()).contains(FileUtils.normalizePath(Path.of("bar", "baaar.md").toString()));
        
        // AND
        assertThat(skill.readRelativeFile(Path.of("foo.md").toString())).isEqualTo("Foo");
        assertThat(skill.readRelativeFile(Path.of("bar", "baaar.md").toString())).isEqualTo("Bar");
    }

    @Test
    void parseYml_flatFile() throws Exception {
        // GIVEN
        Files.writeString(tmp.resolve("my-skill.md"), """
                ---
                name: my-skill
                description: Flat file skill
                ---
                body
                """);

        // WHEN
        var skill = PromptYmlParser.parseYml(tmp.resolve("my-skill.md"));

        // THEN
        assertThat(skill).isNotNull();
        assertThat(skill.getName()).isEqualTo("my-skill");
        assertThat(skill.getDescription()).isEqualTo("Flat file skill");
        assertThat(skill.getBody()).isEqualTo("body");
    }

    @Test
    void testRefreshLoadsSkills() throws Exception {
        // GIVEN skills directory with one skill written by this test

        // WHEN
        var skills = subject.getSkills();

        // THEN
        assertThat(skills).isNotEmpty();
    }

    @Test
    void testEclipseIFilePathsSkill() throws Exception {
        // GIVEN expected skill name
        var expectedName = "eclipse-ifile-paths";

        // WHEN
        var skill = subject.get(expectedName)
                .orElseThrow(() -> new AssertionError(expectedName + " skill not found"));

        // THEN
        assertThat(skill.getName()).isEqualTo(expectedName);
        assertThat(skill.getDescription()).contains("Eclipse IFile");
        assertThat(skill.renderBody()).contains("toPortableString");
    }

    @Test
    void testEmptyDirectoryReturnsEmptyList() throws Exception {
        // GIVEN
        var service = new SkillService();

        // WHEN
        service.refresh(Path.of("nonexistent-dir"));

        // THEN
        assertThat(service.getSkills()).isEmpty();
    }

    @Test
    void testIndividualSkillToggle() throws Exception {
        // GIVEN
        var skills = subject.getAllLoadedSkills();
        assertThat(skills).isNotEmpty();
        var firstSkill = skills.get(0);
        assertThat(firstSkill.isEnabled()).isTrue();

        // WHEN
        subject.setSkillEnabled(firstSkill.getName(), false);

        // THEN
        assertThat(subject.getSkills())
                .noneMatch(s -> s.getName().equals(firstSkill.getName()) && !s.isEnabled());
        assertThat(subject.skillNames()).doesNotContain(firstSkill.getName());
    }

    @Test
    void testSetAllSkillsEnabled() throws Exception {
        // GIVEN
        var allSkills = subject.getAllLoadedSkills();
        assertThat(allSkills).isNotEmpty();

        // WHEN
        subject.setAllSkillsEnabled(false);

        // THEN
        assertThat(allSkills).noneMatch(SkillPromptFile::isEnabled);

        // WHEN
        subject.setAllSkillsEnabled(true);

        // THEN
        assertThat(allSkills).allMatch(SkillPromptFile::isEnabled);
    }

    @Test
    void testGetAllLoadedSkillsIgnoresGlobalState() throws Exception {
        // GIVEN
        var loadedCount = subject.getAllLoadedSkills().size();
        assertThat(loadedCount).isPositive();

        // WHEN
        subject.setEnabled(false);

        // THEN
        assertThat(subject.getSkills()).isEmpty();
        assertThat(subject.getAllLoadedSkills()).hasSize(loadedCount);
    }

    // ---------------------------------------------------------------------
    // Project-skills feature (R1–R7) — local instances + local tmp subdirs,
    // order-independent; the shared static subject is left alone.
    // ---------------------------------------------------------------------

    @Test
    void twoComponents_projectSwitchReplacesProjectComponent() throws Exception {
        // GIVEN config skill + project A and B skills
        var service = new SkillService();
        var configDir = Files.createDirectory(tmp.resolve("r2-config"));
        var projA = Files.createDirectory(tmp.resolve("r2-projA"));
        var projB = Files.createDirectory(tmp.resolve("r2-projB"));
        writeSkill(configDir, "cfg-skill", "cfg-skill", "c");
        writeSkill(projA, "alpha", "alpha", "a");
        writeSkill(projB, "beta", "beta", "b");
        service.refresh(configDir);
        service.setProjectSkillsDir(projA);

        // WHEN project B is selected
        service.setProjectSkillsDir(projB);

        // THEN B's project skill present, A's gone, config skill untouched
        assertThat(service.skillNames()).contains("beta [project]", "cfg-skill [config]");
        assertThat(service.skillNames()).doesNotContain("alpha");

        // AND a project without .agents/skills → empty project slot, config unchanged
        service.setProjectSkillsDir(tmp.resolve("r2-missing"));
        assertThat(service.getSkills())
                .extracting(SkillPromptFile::getName)
                .containsExactly("cfg-skill");
    }

    @Test
    void configComponentNeverMutated_maskedSkillReturns() throws Exception {
        // GIVEN config skill masked by a project skill of the same name
        var service = new SkillService();
        var configDir = Files.createDirectory(tmp.resolve("r2b-config"));
        var projOverride = Files.createDirectory(tmp.resolve("r2b-projOverride"));
        var projOther = Files.createDirectory(tmp.resolve("r2b-projOther"));
        writeSkill(configDir, "deploy", "deploy", "config-version");
        writeSkill(projOverride, "deploy", "deploy", "project-version");
        service.refresh(configDir);
        service.setProjectSkillsDir(projOverride);
        assertThat(service.get("deploy").orElseThrow().getSource()).isEqualTo(SkillSource.PROJECT);

        // WHEN a project without the masking skill is selected
        service.setProjectSkillsDir(projOther);

        // THEN the config skill returns, unchanged
        var deploy = service.get("deploy").orElseThrow();
        assertThat(deploy.getSource()).isEqualTo(SkillSource.CONFIG);
        assertThat(deploy.getBody()).isEqualTo("config-version");

        // AND no project selected → exactly the config skills
        service.setProjectSkillsDir(null);
        assertThat(service.getAllLoadedSkills())
                .extracting(SkillPromptFile::getName)
                .containsExactly("deploy");
    }

    @Test
    void projectSkillOverridesConfigSkillByLowercaseName() throws Exception {
        // GIVEN config and project skill, same name in different case
        var service = new SkillService();
        var configDir = Files.createDirectory(tmp.resolve("r4-config"));
        var projDir = Files.createDirectory(tmp.resolve("r4-proj"));
        writeSkill(configDir, "code-review", "code-review", "config");
        writeSkill(projDir, "CODE-REVIEW", "CODE-REVIEW", "project");
        service.refresh(configDir);
        service.setProjectSkillsDir(projDir);

        // WHEN the view is built
        var skill = service.get("code-review").orElseThrow();

        // THEN the project variant wins, counted once
        assertThat(skill.getSource()).isEqualTo(SkillSource.PROJECT);
        assertThat(skill.getName()).isEqualTo("CODE-REVIEW");
        assertThat(service.loadedSkillCount()).isEqualTo(1);
    }

    @Test
    void reloadRefreshesEveryComponent() throws Exception {
        // GIVEN both slots loaded
        var service = new SkillService();
        var configDir = Files.createDirectory(tmp.resolve("r3-config"));
        var projDir = Files.createDirectory(tmp.resolve("r3-proj"));
        writeSkill(configDir, "cfg-old", "cfg-old", "c");
        writeSkill(projDir, "prj-old", "prj-old", "p");
        service.refresh(configDir);
        service.setProjectSkillsDir(projDir);

        // WHEN a config reload runs after both roots gained new skills
        Files.delete(configDir.resolve("cfg-old.md"));
        writeSkill(configDir, "cfg-new", "cfg-new", "c");
        Files.delete(projDir.resolve("prj-old.md"));
        writeSkill(projDir, "prj-new", "prj-new", "p");
        service.refreshAll();

        // THEN both components reloaded, project slot still on its path
        assertThat(service.getSkills())
                .extracting(SkillPromptFile::getName)
                .containsExactlyInAnyOrder("cfg-new", "prj-new");
        assertThat(service.get("prj-new").orElseThrow().getSource()).isEqualTo(SkillSource.PROJECT);
    }

    /**
     * Approximates R5: each slot's map is swapped as a single reference, so a
     * reader may see the old or the new complete root — never a partial one.
     * The merged view may mix old/new <i>per slot</i> (refreshAll reloads the
     * slots sequentially), so all four full-root combinations are legal.
     */
    @Test
    void swapIsAtomic_noPartialMapVisible() throws Exception {
        // GIVEN two skills per root
        var service = new SkillService();
        var configDir = Files.createDirectory(tmp.resolve("r5-config"));
        var projDir = Files.createDirectory(tmp.resolve("r5-proj"));
        writeSkill(configDir, "cfg-1", "cfg-1", "c");
        writeSkill(configDir, "cfg-2", "cfg-2", "c");
        writeSkill(projDir, "prj-1", "prj-1", "p");
        writeSkill(projDir, "prj-2", "prj-2", "p");
        service.refresh(configDir);
        service.setProjectSkillsDir(projDir);

        var allowed = new HashSet<List<String>>();
        for (var cfg : List.of(Set.of("cfg-1", "cfg-2"), Set.of("cfg-3", "cfg-4")))
            for (var prj : List.of(Set.of("prj-1", "prj-2"), Set.of("prj-3", "prj-4")))
                allowed.add(Stream.concat(cfg.stream(), prj.stream()).sorted().toList());

        var done = new AtomicBoolean(false);
        var violation = new AtomicReference<List<String>>();
        var reader = new Thread(() -> {
            while (!done.get()) {
                var names = service.getAllLoadedSkills().stream()
                        .map(SkillPromptFile::getName).sorted().toList();
                if (!allowed.contains(names)) violation.set(names);
            }
        });
        reader.start();
        try {
            for (int i = 0; i < 200 && violation.get() == null; i++) {
                deleteIfExists(configDir, "cfg-1.md", "cfg-2.md");
                writeSkill(configDir, "cfg-3", "cfg-3", "c");
                writeSkill(configDir, "cfg-4", "cfg-4", "c");
                deleteIfExists(projDir, "prj-1.md", "prj-2.md");
                writeSkill(projDir, "prj-3", "prj-3", "p");
                writeSkill(projDir, "prj-4", "prj-4", "p");
                service.refreshAll();
            }
        } finally {
            done.set(true);
            reader.join();
        }

        // THEN no partial map was ever visible
        assertThat(violation.get()).as("partial skill map visible").isNull();
    }

    @Test
    void sourceMarkerAndListDisclosure() throws Exception {
        // GIVEN one config and one project skill
        var service = new SkillService();
        var configDir = Files.createDirectory(tmp.resolve("r7-config"));
        var projDir = Files.createDirectory(tmp.resolve("r7-proj"));
        writeSkill(configDir, "cfg", "cfg", "c");
        writeSkill(projDir, "review", "review", "r");
        service.refresh(configDir);
        service.setProjectSkillsDir(projDir);

        // WHEN the skills are inspected
        var projectSkill = service.get("review").orElseThrow();
        var configSkill = service.get("cfg").orElseThrow();

        // THEN source is marked on the instance, the read header and the list
        assertThat(projectSkill.getSource()).isEqualTo(SkillSource.PROJECT);
        assertThat(projectSkill.renderBody()).startsWith("=== SKILL: review [project] ===");
        assertThat(projectSkill.buildShortInfo()).contains("source: [project]");
        assertThat(configSkill.renderBody()).startsWith("=== SKILL: cfg [config] ===");
        assertThat(configSkill.buildShortInfo()).contains("source: [config]");
        assertThat(service.skillNames()).contains("review [project]", "cfg [config]");
    }

    @Test
    void get_acceptsTaggedName_returnsSkill() throws Exception {
        // GIVEN a config skill "cfg" + a project skill "review"
        var service = new SkillService();
        var configDir = Files.createDirectory(tmp.resolve("r4a-config"));
        var projDir = Files.createDirectory(tmp.resolve("r4a-proj"));
        writeSkill(configDir, "cfg", "cfg", "c");
        writeSkill(projDir, "review", "review", "r");
        service.refresh(configDir);
        service.setProjectSkillsDir(projDir);

        // WHEN the model echoes a tagged name as listed by skillNames()
        // THEN it resolves, with the correct source (case-insensitive tag)
        assertThat(service.get("review [project]").orElseThrow().getSource()).isEqualTo(SkillSource.PROJECT);
        assertThat(service.get("cfg [config]").orElseThrow().getSource()).isEqualTo(SkillSource.CONFIG);
        assertThat(service.get("REVIEW [PROJECT]").orElseThrow().getName()).isEqualTo("review");

        // AND plain names still resolve
        assertThat(service.get("review").orElseThrow().getSource()).isEqualTo(SkillSource.PROJECT);
        assertThat(service.get("cfg").orElseThrow().getSource()).isEqualTo(SkillSource.CONFIG);

        // AND unknown tags / unknown names keep missing
        assertThat(service.get("review [bogus]")).isEmpty();
        assertThat(service.get("nope")).isEmpty();
    }

    @Test
    void enabledStateSurvivesRefreshAndFollowsName() throws Exception {
        // GIVEN config skill foo, disabled by name
        var service = new SkillService();
        var configDir = Files.createDirectory(tmp.resolve("r1-config"));
        var projDir = Files.createDirectory(tmp.resolve("r1-proj"));
        writeSkill(configDir, "foo", "foo", "config-foo");
        service.refresh(configDir);
        service.setSkillEnabled("foo", false);

        // WHEN a reload happens
        service.refresh(configDir);
        assertThat(service.getSkills()).noneMatch(s -> s.getName().equalsIgnoreCase("foo"));

        // AND the disabled name still applies to a project override of the same name
        writeSkill(projDir, "foo", "foo", "project-foo");
        service.setProjectSkillsDir(projDir);
        assertThat(service.getSkills()).noneMatch(s -> s.getName().equalsIgnoreCase("foo"));

        // AND a first-seen name starts enabled
        writeSkill(projDir, "bar", "bar", "project-bar");
        service.refreshAll();
        assertThat(service.get("bar").orElseThrow().isEnabled()).isTrue();
        assertThat(service.getSkills()).anyMatch(s -> s.getName().equals("bar"));
    }

    @Test
    void noProjectUsesEmptyComponent_pathNeverNull() throws Exception {
        // GIVEN a fresh service with one config skill, no project selected
        var service = new SkillService();
        var configDir = Files.createDirectory(tmp.resolve("r14-config"));
        writeSkill(configDir, "cfg", "cfg", "c");
        service.refresh(configDir);

        // WHEN the view is computed
        var names = service.getSkills().stream().map(SkillPromptFile::getName).toList();

        // THEN view = config skills, project component has a non-null path and is empty
        assertThat(names).containsExactly("cfg");
        assertThat(service.projectComponent().path()).isNotNull();
        assertThat(service.projectComponent().skills()).isEmpty();

        // AND after setProjectSkillsDir(null) it is still non-null + empty
        service.setProjectSkillsDir(null);
        assertThat(service.projectComponent().path()).isNotNull();
        assertThat(service.projectComponent().skills()).isEmpty();
    }

    @Test
    void projectSwitchAlwaysFreshLoad_noCache() throws Exception {
        // GIVEN project A with skill alpha
        var service = new SkillService();
        var projA = Files.createDirectory(tmp.resolve("r14a-A"));
        var projB = Files.createDirectory(tmp.resolve("r14a-B"));
        writeSkill(projA, "alpha", "alpha", "a");
        service.setProjectSkillsDir(projA);
        var first = service.projectComponent();

        // WHEN A → B → (second A skill written on disk) → A
        service.setProjectSkillsDir(projB);
        var second = service.projectComponent();
        writeSkill(projA, "alpha2", "alpha2", "a2");
        service.setProjectSkillsDir(projA);
        var third = service.projectComponent();

        // THEN the view contains both A skills (fresh load)
        assertThat(service.getSkills())
                .extracting(SkillPromptFile::getName)
                .containsExactlyInAnyOrder("alpha", "alpha2");

        // AND every switch is a new component instance (no cache)
        assertThat(second).isNotSameAs(first);
        assertThat(third).isNotSameAs(second);
        assertThat(third).isNotSameAs(first);

        // AND delete A's skills + switch to A again → they are gone
        Files.delete(projA.resolve("alpha.md"));
        Files.delete(projA.resolve("alpha2.md"));
        service.setProjectSkillsDir(projB);
        service.setProjectSkillsDir(projA);
        assertThat(service.getSkills()).isEmpty();
    }

    @Test
    void failedRefreshKeepsPreviousState() throws Exception {
        // A load failure is forced with a real, unreadable temp dir — Jimfs does
        // not enforce posix permissions. Skips where the environment cannot
        // produce the denial (e.g. running as root).
        var base = Files.createTempDirectory("skill-r5a");
        var configDir = Files.createDirectory(base.resolve("config-skills"));
        var projDir = Files.createDirectory(base.resolve("proj-skills"));
        try {
            var service = new SkillService();
            writeSkill(configDir, "cfg", "cfg", "c");
            writeSkill(projDir, "prj", "prj", "p");
            service.refresh(configDir);
            service.setProjectSkillsDir(projDir);

            // WHEN a reload fails (config dir no longer listable)
            Files.setPosixFilePermissions(configDir, Set.of());
            var error = catchThrowable(service::refreshAll);
            Assumptions.assumeTrue(error != null,
                    "environment does not enforce directory permissions");
            assertThat(error).isInstanceOf(IOException.class);

            // THEN the previous state is kept unchanged (no clear-before-load)
            assertThat(service.getAllLoadedSkills())
                    .extracting(SkillPromptFile::getName)
                    .containsExactlyInAnyOrder("cfg", "prj");
        } finally {
            Files.setPosixFilePermissions(configDir, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
            deleteIfExists(configDir, "cfg.md");
            deleteIfExists(projDir, "prj.md");
            Files.deleteIfExists(configDir);
            Files.deleteIfExists(projDir);
            Files.deleteIfExists(base);
        }
    }

    private static void deleteIfExists(Path dir, String... names) throws IOException {
        for (var name : names) Files.deleteIfExists(dir.resolve(name));
    }

    private static void writeSkill(Path dir, String name, String description, String body) throws IOException {
        Files.writeString(dir.resolve(name + ".md"), """
                ---
                name: %s
                description: %s
                ---
                %s
                """.formatted(name, description, body));
    }
}
