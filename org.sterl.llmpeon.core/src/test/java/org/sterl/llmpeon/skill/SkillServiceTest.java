package org.sterl.llmpeon.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.shared.PromptYmlParser;

class SkillServiceTest {

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
    void testRefreshLoadsSkills() throws Exception {
        // GIVEN skills directory with at least one skill

        // WHEN
        var skills = subject.getSkills();

        // THEN
        assertThat(skills).isNotEmpty();
        skills.forEach(r -> System.err.println(r));
    }

    @Test
    void testEclipseIFilePathsSkill() throws Exception {
        // GIVEN expected skill name
        var expectedName = "eclipse-ifile-paths";

        // WHEN
        var skill = subject.get(expectedName)
                .orElseThrow(() -> new AssertionError(expectedName + " skill not found"));

        // THEN
        assertThat(skill.name()).isEqualTo(expectedName);
        assertThat(skill.description()).contains("Eclipse IFile");
        assertThat(skill.readBody()).contains("toPortableString");
    }

    @Test
    void testParseSkillFileReturnsNullForMissingFile() throws Exception {
        // GIVEN a non-existent path

        // WHEN
        var result = PromptYmlParser.parseSkill(SKILLS_DIR.resolve("nonexistent/SKILL.md"));

        // THEN
        assertThat(result).isNull();
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
        subject.setSkillEnabled(firstSkill.name(), false);

        // THEN
        assertThat(subject.getSkills())
                .noneMatch(s -> s.name().equals(firstSkill.name()) && !s.isEnabled());
        assertThat(subject.skillNames()).doesNotContain(firstSkill.name());
    }

    @Test
    void testSetAllSkillsEnabled() throws Exception {
        // GIVEN
        var allSkills = subject.getAllLoadedSkills();
        assertThat(allSkills).isNotEmpty();

        // WHEN
        subject.setAllSkillsEnabled(false);

        // THEN
        assertThat(allSkills).noneMatch(Skill::isEnabled);

        // WHEN
        subject.setAllSkillsEnabled(true);

        // THEN
        assertThat(allSkills).allMatch(Skill::isEnabled);
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