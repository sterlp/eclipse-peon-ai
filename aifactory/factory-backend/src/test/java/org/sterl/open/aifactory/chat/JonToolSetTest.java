package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.AiReviewAgent;
import org.sterl.llmpeon.agent.NamedAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.docslinter.DocsIdTool;
import org.sterl.llmpeon.docslinter.DocsLinterTool;
import org.sterl.llmpeon.poagent.tools.PoDelegateTool;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.WriteValidator;
import org.sterl.llmpeon.tool.tools.CompactSessionTool;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import org.sterl.llmpeon.tool.tools.DiskGrepTool;
import org.sterl.llmpeon.tool.tools.SearchAgentTool;
import org.sterl.llmpeon.tool.tools.ShellTool;
import org.sterl.llmpeon.tool.web.WebFetchTool;
import org.sterl.llmpeon.tool.web.WebGetTool;
import org.sterl.llmpeon.tool.web.WebSearchTool;
import org.sterl.open.aifactory.shared.TmpRoots;

/**
 * The tool set of a project is what UC-WP-17 says about Jon, so it is verified here and not through a
 * turn: the same instance the runtime hands to the core is the one under test.
 */
class JonToolSetTest {

    /** The only core packages a factory tool may come from — an Eclipse tool has none of these. */
    private static final List<String> CORE_TOOL_PACKAGES = List.of(
            "org.sterl.llmpeon.tool.",
            "org.sterl.llmpeon.docslinter.",
            "org.sterl.llmpeon.poagent.tools.");

    @TempDir
    Path projectRoot;

    @Test
    // UC-WP-17
    void allCoreToolsNoEclipseTools() {
        Path root = projectRoot.toAbsolutePath().normalize();
        JonToolSet toolSet = toolSetWithTeam(root);
        ToolService tools = toolSet.tools();

        for (Class<? extends org.sterl.llmpeon.tool.tools.AbstractTool> coreTool : List.of(
                DiskFileReadTool.class, DiskFileWriteTool.class, DiskGrepTool.class,
                DocsLinterTool.class, DocsIdTool.class, WebGetTool.class,
                ShellTool.class, WebFetchTool.class, WebSearchTool.class,
                SearchAgentTool.class, CompactSessionTool.class, PoDelegateTool.class)) {
            assertThat(tools.getTool(coreTool))
                    .as("Jon must have the core tool %s", coreTool.getSimpleName())
                    .isPresent();
        }

        assertThat(tools.getExecutors())
                .as("nothing outside the core tool packages reached the model")
                .allSatisfy(executor -> {
                    String toolClass = executor.getTool().getClass().getName();
                    assertThat(CORE_TOOL_PACKAGES).anyMatch(toolClass::startsWith);
                    assertThat(toolClass).doesNotContain("Eclipse");
                    assertThat(toolClass).doesNotStartWith("org.sterl.llmpeon.parts");
                });

        assertThat(tools.getTool(DiskFileReadTool.class).orElseThrow().getWorkingDir()).isEqualTo(root);
        assertThat(tools.getTool(DiskFileWriteTool.class).orElseThrow().getWorkingDir()).isEqualTo(root);
        assertThat(tools.getTool(DocsLinterTool.class).orElseThrow().getWorkingDir()).isEqualTo(root);

        // the shell has no readable root — it discloses the one it used, read from the supplier at call time
        String run = tools.getTool(ShellTool.class).orElseThrow()
                .shellRunCommand("echo out", null, 20, null, null);
        assertThat(run).startsWith("cwd=" + root);
    }

    @Test
    void writeRootsAreProjectAndTmp() {
        Path root = projectRoot.toAbsolutePath().normalize();
        WriteValidator validator = JonToolSet.forProject(root).writeValidator();
        Path tmp = TmpRoots.systemTmp();

        validator.validate(root.resolve("a/b.md").toString());
        validator.validate(tmp.resolve("inc3a-probe.md").toString());

        assertThatThrownBy(() -> validator.validate("/etc/hosts"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(root.toString())
                .hasMessageContaining(tmp.toString());
        assertThatThrownBy(() -> validator.validate(root.resolve("..").resolve("..").resolve("escape").toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(root.toString());
    }

    /**
     * The delegation tool needs three real slaves, so the test builds the same team the runtime builds
     * — RAM-only memory, shared tool set, no model call.
     */
    private JonToolSet toolSetWithTeam(Path root) {
        JonToolSet toolSet = JonToolSet.forProject(root);
        ConfiguredChatModel model = new ConfiguredChatModel(LlmConfig.newOpenAi("test-model", "http://127.0.0.1:1/v1"));
        NamedAgent plan = new NamedAgent("Da Thinka", new AiPlanAgent(model, toolSet.tools()));
        NamedAgent review = new NamedAgent("Da Dok", new AiReviewAgent(model, toolSet.tools()));
        NamedAgent dev = new NamedAgent("Da Mek", new AiDevAgent(model, toolSet.tools()));
        toolSet.addDelegateTool(plan, review, dev, () -> 80_000);
        return toolSet;
    }
}
