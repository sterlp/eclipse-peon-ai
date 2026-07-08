package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.AbstractMemoryFileTest;
import org.sterl.llmpeon.skill.SkillService;

class DisabledSkillTest extends AbstractMemoryFileTest {

    SkillService skillService;

    @BeforeEach
    void setUp() throws Exception {
        skillService = new SkillService();
        skillService.refresh(tmp);
        skillService.setEnabled(true);
        skillService.setAllSkillsEnabled(true);
    }
    
    @Test
    void testHasSkills() throws Exception {
        // GIVEN
        createSkill("formatting", "Code formatting rules.");
        createSkill("testing", "Unit test guidelines.");

        // WHEN
        skillService.refresh(tmp);
        
        // AND
        assertThat(skillService.skillNames()).contains("formatting");
        assertThat(skillService.skillNames()).contains("testing");
    }


    @Test
    void testSkillsAreMissingIfDisabled() throws Exception {
        // GIVEN
        createSkill("formatting", "Code formatting rules.");
        createSkill("testing", "Unit test guidelines.");
        skillService.refresh(tmp);

        // AND
        skillService.setEnabled(false);

        // WHEN
        assertThat(skillService.skillNames()).isEmpty();

    }

    @Test
    void individuallyDisabledSkillDoesNotAppearAsTool() throws Exception {
        createSkill("formatting", "Code formatting rules.");
        createSkill("testing", "Unit test guidelines.");
        skillService.refresh(tmp);

        // WHEN
        skillService.setSkillEnabled("formatting", false);

        // AND
        assertThat(skillService.skillNames()).doesNotContain("formatting");
        assertThat(skillService.skillNames()).contains("testing");
    }

    @Test
    void disabledSkillServiceStillTracksLoadedSkills() throws Exception {
        createSkill("formatting", "Code formatting rules.");
        skillService.refresh(tmp);

        skillService.setEnabled(false);

        assertThat(skillService.getSkills()).isEmpty();
        assertThat(skillService.getAllLoadedSkills()).hasSize(1);
        assertThat(skillService.getAllLoadedSkills().get(0).getName()).isEqualTo("formatting");
    }

    private void createSkill(String name, String description) throws IOException {
        var skillDir = tmp.resolve(name);
        Files.createDirectories(skillDir);
        var skillFile = skillDir.resolve("SKILL.md");
        Files.writeString(skillFile, "---\nname: " + name + "\ndescription: " + description + "\n---\n\n# " + name + "\n\nBody content.");
    }
}
