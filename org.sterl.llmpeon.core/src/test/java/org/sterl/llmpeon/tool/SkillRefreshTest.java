package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.skill.SkillService;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

class SkillRefreshTest {

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
    void addingSkillFileUpdatesToolList() throws Exception {
        var specsBefore = toolService.toolSpecifications();
        var skillCountBefore = specsBefore.stream().filter(s -> s.name().startsWith("readSkill")).count();

        createSkill("new-skill", "A newly added skill.");
        skillService.refresh(tempDir);

        var specsAfter = toolService.toolSpecifications();
        var skillCountAfter = specsAfter.stream().filter(s -> s.name().startsWith("readSkill")).count();

        assertThat(skillCountAfter).isEqualTo(skillCountBefore + 1);
        assertThat(specsAfter).anyMatch(s -> s.name().equals("readSkillnew-skill"));
    }

    @Test
    void removingSkillFileUpdatesToolList() throws Exception {
        createSkill("removable", "A skill that will be removed.");
        skillService.refresh(tempDir);

        var specsBefore = toolService.toolSpecifications();
        assertThat(specsBefore).anyMatch(s -> s.name().equals("readSkillremovable"));

        Files.deleteIfExists(tempDir.resolve("removable").resolve("SKILL.md"));
        skillService.refresh(tempDir);

        var specsAfter = toolService.toolSpecifications();
        assertThat(specsAfter).noneMatch(s -> s.name().equals("readSkillremovable"));
    }

    @Test
    void modifyingSkillContentIsReflectedOnNextExecution() throws Exception {
        createSkill("modifiable", "A modifiable skill.", "Original content.");
        skillService.refresh(tempDir);

        var request = ToolExecutionRequest.builder().id("req-1").name("readSkillmodifiable").arguments("").build();
        var resultBefore = toolService.execute(request, null, null, java.util.Collections.emptyList());
        assertThat(resultBefore.message().text()).contains("Original content");

        var skillFile = tempDir.resolve("modifiable").resolve("SKILL.md");
        Files.writeString(skillFile, "---\nname: modifiable\ndescription: A modifiable skill.\n---\n\n# modifiable\n\nUpdated content.");
        skillService.refresh(tempDir);

        var resultAfter = toolService.execute(request, null, null, java.util.Collections.emptyList());
        assertThat(resultAfter.message().text()).contains("Updated content");
        assertThat(resultAfter.message().text()).doesNotContain("Original content");
    }

    @Test
    void toolListReflectsCurrentSkillStateWithoutReRegistration() throws Exception {
        createSkill("skill-a", "Skill A.");
        skillService.refresh(tempDir);

        var specs1 = toolService.toolSpecifications();
        assertThat(specs1).anyMatch(s -> s.name().equals("readSkillskill-a"));

        createSkill("skill-b", "Skill B.");
        skillService.refresh(tempDir);

        var specs2 = toolService.toolSpecifications();
        assertThat(specs2).anyMatch(s -> s.name().equals("readSkillskill-a"));
        assertThat(specs2).anyMatch(s -> s.name().equals("readSkillskill-b"));

        // No need to re-add provider — tools are discovered dynamically
        assertThat(specs2.stream().filter(s -> s.name().startsWith("readSkill")).count()).isEqualTo(2);
    }

    private void createSkill(String name, String description) throws IOException {
        createSkill(name, description, "Default body.");
    }

    private void createSkill(String name, String description, String body) throws IOException {
        var skillDir = tempDir.resolve(name);
        Files.createDirectories(skillDir);
        var skillFile = skillDir.resolve("SKILL.md");
        Files.writeString(skillFile, "---\nname: " + name + "\ndescription: " + description + "\n---\n\n# " + name + "\n\n" + body);
    }
}
