package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;

import org.junit.Test;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.tools.EclipseBuildTool;
import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;
import org.sterl.llmpeon.parts.tools.EclipseGrepTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

public class EclipseWorkspaceReadFileToolTest extends AbstractIntegrationTest {

    @Test
    public void test_findReferences() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var tool = new EclipseCodeNavigationTool();

        var content = tool.eclipseFindReferences(
                "org.sterl.fixture", "Alpha", null, PeonTestFixture.PROJECT_NAME);

        assertContains(content, "Beta.java");
        assertContains(content, "5");
    }

    @Test
    public void testList() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var tool = new EclipseBuildTool();

        var result = tool.eclipseListAllOpenProjects();

        assertTrue("Fixture project not found:\n" + result, result.contains(PeonTestFixture.PROJECT_NAME));
    }

    @Test
    public void test_getTypeSource() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var tool = new EclipseCodeNavigationTool();

        var content = tool.eclipseReadTypeSource(
                "org.sterl.fixture", "Alpha", PeonTestFixture.PROJECT_NAME);

        assertContains(content, "public String hello()");
        assertContains(content, " 1: ");
        assertContains(content, "Alpha.java");
    }

    @Test
    public void test_getTypeSource_wrong_package() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var tool = new EclipseCodeNavigationTool();

        var content = tool.eclipseReadTypeSource(
                "foo.bar", "Alpha", PeonTestFixture.PROJECT_NAME);

        assertContains(content, "Alpha.java");
    }

    @Test
    public void readUtf8() throws Exception {
        var tool = new EclipseWorkspaceReadFileTool();

        var content = tool.eclipseReadFile(
                JdtUtil.pathOf(project) + "/data/utf-8-test.txt", null, null);

        assertEquals("   1: äüß Ö ⚡\n", content);
    }

    @Test
    public void readIso() throws Exception {
        var tool = new EclipseWorkspaceReadFileTool();

        var content = tool.eclipseReadFile(
                JdtUtil.pathOf(project) + "/data/iso-test.txt", null, null);

        assertEquals("   1: äüß Ö\n", content);
    }

    @Test
    public void test_grepWorkspaceFiles() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var tool = new EclipseGrepTool();

        var content = tool.eclipseGrepFiles(
                "grepMe", PeonTestFixture.PROJECT_NAME, ".java");

        assertTrue("Should contain fixture grep target:\n" + content,
                content.contains("GrepTarget.java"));
    }

    @Test
    public void test_grepWorkspaceFiles_regexPattern() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var tool = new EclipseGrepTool();

        var content = tool.eclipseGrepFiles(
                "class.*Alpha", PeonTestFixture.PROJECT_NAME, ".java");

        assertTrue("Regex should match fixture class declaration:\n" + content,
                content.contains("Alpha.java"));
    }

    @Test
    public void test_grepWorkspaceFiles_regexAlternation() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var tool = new EclipseGrepTool();

        var content = tool.eclipseGrepFiles(
                "class AlphaBeta|class Alphabet", PeonTestFixture.PROJECT_NAME, ".java");

        assertTrue("Regex alternation should match fixture classes:\n" + content,
                content.contains("AlphaBeta.java") && content.contains("Alphabet.java"));
    }

    @Test
    public void test_readWorkspaceFiles() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        ToolService service = new ToolService();
        service.addTool(new EclipseWorkspaceReadFileTool());

        var request = ToolExecutionRequest.builder()
            .name("eclipseReadFile")
            .arguments("{\"filePath\": \"/test_project/data/lines-120.txt\"}")
            .build();

        var content = service.execute(request,
                ToolLoopRequest.builder()
                    .memory(new ThreadSafeMemory())
                    .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo")))
                    .build());

        assertContains(content.text(), "line 1");
        assertContains(content.text(), "line 120");
    }

    @Test
    public void readFileOnToolLineStartsWithScrollSymbol() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        ToolService service = new ToolService();
        service.addTool(new EclipseWorkspaceReadFileTool());

        var lines = new ArrayList<String>();
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onTool(String message) { lines.add(message); }
        };

        var request = ToolExecutionRequest.builder()
            .name("eclipseReadFile")
            .arguments("{\"filePath\": \"" + JdtUtil.pathOf(project) + "/data/lines-120.txt\"}")
            .build();

        var content = service.execute(request,
                ToolLoopRequest.builder()
                    .memory(new ThreadSafeMemory())
                    .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo")))
                    .monitor(monitor)
                    .build());

        assertContains(content.text(), "line 1");
        assertEquals(1, lines.size());
        assertTrue("onTool line should start with the scroll symbol: " + lines.get(0),
                lines.get(0).startsWith("📜 "));
    }
}
