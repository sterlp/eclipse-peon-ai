package org.sterl.llmpeon.skill;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SkillServiceTest {

    // points to the project root skills/ folder
    private static final Path SKILLS_DIR = Path.of("../skills");

    @Test
    void testRefreshLoadsSkills() throws Exception {
        SkillService service = new SkillService(SKILLS_DIR);
        assertEquals(0, service.getSkills().size());

        service.refresh();

        assertTrue(service.getSkills().size() >= 1,
                "Should find at least the eclipse-ifile-paths skill");
        
        service.getSkills().forEach(r -> System.err.println(r));
    }

    @Test
    void testEclipseIFilePathsSkill() throws Exception {
        SkillService service = new SkillService(SKILLS_DIR);
        service.refresh();

        SkillRecord skill = service.getSkills().stream()
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
        SkillService service = new SkillService(Path.of("nonexistent-dir"));
        service.refresh();
        assertEquals(0, service.getSkills().size());
    }
}
