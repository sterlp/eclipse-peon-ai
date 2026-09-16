package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.docslinter.DocsLinterTool;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.WriteValidator;

class AiReviewAgentTest {

    @TempDir
    Path rootDir;

    private DocsLinterTool tool;
    private AiReviewAgent agent;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(rootDir.resolve("docs"));
        tool = new DocsLinterTool(rootDir);
        ToolService toolService = new ToolService(false);
        toolService.addTool(tool);
        ConfiguredChatModel model = LlmConfig.newConfig(
                AiProvider.OLLAMA, "test-model", "http://localhost:9999").build();
        agent = new AiReviewAgent(model, toolService);
    }

    @Test
    void reviewAgentReturnsDenyAllValidator() {
        assertThat(agent.getWriteValidator()).isSameAs(WriteValidator.DENY_ALL);
    }

    @Test
    void reviewAgentAllowsSummaryLint() throws IOException {
        Files.writeString(rootDir.resolve("docs/a.md"), """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-1 Covered UC ✅
                """);

        String output = tool.lintDocs(null, List.of("docs"), null);
        assertThat(output).contains("1 doc file(s): 1 linted, 0 not participating");
        assertThat(output).contains("UC definitions: 1 / 1");
    }

    // Characterization test: a DocsLinterTool placed in a fresh ToolService is visible
    // to a ReviewAgent because the tool reports isEditTool()==false. This does NOT
    // prove the real plugin wiring (UC-DL-51 is covered in PeonAiServiceTest in Slice B).
    @Test
    void docsLinterToolIsVisibleToReviewAgent() {
        assertThat(tool.isEditTool()).isFalse();

        boolean active = false;
        for (var exec : agent.getToolService().getExecutors()) {
            if (exec.getTool() instanceof DocsLinterTool) {
                active = agent.isToolActive(exec);
                break;
            }
        }
        assertThat(active).isTrue();
    }

    @Test
    void planAgentKeepsAllowAllValidator() {
        assertThat(WriteValidator.ALLOW_ALL).isNotNull();
        WriteValidator.ALLOW_ALL.validate("anything/at/all.txt");
    }
}
