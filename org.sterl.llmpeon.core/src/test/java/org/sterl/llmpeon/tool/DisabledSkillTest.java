package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.shared.AiMonitor;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

class DisabledSkillTest {

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
    void globallyDisabledSkillServiceHidesAllTools() throws Exception {
        createSkill("formatting", "Code formatting rules.");
        createSkill("testing", "Unit test guidelines.");
        skillService.refresh(tempDir);

        var specsEnabled = toolService.toolSpecifications();
        var skillCountEnabled = specsEnabled.stream().filter(s -> s.name().startsWith("readSkill")).count();
        assertThat(skillCountEnabled).isEqualTo(2);

        skillService.setEnabled(false);

        var specsDisabled = toolService.toolSpecifications();
        var skillCountDisabled = specsDisabled.stream().filter(s -> s.name().startsWith("readSkill")).count();
        assertThat(skillCountDisabled).isEqualTo(0);
    }

    @Test
    void individuallyDisabledSkillDoesNotAppearAsTool() throws Exception {
        createSkill("formatting", "Code formatting rules.");
        createSkill("testing", "Unit test guidelines.");
        skillService.refresh(tempDir);

        var specsBefore = toolService.toolSpecifications();
        assertThat(specsBefore).anyMatch(s -> s.name().equals("readSkillformatting"));
        assertThat(specsBefore).anyMatch(s -> s.name().equals("readSkilltesting"));

        skillService.setSkillEnabled("formatting", false);

        var specsAfter = toolService.toolSpecifications();
        assertThat(specsAfter).noneMatch(s -> s.name().equals("readSkillformatting"));
        assertThat(specsAfter).anyMatch(s -> s.name().equals("readSkilltesting"));
    }

    @Test
    void setAllSkillsEnabledFalseHidesAllTools() throws Exception {
        createSkill("formatting", "Code formatting rules.");
        createSkill("testing", "Unit test guidelines.");
        skillService.refresh(tempDir);

        var specsBefore = toolService.toolSpecifications();
        assertThat(specsBefore.stream().filter(s -> s.name().startsWith("readSkill")).count()).isEqualTo(2);

        skillService.setAllSkillsEnabled(false);

        var specsAfter = toolService.toolSpecifications();
        assertThat(specsAfter).noneMatch(s -> s.name().startsWith("readSkill"));
    }

    @Test
    void reEnablingSkillRestoresTool() throws Exception {
        createSkill("formatting", "Code formatting rules.");
        skillService.refresh(tempDir);

        skillService.setSkillEnabled("formatting", false);
        var specsDisabled = toolService.toolSpecifications();
        assertThat(specsDisabled).noneMatch(s -> s.name().equals("readSkillformatting"));

        skillService.setSkillEnabled("formatting", true);
        var specsEnabled = toolService.toolSpecifications();
        assertThat(specsEnabled).anyMatch(s -> s.name().equals("readSkillformatting"));
    }

    @Test
    void executingDisabledSkillToolReturnsUnknownToolError() throws Exception {
        createSkill("formatting", "Code formatting rules.");
        skillService.refresh(tempDir);

        skillService.setSkillEnabled("formatting", false);

        var request = ToolExecutionRequest.builder().id("req-1").name("readSkillformatting").arguments("").build();
        var result = toolService.execute(request, AiMonitor.NULL_MONITOR, null, Collections.emptyList());

        assertThat(result.message().text()).contains("unknown tool");
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
