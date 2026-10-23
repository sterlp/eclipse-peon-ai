package org.sterl.llmpeon.scaffold;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.skill.SkillSource;
import org.sterl.llmpeon.tool.ToolLoopRequest;
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
        subject.setAdditionalWriteRootsSupplier(() -> List.of(
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

    // R10/R17
    @Test
    void writeValidatorAllowsUserAgentsRoot() throws Exception {
        // GIVEN a scaffold whose additional write root is the user skills root of a fake home
        var tmpHome = Files.createTempDirectory("scaffold-home");
        var configDir = Files.createDirectories(tmpHome.resolve(".peon"));
        var userRoot = tmpHome.resolve(SkillService.USER_SKILLS_DIR);
        var config = LlmConfig.builder()
                .providerType(AiProvider.OLLAMA)
                .model("qwen3")
                .url("http://localhost:9999")
                .configDir(configDir)
                .build();
        var subject = new AiScaffoldAgent(config.build());
        subject.setAdditionalWriteRootsSupplier(() -> List.of(userRoot));
        var validator = subject.getWriteValidator();

        // WHEN / THEN the user root is writable — the migration target of R17
        assertThatCode(() -> validator.validate(userRoot.resolve("deploy").resolve("SKILL.md").toString()))
                .as("the user .agents/skills root is allowed")
                .doesNotThrowAnyException();

        // ... but the root is a root, not the whole home dir: sibling tool dirs and loose files stay denied
        assertThatThrownBy(() -> validator.validate(tmpHome.resolve(".claude").resolve("skills").resolve("x.md").toString()))
                .as("a foreign home dir is still denied")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Write denied");
        assertThatThrownBy(() -> validator.validate(tmpHome.resolve("notes.md").toString()))
                .as("a loose file in the home dir is still denied")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Write denied");
    }

    // R17
    @Test
    void scaffoldWriteIntoUserAgentsRootWinsOverPeonOriginal() throws Exception {
        // GIVEN the skill deployed in the deprecated ~/.peon/skills, ~/.agents/skills not created yet
        var tmpHome = Files.createTempDirectory("scaffold-migration");
        var configDir = Files.createDirectories(tmpHome.resolve(".peon"));
        var legacySkills = Files.createDirectories(configDir.resolve("skills"));
        var userRoot = tmpHome.resolve(SkillService.USER_SKILLS_DIR);
        var legacyFile = Files.createDirectories(legacySkills.resolve("deploy")).resolve("SKILL.md");
        Files.writeString(legacyFile, skillFile("peon"));
        var config = LlmConfig.builder()
                .providerType(AiProvider.OLLAMA)
                .model("qwen3")
                .url("http://localhost:9999")
                .configDir(configDir)
                .build();
        var chatModel = config.build();
        var skillService = new SkillService(userRoot);
        skillService.refresh(legacySkills);
        var subject = new AiScaffoldAgent(chatModel, skillService);
        subject.setAdditionalWriteRootsSupplier(() -> List.of(userRoot));
        var writeTool = subject.getToolService().getTool(DiskFileWriteTool.class).orElseThrow();
        // the production request carries the agent's validator, so the write really goes through the gate
        writeTool.withToolRequest(ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(chatModel)
                .writeValidator(subject.getWriteValidator())
                .build());

        try {
            // WHEN the scaffold migrates the skill by writing it into the user root
            writeTool.diskWriteFile(userRoot.resolve("deploy").resolve("SKILL.md").toString(), skillFile("agents"));

            // THEN it is visible as a CONFIG skill and wins over the .peon original — with no reload
            // (the successful write refreshed every component, R15)
            var deploy = skillService.get("deploy").orElseThrow();
            assertThat(deploy.getSource()).isEqualTo(SkillSource.CONFIG);
            assertThat(deploy.getBody()).isEqualTo("agents");
            assertThat(skillService.loadedSkillCount()).isEqualTo(1);

            // AND the .peon original stays byte-identical — a copy, never a move
            assertThat(Files.readString(legacyFile)).isEqualTo(skillFile("peon"));
        } finally {
            deleteRecursively(tmpHome);
        }
    }

    private static String skillFile(String body) {
        return """
                ---
                name: deploy
                description: deploy the service
                ---
                %s
                """.formatted(body);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

}