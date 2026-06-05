package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.StandingOrdersBuilder;
import org.sterl.llmpeon.skill.SkillService;

class DisabledSkillTest {

    @TempDir
    Path tempDir;

    StandingOrdersBuilder ordersBuilder;
    SkillService skillService;

    @BeforeEach
    void setUp() throws Exception {
        ordersBuilder = new StandingOrdersBuilder();
        skillService = new SkillService();
        skillService.refresh(tempDir);
        skillService.setEnabled(true);
        skillService.setAllSkillsEnabled(true);
        ordersBuilder.add(skillService);
    }
    
    @Test
    void testHasSkills() throws Exception {
        // GIVEN
        createSkill("formatting", "Code formatting rules.");
        createSkill("testing", "Unit test guidelines.");

        // WHEN
        skillService.refresh(tempDir);
        var orders = ordersBuilder.build();
        
        // AND
        assertThat(orders.getFirst()).contains("name: formatting");
        assertThat(orders.getFirst()).contains("name: testing");
    }


    @Test
    void testSkillsAreMissingIfDisabled() throws Exception {
        // GIVEN
        createSkill("formatting", "Code formatting rules.");
        createSkill("testing", "Unit test guidelines.");
        skillService.refresh(tempDir);

        // AND
        skillService.setEnabled(false);
        var orders = ordersBuilder.build();

        // WHEN
        assertThat(orders).isEmpty();

    }

    @Test
    void individuallyDisabledSkillDoesNotAppearAsTool() throws Exception {
        createSkill("formatting", "Code formatting rules.");
        createSkill("testing", "Unit test guidelines.");
        skillService.refresh(tempDir);

        // WHEN
        skillService.setSkillEnabled("formatting", false);
        var orders = ordersBuilder.build();

        // AND
        assertThat(orders.getFirst()).doesNotContain("name: formatting");
        assertThat(orders.getFirst()).contains("name: testing");
    }

    @Test
    void disabledSkillServiceStillTracksLoadedSkills() throws Exception {
        createSkill("formatting", "Code formatting rules.");
        skillService.refresh(tempDir);

        skillService.setEnabled(false);

        assertThat(skillService.getSkills()).isEmpty();
        assertThat(skillService.getAllLoadedSkills()).hasSize(1);
        assertThat(skillService.getAllLoadedSkills().get(0).name()).isEqualTo("formatting");
    }

    private void createSkill(String name, String description) throws IOException {
        var skillDir = tempDir.resolve(name);
        Files.createDirectories(skillDir);
        var skillFile = skillDir.resolve("SKILL.md");
        Files.writeString(skillFile, "---\nname: " + name + "\ndescription: " + description + "\n---\n\n# " + name + "\n\nBody content.");
    }
}
