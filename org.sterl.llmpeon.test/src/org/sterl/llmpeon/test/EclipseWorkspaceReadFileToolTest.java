package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.parts.tools.EclipseBuildTool;
import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;
import org.sterl.llmpeon.parts.tools.EclipseGrepTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

public class EclipseWorkspaceReadFileToolTest extends AbstractTest {

    @Test
    public void test_findReferences() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        
        var tool = new EclipseCodeNavigationTool();
        
        // WHEN
        var content = tool.eclipseFindReferences(EclipseWorkspaceReadFileToolTest.class.getPackageName(), 
                EclipseWorkspaceReadFileToolTest.class.getSimpleName(), null, null);
        
        // THEN
        assertContains(content, getClass().getSimpleName() + ".java");
        assertContains(content, "30");
    }

    @Test
    public void testList() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        var tool = new EclipseBuildTool();
        
        // WHEN
        var result = tool.eclipseListAllOpenProjects();
        
        // THEN
        assertTrue("Own project not found:\n" + result, result.contains("org.sterl.llmpeon.test"));
    }
    
    @Test
    public void test_getTypeSource() throws Exception {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var tool = new EclipseCodeNavigationTool();

        // WHEN
        var content = tool.eclipseReadTypeSource(
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
        var content = tool.eclipseReadTypeSource(
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
        String searchResult = tool.eclipseSearchFiles("EclipseWorkspaceReadFileToolTest", null, 0);
        assertTrue("Expected to find the test file in workspace: " + searchResult,
                searchResult.contains(this.getClass().getSimpleName() + ".java"));

        String content = tool.eclipseReadFile(searchResult.split("\n")[0], 0, 0);
        assertTrue("Expected to read own source, got: " + content.substring(0, Math.min(200, content.length())),
                content.contains("searchAndReadSelf"));
    }
    
    @Test
    public void searchWorkspaceFiles_limitRestrictsResults() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var tool = new EclipseWorkspaceReadFileTool();

        // unlimited should return multiple .java files
        String all = tool.eclipseSearchFiles("*.java", null, 0);
        int allCount = all.split("\n").length;
        assertTrue("Expected more than 1 .java file", allCount > 1);
        
        all = tool.eclipseSearchFiles("*.java", project.getName(), 0);
        allCount = all.split("\n").length;
        assertTrue("Expected more than 1 .java file", allCount > 1);

        // limit=1 must return exactly 1 result
        String limited = tool.eclipseSearchFiles("*.java", project.getName(), 1);
        assertEquals("Expected exactly 1 result with limit=1", 1, limited.split("\n").length);
    }

    @Test
    public void test_grepWorkspaceFiles() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        
        var tool = new EclipseGrepTool();
        
        // WHEN
        var content = tool.eclipseGrepFiles("test_grepWorkspaceFiles()", null, ".java");
        
        // THEN
        assertTrue("Should contain out test:\n" + content, content.contains(getClass().getSimpleName() + ".java"));
    }

    @Test
    public void test_grepWorkspaceFiles_regexPattern() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        
        var tool = new EclipseGrepTool();
        
        // WHEN - regex pattern that should match class declarations like "class EclipseGrepTool"
        var content = tool.eclipseGrepFiles("class.*Tool", null, ".java");
        
        // THEN - should find files with class declarations matching the pattern
        assertTrue("Regex pattern 'class.*Tool' should match class declarations:\n" + content, 
                content.contains("EclipseWorkspaceReadFileToolTest.java"));
    }

    @Test
    public void test_grepWorkspaceFiles_regexAlternation() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        
        var tool = new EclipseGrepTool();
        
        // WHEN - regex alternation pattern that should match multiple terms
        var content = tool.eclipseGrepFiles("JdtUtilDiskPathTest|PeonAiServiceTest", null, ".java");
        
        // THEN - should find files matching either term
        assertTrue("Regex alternation should match:\n" + content, 
                content.contains("JdtUtilDiskPathTest.java") 
                && content.contains("PeonAiServiceTest.java"));
    }

    
    @Test
    public void test_readWorkspaceFiles() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        ToolService service = new ToolService();
        service.addTool(new EclipseWorkspaceReadFileTool());

        var tr = ToolExecutionRequest.builder().arguments("")
            .name("eclipseReadFile")
            .arguments("{\"filePath\": \"" + this.getClass().getName().replace(".", "/") + ".java\"}")
            .build();
        
        // WHEN
        var content = service.execute(tr,
                ToolLoopRequest.builder()
                    .memory(new ThreadSafeMemory())
                    .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo")))
                    .build());
        
        // THEN
        assertContains(content.text(), 
                "Hallo meine schöne datei wie geht es dir?");
    }
}
