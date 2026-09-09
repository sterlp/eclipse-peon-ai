package org.sterl.llmpeon.scaffold;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;

class AiScaffoldAgentTest {

    @Test
    void getAgentModelName_returnsNull() throws Exception {
        // GIVEN
        var tmpDir = Files.createTempDirectory("scaffold-test");
        var config = LlmConfig.builder()
                .providerType(AiProvider.OLLAMA)
                .model("qwen3")
                .url("http://localhost:9999")
                .configDir(tmpDir)
                .build();
        var subject = new AiScaffoldAgent(config.build());

        // WHEN / THEN — inherits null from interface default
        assertThat(subject.getAgentModelName()).isNull();
    }

    @Test
    void setAgentModelName_returnsFalse() throws Exception {
        // GIVEN
        var tmpDir = Files.createTempDirectory("scaffold-test");
        var config = LlmConfig.builder()
                .providerType(AiProvider.OLLAMA)
                .model("qwen3")
                .url("http://localhost:9999")
                .configDir(tmpDir)
                .build();
        var subject = new AiScaffoldAgent(config.build());

        // WHEN / THEN — inherits no-op from interface default
        assertThat(subject.setAgentModelName("gpt-4")).isFalse();
    }

    @Test
    void writeValidatorAllowsConfigAndProjectSkillsOnly() throws Exception {
        // GIVEN a scaffold whose write roots are the config dir + two open projects' .agents/skills (R12)
        var configDir = Files.createTempDirectory("scaffold-config");
        var projectA = Files.createTempDirectory("project-a");
        var projectB = Files.createTempDirectory("project-b");
        var config = LlmConfig.builder()
                .providerType(AiProvider.OLLAMA)
                .model("qwen3")
                .url("http://localhost:9999")
                .configDir(configDir)
                .build();
        var subject = new AiScaffoldAgent(config.build());
        subject.setProjectSkillsRootsSupplier(() -> List.of(
                projectA.resolve(SkillService.PROJECT_SKILLS_DIR),
                projectB.resolve(SkillService.PROJECT_SKILLS_DIR)));
        var validator = subject.getWriteValidator();

        // WHEN / THEN — the config dir and both project skill dirs are writable
        assertThatCode(() -> validator.validate(configDir.resolve("skills/x/SKILL.md").toString()))
                .as("config dir is allowed")
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(projectA.resolve(SkillService.PROJECT_SKILLS_DIR).resolve("x").resolve("SKILL.md").toString()))
                .as("project A skill dir is allowed")
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(projectB.resolve(SkillService.PROJECT_SKILLS_DIR).resolve("y").resolve("SKILL.md").toString()))
                .as("project B skill dir is allowed")
                .doesNotThrowAnyException();

        // ... but nothing else: no project source dirs, no traversal, no foreign absolute paths
        assertThatThrownBy(() -> validator.validate(projectA.resolve("src").resolve("X.java").toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Write denied")
                .hasMessageContaining(projectA.toString());
        assertThatThrownBy(() -> validator.validate("../secret"))
                .as("relative traversal beyond the config dir is denied")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(Files.createTempDirectory("foreign").resolve("x.md").toString()))
                .as("foreign absolute paths are denied")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void successfulWriteTriggersSkillRefreshAll() throws Exception {
        // GIVEN a scaffold wired to a SkillService pointing at the config skills dir (R15)
        var configDir = Files.createTempDirectory("scaffold-r15");
        var config = LlmConfig.builder()
                .providerType(AiProvider.OLLAMA)
                .model("qwen3")
                .url("http://localhost:9999")
                .configDir(configDir)
                .build();
        var skillService = new SkillService();
        skillService.refresh(configDir.resolve("skills"));
        var subject = new AiScaffoldAgent(config.build(), skillService);
        var writeTool = subject.getToolService().getTool(DiskFileWriteTool.class).orElseThrow();

        // WHEN the scaffold writes a new skill into the config skills dir
        writeTool.diskWriteFile(configDir.resolve("skills").resolve("new-skill.md").toString(), """
                ---
                name: new-skill
                description: written by scaffold
                ---
                body
                """);

        // THEN the service view contains the skill — without any reload call
        assertThat(skillService.get("new-skill")).isPresent();

        // AND a failed write leaves the view untouched (afterWrite never fires on error)
        var count = skillService.loadedSkillCount();
        assertThatThrownBy(() -> writeTool.diskWriteFile("", "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(skillService.loadedSkillCount()).isEqualTo(count);
    }

}