package org.sterl.llmpeon.test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;
import org.sterl.llmpeon.parts.tools.EclipseBuildTool;
import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;
import org.sterl.llmpeon.parts.tools.EclipseGrepTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;

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
        String searchResult = tool.searchWorkspaceFiles("EclipseWorkspaceReadFilesToolTest");
        assertTrue("Expected to find the test file in workspace: " + searchResult,
                searchResult.contains(this.getClass().getSimpleName() + ".java"));

        String content = tool.readWorkspaceFile(searchResult.split("\n")[0], 0, 0);
        assertTrue("Expected to read own source, got: " + content.substring(0, Math.min(200, content.length())),
                content.contains("searchAndReadSelf"));
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
}
