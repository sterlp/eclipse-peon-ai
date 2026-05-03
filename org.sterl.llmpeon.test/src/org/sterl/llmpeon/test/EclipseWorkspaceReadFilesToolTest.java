package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;

import org.junit.Test;
import org.sterl.llmpeon.parts.tools.EclipseBuildTool;
import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;
import org.sterl.llmpeon.parts.tools.EclipseGrepTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;

public class EclipseWorkspaceReadFilesToolTest extends AbstractTest {

    @Test
    public void testList() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        var tool = new EclipseBuildTool();
        
        // WHEN
        var result = tool.listAllOpenEclipseProjects();
        
        // THEN
        assertTrue("Own project not found:\n" + result, result.contains("Project name: org.sterl.llmpeon.test"));
    }
    
    @Test
    public void test_getTypeSource() throws Exception {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var tool = new EclipseCodeNavigationTool();

        // WHEN
        var content = tool.getTypeSource(EclipseWorkspaceReadFilesToolTest.class.getName(), "org.sterl.llmpeon.test");
        // THEN
        assertTrue("Expected to read own source, got: " + content.substring(0, Math.min(200, content.length())),
                content.contains("public void testTypeSearch()"));
    }

    @Test
    public void searchAndReadSelf() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        var tool = new EclipseWorkspaceReadFileTool();

        // 1. find this file by name
        String searchResult = tool.searchWorkspaceFiles("EclipseWorkspaceReadFilesToolTest", 0);
        assertTrue("Expected to find the test file in workspace: " + searchResult,
                searchResult.contains(this.getClass().getSimpleName() + ".java"));

        String content = tool.readWorkspaceFile(searchResult.split("\n")[0], 0, 0);
        assertTrue("Expected to read own source, got: " + content.substring(0, Math.min(200, content.length())),
                content.contains("searchAndReadSelf"));
    }
    
    @Test
    public void searchWorkspaceFiles_limitRestrictsResults() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var tool = new EclipseWorkspaceReadFileTool();

        // unlimited should return multiple .java files
        String all = tool.searchWorkspaceFiles("*.java", 0);
        int allCount = all.split("\n").length;
        assertTrue("Expected more than 1 .java file", allCount > 1);

        // limit=1 must return exactly 1 result
        String limited = tool.searchWorkspaceFiles("*.java", 1);
        assertEquals("Expected exactly 1 result with limit=1", 1, limited.split("\n").length);
    }

    @Test
    public void test_grepWorkspaceFiles() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        
        var tool = new EclipseGrepTool();
        
        // WHEN
        var content = tool.grepWorkspaceFiles("test_grepWorkspaceFiles()", null, ".java");
        
        // THEN
        assertTrue("Should contain out test:\n" + content, content.contains(getClass().getSimpleName() + ".java"));
    }
    
    @Test
    public void test_findReferences() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        
        var tool = new EclipseCodeNavigationTool();
        
        // WHEN
        var content = tool.findReferences(EclipseWorkspaceReadFilesToolTest.class.getName(), null, null);
        
        // THEN
        assertTrue("Should contain out test:\n" + content, content.contains(getClass().getSimpleName() + ".java"));
        assertTrue("Should contain out line:\n" + content, content.contains("76"));
    }
    
    @Test
    public void test_readWorkspaceFiles() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        ToolService service = new ToolService();
        service.addTool(new EclipseWorkspaceReadFileTool());

        var tr = ToolExecutionRequest.builder().arguments("")
            .name("readWorkspaceFile")
            .arguments("{\"arg0\": \"" + this.getClass().getName().replace(".", "/") + ".java\"}")
            .build();
        
        // WHEN
        var content = service.execute(tr, null, null, new ArrayList<ChatMessage>());
        
        // THEN
        assertTrue(content.message().text().contains("Hallo meine schöne datei wie geht es dir?"));
    }
}
