package org.sterl.llmpeon.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.AbstractMemoryFileTest;
import org.sterl.llmpeon.prompt.PromptYmlParser;

class SkillServiceTest extends AbstractMemoryFileTest {

    private static final Path SKILLS_DIR = Path.of("../skills");

    static final SkillService subject = new SkillService();

    @BeforeAll
    static void beforeAll() throws Exception {
        subject.refresh(SKILLS_DIR);
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
        assertThat(skill.renderBody()).contains(Path.of("bar", "baaar.md").toString());
        
        // AND
        assertThat(skill.readRelativeFile(Path.of("foo.md").toString())).isEqualTo("Foo");
        assertThat(skill.readRelativeFile(Path.of("bar/baaar.md").toString())).isEqualTo("Bar");
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
        // GIVEN skills directory with at least one skill

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
}