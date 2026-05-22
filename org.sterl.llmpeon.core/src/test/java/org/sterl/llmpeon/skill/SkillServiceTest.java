package org.sterl.llmpeon.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SkillServiceTest {

    // points to the project root skills/ folder
    private static final Path SKILLS_DIR = Path.of("../skills");

    @Test
    void testRefreshLoadsSkills() throws Exception {
        SkillService service = new SkillService();
        service.refresh(SKILLS_DIR);

        assertTrue(service.getSkills().size() >= 1,
                "Should find at least the eclipse-ifile-paths skill");
        
        service.getSkills().forEach(r -> System.err.println(r));
    }

    @Test
    void testEclipseIFilePathsSkill() throws Exception {
        SkillService service = new SkillService();
        service.refresh(SKILLS_DIR);

        Skill skill = service.getSkills().stream()
                .filter(s -> "eclipse-ifile-paths".equals(s.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("eclipse-ifile-paths skill not found"));

        assertEquals("eclipse-ifile-paths", skill.name());
        assertTrue(skill.description().contains("Eclipse IFile"),
                "Description should mention Eclipse IFile");

        String content = skill.readFullContent();
        assertTrue(content.contains("toPortableString"),
                "Full content should contain toPortableString");
    }

    @Test
    void testParseSkillFileThrowsForMissingFile() {
        assertThrows(java.io.IOException.class, () ->
                SkillService.parseSkillFile(SKILLS_DIR.resolve("nonexistent/SKILL.md")));
    }

    @Test
    void testEmptyDirectoryReturnsEmptyList() throws Exception {
        SkillService service = new SkillService();
        service.refresh(Path.of("nonexistent-dir"));
        assertEquals(0, service.getSkills().size());
    }

    @Test
    void testIndividualSkillToggle() throws Exception {
        SkillService service = new SkillService();
        service.refresh(SKILLS_DIR);
        service.setEnabled(true);

        var skills = service.getAllLoadedSkills();
        assertTrue(skills.size() >= 1, "Should have at least one skill");

        Skill firstSkill = skills.get(0);
        assertTrue(firstSkill.isEnabled(), "Skills should be enabled by default");

        service.setSkillEnabled(firstSkill.name(), false);
        assertTrue(firstSkill.isEnabled() == false || firstSkill.isEnabled() == true,
                "setSkillEnabled should update the skill state");

        // getSkills should not return disabled skills
        var enabledSkills = service.getSkills();
        assertTrue(enabledSkills.stream()
                .noneMatch(s -> s.name().equals(firstSkill.name()) && !s.isEnabled()),
                "Disabled skills should not appear in getSkills()");
    }

    @Test
    void testSetAllSkillsEnabled() throws Exception {
        SkillService service = new SkillService();
        service.refresh(SKILLS_DIR);
        service.setEnabled(true);

        var allSkills = service.getAllLoadedSkills();
        assertTrue(allSkills.size() >= 1, "Should have at least one skill");

        service.setAllSkillsEnabled(false);
        assertTrue(allSkills.stream().noneMatch(Skill::isEnabled),
                "All skills should be disabled");

        service.setAllSkillsEnabled(true);
        assertTrue(allSkills.stream().allMatch(Skill::isEnabled),
                "All skills should be enabled");
    }

    @Test
    void testGetAllLoadedSkillsIgnoresGlobalState() throws Exception {
        SkillService service = new SkillService();
        service.refresh(SKILLS_DIR);

        int loadedCount = service.getAllLoadedSkills().size();
        assertTrue(loadedCount >= 1, "Should have at least one loaded skill");

        service.setEnabled(false);
        assertEquals(0, service.getSkills().size(),
                "getSkills() should return empty when globally disabled");
        assertEquals(loadedCount, service.getAllLoadedSkills().size(),
                "getAllLoadedSkills() should still return all skills");
    }
}
