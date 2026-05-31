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

class DynamicToolExecutionTest {

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
    void executingSkillToolReturnsSkillBody() throws Exception {
        createSkill("formatting", "Code formatting rules.", "Always use 4 spaces for indentation.");

        skillService.refresh(tempDir);

        var request = ToolExecutionRequest.builder().id("req-1").name("readSkillformatting").arguments("").build();
        var result = toolService.execute(request, AiMonitor.NULL_MONITOR, null, Collections.emptyList());

        assertThat(result.message().text()).contains("Always use 4 spaces");
    }

    @Test
    void executingSkillToolIncludesPathPrefix() throws Exception {
        createSkill("testing", "Unit test guidelines.", "Write JUnit 5 tests.");

        skillService.refresh(tempDir);

        var request = ToolExecutionRequest.builder().id("req-1").name("readSkilltesting").arguments("").build();
        var result = toolService.execute(request, AiMonitor.NULL_MONITOR, null, Collections.emptyList());

        assertThat(result.message().text()).startsWith("Path:");
        assertThat(result.message().text()).contains("testing");
    }

    @Test
    void executingSkillToolPreservesFrontmatterSeparator() throws Exception {
        createSkill("conventions", "Coding conventions.", "Use camelCase for variables.");

        skillService.refresh(tempDir);

        var request = ToolExecutionRequest.builder().id("req-1").name("readSkillconventions").arguments("").build();
        var result = toolService.execute(request, AiMonitor.NULL_MONITOR, null, Collections.emptyList());

        assertThat(result.message().text()).contains("---");
    }

    @Test
    void executingUnknownSkillToolReturnsError() {
        var request = ToolExecutionRequest.builder().id("req-1").name("readSkillnonexistent").arguments("").build();
        var result = toolService.execute(request, AiMonitor.NULL_MONITOR, null, Collections.emptyList());

        assertThat(result.message().text()).contains("unknown tool");
    }

    @Test
    void toolResultMessageHasCorrectToolName() throws Exception {
        createSkill("formatting", "Code formatting.", "Rules.");

        skillService.refresh(tempDir);

        var request = ToolExecutionRequest.builder().id("req-1").name("readSkillformatting").arguments("").build();
        var result = toolService.execute(request, AiMonitor.NULL_MONITOR, null, Collections.emptyList());

        assertThat(result.message().toolName()).isEqualTo("readSkillformatting");
        assertThat(result.message().id()).isEqualTo("req-1");
    }

    private void createSkill(String name, String description, String body) throws IOException {
        var skillDir = tempDir.resolve(name);
        Files.createDirectories(skillDir);
        var skillFile = skillDir.resolve("SKILL.md");
        Files.writeString(skillFile, "---\nname: " + name + "\ndescription: " + description + "\n---\n\n# " + name + "\n\n" + body);
    }
}
