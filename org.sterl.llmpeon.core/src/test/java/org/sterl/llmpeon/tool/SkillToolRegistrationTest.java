package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.skill.SkillService;

class SkillToolRegistrationTest {

    @TempDir
    Path tempDir;

    ToolService toolService;
    SkillService skillService;

    @BeforeEach
    void setUp() throws Exception {
        toolService = new ToolService();
        skillService = new SkillService();
        skillService.refresh(tempDir);
        skillService.setEnabled(true);
        skillService.setAllSkillsEnabled(true);
        toolService.addProvider(skillService);
    }

    @Test
    void skillAppearsAsIndividualTool() throws Exception {
        createSkill("formatting", "Enforces consistent code formatting rules.");

        skillService.refresh(tempDir);

        var specs = toolService.toolSpecifications();
        assertThat(specs).anyMatch(s -> s.name().equals("readSkillformatting"));
    }

    @Test
    void toolNameFollowsReadSkillPattern() throws Exception {
        createSkill("java-conventions", "Java coding conventions.");

        skillService.refresh(tempDir);

        var specs = toolService.toolSpecifications();
        var skillTool = specs.stream().filter(s -> s.name().startsWith("readSkill")).findFirst();
        assertThat(skillTool).isPresent();
        assertThat(skillTool.get().name()).isEqualTo("readSkilljava-conventions");
    }

    @Test
    void toolDescriptionComesFromFrontmatter() throws Exception {
        createSkill("testing", "Write unit tests for Java classes.");

        skillService.refresh(tempDir);

        var specs = toolService.toolSpecifications();
        var skillTool = specs.stream().filter(s -> s.name().equals("readSkilltesting")).findFirst();
        assertThat(skillTool).isPresent();
        assertThat(skillTool.get().description()).contains("unit tests");
    }

    @Test
    void multipleSkillsAppearAsMultipleTools() throws Exception {
        createSkill("formatting", "Code formatting rules.");
        createSkill("testing", "Unit test guidelines.");

        skillService.refresh(tempDir);

        var specs = toolService.toolSpecifications();
        var skillTools = specs.stream().filter(s -> s.name().startsWith("readSkill")).toList();
        assertThat(skillTools).hasSize(2);
    }

    @Test
    void noSkillsProducesNoSkillTools() {
        var specs = toolService.toolSpecifications();
        var skillTools = specs.stream().filter(s -> s.name().startsWith("readSkill")).toList();
        assertThat(skillTools).isEmpty();
    }

    private void createSkill(String name, String description) throws IOException {
        var skillDir = tempDir.resolve(name);
        Files.createDirectories(skillDir);
        var skillFile = skillDir.resolve("SKILL.md");
        Files.writeString(skillFile, "---\nname: " + name + "\ndescription: " + description + "\n---\n\n# " + name + "\n\nBody content.");
    }
}
