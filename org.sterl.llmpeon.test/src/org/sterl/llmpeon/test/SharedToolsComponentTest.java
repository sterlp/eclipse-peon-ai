package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.stream.Collectors;

import org.junit.Test;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.command.CommandService;
import org.sterl.llmpeon.docslinter.DocsIdTool;
import org.sterl.llmpeon.docslinter.DocsLinterTool;
import org.sterl.llmpeon.parts.ai.component.SharedToolsComponent;
import org.sterl.llmpeon.parts.tools.EclipseBuildTool;
import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;
import org.sterl.llmpeon.parts.tools.EclipseConsoleLogTool;
import org.sterl.llmpeon.parts.tools.EclipseGrepTool;
import org.sterl.llmpeon.parts.tools.EclipseRunTestTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceWriteFileTool;
import org.sterl.llmpeon.parts.tools.debug.JavaDebugTool;
import org.sterl.llmpeon.parts.tools.memory.WorkspaceMemoryTool;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import org.sterl.llmpeon.tool.tools.DiskGrepTool;
import org.sterl.llmpeon.tool.tools.SearchAgentTool;
import org.sterl.llmpeon.tool.tools.WebGetTool;

/**
 * Inc 1 (PeonAiService-Struktur-Aufräumen): SharedToolsComponent — tool registration,
 * SearchAgentTool privilege filter and the disk-tool toggle, without LLM.
 */
public class SharedToolsComponentTest {

    private final SharedToolsComponent sut = new SharedToolsComponent(new SkillService(), new CommandService());

    /** GIVEN the component WHEN reading the shared tool service THEN every Eclipse tool is registered exactly once. */
    @Test
    public void test_sharedTools_registersAllEclipseTools() {
        // GIVEN / WHEN
        ToolService ts = sut.toolService();

        // THEN — the tool instance is registered (a tool may expose several @Tool methods)
        assertTrue(countExecutors(ts, WorkspaceMemoryTool.class) >= 1);
        assertTrue(countExecutors(ts, EclipseWorkspaceReadFileTool.class) >= 1);
        assertTrue(countExecutors(ts, EclipseWorkspaceWriteFileTool.class) >= 1);
        assertTrue(countExecutors(ts, EclipseGrepTool.class) >= 1);
        assertTrue(countExecutors(ts, EclipseBuildTool.class) >= 1);
        assertTrue(countExecutors(ts, JavaDebugTool.class) >= 1);
        assertTrue(countExecutors(ts, EclipseRunTestTool.class) >= 1);
        assertTrue(countExecutors(ts, EclipseCodeNavigationTool.class) >= 1);
        assertTrue(countExecutors(ts, EclipseConsoleLogTool.class) >= 1);

        // AND: the search sub-agent is registered
        assertTrue(ts.getTool(SearchAgentTool.class).isPresent());
    }

    private long countExecutors(ToolService ts, Class<?> type) {
        return ts.getExecutors().stream().filter(e -> type.isInstance(e.getTool())).count();
    }

    /** GIVEN the SearchAgentTool WHEN its filter runs THEN privileged tools are excluded, normal ones stay visible. */
    @Test
    public void test_sharedTools_searchAgentFilter_excludesAskUserAndMemory() {
        // GIVEN
        var searchAgent = sut.toolService().getTool(SearchAgentTool.class).orElseThrow();

        // WHEN / THEN — the memory write tool must not leak into search agents
        var memory = sut.toolService().getExecutor("memoryAdd");
        assertTrue("memoryAdd executor expected", memory != null);
        assertFalse("WorkspaceMemoryTool must be filtered out of search agents",
                searchAgent.getFilter().test(memory));

        // AND: normal tools stay visible to search agents
        var grep = sut.toolService().getExecutor("eclipseGrepFiles");
        assertTrue("grep stays visible to search agents", searchAgent.getFilter().test(grep));
    }

    /** GIVEN default config (disk tools disabled) WHEN the shared service is built THEN the full DocsLinterTool is present. */
    // UC-DL-52
    @Test
    public void docsLinterExistsWhenDiskToolsDisabled() {
        // GIVEN / WHEN (default LlmConfig has diskToolsEnabled=false)
        var ts = sut.toolService();

        // THEN
        assertTrue("DocsLinterTool must be registered unconditionally",
                ts.getTool(DocsLinterTool.class).isPresent());
        // AND the ID facade must NOT leak into the shared service
        assertTrue("DocsIdTool must not be registered in the shared service",
                ts.getTool(DocsIdTool.class).isEmpty());
    }

    /** UC-DL-53: GIVEN enabled disk tools WHEN toggled back to disabled THEN shared docs-linter executors are unchanged. */
    // UC-DL-53
    @Test
    public void diskToggleDoesNotChangeDocsLinter() {
        // GIVEN: snapshot the docs-linter tool names in the default (disk-off) state
        var before = docsLinterNames();
        assertTrue("linter tools expected before toggle", before.contains("lintDocs"));
        assertTrue("linter tools expected before toggle", before.contains("lintDocsAndTests"));

        // WHEN: enable then disable disk tools
        sut.updateActiveDiskTools(config(true));
        sut.updateActiveDiskTools(config(false));

        // THEN: docs-linter names are unchanged
        var after = docsLinterNames();
        assertEquals(before, after);

        // AND disk tools are gone
        assertFalse(sut.toolService().getTool(DiskFileWriteTool.class).isPresent());
        assertFalse(sut.toolService().getTool(DiskFileReadTool.class).isPresent());
        assertFalse(sut.toolService().getTool(DiskGrepTool.class).isPresent());
    }

    /** GIVEN enabled/disabled config WHEN updateActiveDiskTools THEN disk tools toggle on/off without duplicates. */
    @Test
    public void test_updateActiveDiskTools_togglesDiskTools() {
        // GIVEN disabled (default)
        assertFalse(sut.toolService().getTool(DiskFileWriteTool.class).isPresent());

        // WHEN enabled
        sut.updateActiveDiskTools(config(true));

        // THEN all three disk tools are registered
        assertTrue(sut.toolService().getTool(DiskFileWriteTool.class).isPresent());
        assertTrue(sut.toolService().getTool(DiskFileReadTool.class).isPresent());
        assertTrue(sut.toolService().getTool(DiskGrepTool.class).isPresent());

        // AND: enabling twice does not duplicate them
        sut.updateActiveDiskTools(config(true));
        long writeExecutors = countExecutors(sut.toolService(), DiskFileWriteTool.class);
        assertTrue("no duplicates expected", writeExecutors == sut.toolService().getExecutors().stream()
                .filter(e -> e.getTool() instanceof DiskFileWriteTool)
                .map(e -> e.getSpec().name())
                .distinct()
                .count());

        // WHEN disabled again
        sut.updateActiveDiskTools(config(false));

        // THEN they are removed
        assertFalse(sut.toolService().getTool(DiskFileWriteTool.class).isPresent());
        assertFalse(sut.toolService().getTool(DiskFileReadTool.class).isPresent());
        assertFalse(sut.toolService().getTool(DiskGrepTool.class).isPresent());
    }

    /** GIVEN default config (disk tools disabled) WHEN toggling THEN webGet follows the disk tools (R-WEB-8, default OFF). */
    @Test
    public void test_webGetFollowsDiskToolToggle() {
        // GIVEN default: disk tools disabled
        assertFalse(sut.toolService().getTool(WebGetTool.class).isPresent());

        // WHEN enabled
        sut.updateActiveDiskTools(config(true));
        assertTrue(sut.toolService().getTool(WebGetTool.class).isPresent());

        // WHEN disabled again
        sut.updateActiveDiskTools(config(false));
        assertFalse(sut.toolService().getTool(WebGetTool.class).isPresent());
    }

    // UC-WEB-8
    @Test
    public void webGetFilteredFromSearchAgent() {
        // GIVEN webGet registered (disk tools enabled) in the production wiring
        sut.updateActiveDiskTools(config(true));
        var webGet = sut.toolService().getExecutor("webGet");
        assertTrue("webGet executor expected", webGet != null);
        var searchAgent = sut.toolService().getTool(SearchAgentTool.class).orElseThrow();

        // THEN the isEditTool filter excludes it from search agents
        assertFalse("webGet (isEditTool) must be filtered out of search agents",
                searchAgent.getFilter().test(webGet));
    }

    /** GIVEN JavaDebugTool registered WHEN the edit-tool filter matrix runs THEN read-only agents exclude it (R-JD-5). */
    @Test
    public void javaDebugToolIsEditToolFilteredFromReadOnlyAgents() {
        var ts = sut.toolService();
        var tool = ts.getTool(JavaDebugTool.class).orElseThrow();
        assertTrue("JavaDebugTool must be an edit tool", tool.isEditTool());

        var getState = ts.getExecutor("get_state");
        assertTrue("get_state executor expected", getState != null);
        var searchAgent = sut.toolService().getTool(SearchAgentTool.class).orElseThrow();

        // THEN the isEditTool filter excludes it from search agents
        // (AiPlanAgent/AiReviewAgent/CustomAgent apply the same !isEditTool predicate)
        assertFalse("JavaDebugTool (isEditTool) must be filtered out of search agents",
                searchAgent.getFilter().test(getState));
    }

    private java.util.Set<String> docsLinterNames() {
        return sut.toolService().getExecutors().stream()
                .filter(e -> e.getTool() instanceof DocsLinterTool)
                .map(e -> e.getSpec().name())
                .collect(Collectors.toSet());
    }

    private static LlmConfig config(boolean diskEnabled) {
        return LlmConfig.builder()
                .model("test")
                .url("http://localhost:0")
                .build()
                .toBuilder()
                .diskToolsEnabled(diskEnabled)
                .build();
    }
}
