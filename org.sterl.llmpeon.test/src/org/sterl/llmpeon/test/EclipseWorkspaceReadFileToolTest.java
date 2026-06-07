package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;
import org.sterl.llmpeon.ai.ConfiguredModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.parts.tools.EclipseBuildTool;
import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;
import org.sterl.llmpeon.parts.tools.EclipseGrepTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

public class EclipseWorkspaceReadFileToolTest extends AbstractTest {

    @Test
    public void test_findReferences() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        
        var tool = new EclipseCodeNavigationTool();
        
        // WHEN
        var content = tool.findReferences(EclipseWorkspaceReadFileToolTest.class.getPackageName(), 
                EclipseWorkspaceReadFileToolTest.class.getSimpleName(), null, null);
        
        // THEN
        assertContains(content, getClass().getSimpleName() + ".java");
        assertContains(content, "29");
    }

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
        var content = tool.readTypeSource(
                getClass().getPackageName(), 
                getClass().getSimpleName(), 
                "org.sterl.llmpeon.test");
        // THEN
        assertContains(content, "public void test_getTypeSource()");
        assertContains(content, " 1: ");
        assertContains(content, getClass().getSimpleName() + ".java");
    }
    
    @Test
    public void test_getTypeSource_wrong_package() throws Exception {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var tool = new EclipseCodeNavigationTool();

        // WHEN
        var content = tool.readTypeSource(
                "foo.bar", 
                getClass().getSimpleName(), 
                "org.sterl.llmpeon.test");
        // THEN should return a list
        assertContains(content, getClass().getSimpleName() + ".java");
    }

    @Test
    public void searchAndReadSelf() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        var tool = new EclipseWorkspaceReadFileTool();

        // 1. find this file by name
        String searchResult = tool.searchWorkspaceFiles("EclipseWorkspaceReadFileToolTest", 0);
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
    public void test_grepWorkspaceFiles_regexPattern() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        
        var tool = new EclipseGrepTool();
        
        // WHEN - regex pattern that should match class declarations like "class EclipseGrepTool"
        var content = tool.grepWorkspaceFiles("class.*Tool", null, ".java");
        
        // THEN - should find files with class declarations matching the pattern
        assertTrue("Regex pattern 'class.*Tool' should match class declarations:\n" + content, 
                content.contains("EclipseGrepTool.java") || content.contains("EclipseWorkspaceReadFileTool.java"));
    }

    @Test
    public void test_grepWorkspaceFiles_regexAlternation() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        
        var tool = new EclipseGrepTool();
        
        // WHEN - regex alternation pattern that should match multiple terms
        var content = tool.grepWorkspaceFiles("EclipseGrepTool|EclipseWorkspaceReadFileTool", null, ".java");
        
        // THEN - should find files matching either term
        assertTrue("Regex alternation should match:\n" + content, 
                content.contains("EclipseGrepTool.java") || content.contains("EclipseWorkspaceReadFileTool.java"));
    }

    
    @Test
    public void test_readWorkspaceFiles() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        ToolService service = new ToolService();
        service.addTool(new EclipseWorkspaceReadFileTool());

        var tr = ToolExecutionRequest.builder().arguments("")
            .name("readWorkspaceFile")
            .arguments("{\"filePath\": \"" + this.getClass().getName().replace(".", "/") + ".java\"}")
            .build();
        
        // WHEN
        var content = service.execute(tr,
                ToolLoopRequest.builder()
                    .memory(MessageWindowChatMemory.withMaxMessages(10))
                    .model(new ConfiguredModel(LlmConfig.newOpenAi("foo")))
                    .build());
        
        // THEN
        assertContains(content.text(), 
                "Hallo meine schöne datei wie geht es dir?");
    }
}
