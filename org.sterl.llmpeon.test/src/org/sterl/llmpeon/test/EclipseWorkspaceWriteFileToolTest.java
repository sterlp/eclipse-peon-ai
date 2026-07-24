package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.time.OffsetDateTime;

import org.junit.Test;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceWriteFileTool;

public class EclipseWorkspaceWriteFileToolTest extends AbstractTest {

    private final EclipseWorkspaceReadFileTool readTool = new EclipseWorkspaceReadFileTool();

    @Test
    public void test_writeWorkspaceFile() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        // GIVEN
        var tool = new EclipseWorkspaceWriteFileTool();
        var fileName = "/org.sterl.llmpeon.test/foo.txt";
        var message = "Hello world " + OffsetDateTime.now();
        // WHEN
        tool.eclipseWriteFile(fileName, message);
        
        // THEN
        assertEquals(message, readTool.eclipseReadFile(fileName, 0, 0));
    }
    
    @Test
    public void test_editWorkspaceFile() {
        // GIVEN
        var tool = new EclipseWorkspaceWriteFileTool();
        var fileName = "/org.sterl.llmpeon.test/foo.txt";
        var message = """
                    private void updateSelectedProject(IProject project) {
                        if (project != null && !projectPinned) {
                            currentProject = project;
                            agentsMdService.load(project);
                            workspaceWriteFilesTool.setCurrentProject(project);
                            workspaceReadFilesTool.setCurrentProject(project);
                            agentMode.setProject(project);
                        }
                
                        if (actionsBar != null) {
                            EclipseUtil.runInUiThread(parent, () -> {
                                actionsBar.setAgentModeAvailable(currentProject != null && currentProject.isOpen());
                                if (currentProject == null && currentMode == PeonMode.AGENT) {
                                    onModeChange(PeonMode.DEV);
                                }
                                refreshStatusLine();
                            });
                        }
                    }
                """;
        var editMessage = """
                private void updateSelectedProject(IProject project) {
                // Guard against selection injection before createPartControl() initializes fields
                if (agentMode == null || actionsBar == null) return;
        """;
        tool.eclipseWriteFile(fileName, message);
        
        // WHEN
        tool.eclipseEditFile(fileName, 
                "    private void updateSelectedProject(IProject project) {", 
                editMessage);
        // THEN
        message = readTool.eclipseReadFile(fileName, 0, 0);
        assertTrue("Missing edit text in:\n" + message, message.contains(editMessage));
    }
    
    @Test
    public void test_replaceWorkspaceLine_middle() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        // GIVEN
        var tool = new EclipseWorkspaceWriteFileTool();
        var fileName = "/org.sterl.llmpeon.test/foo.txt";
        tool.eclipseWriteFile(fileName, "line1\nline2\nline3\nline4\nline5");

        // WHEN — replace middle line 3, expanding it to two lines
        tool.eclipseReplaceLines(fileName, 3, "replaced3a\nreplaced3b");

        // THEN — surrounding lines untouched, middle replaced
        var content = readTool.eclipseReadFile(fileName, 0, 0);
        assertTrue(content, content.contains("line1"));
        assertTrue(content, content.contains("line2"));
        assertTrue(content, content.contains("replaced3a"));
        assertTrue(content, content.contains("replaced3b"));
        assertTrue(content, content.contains("line4"));
        assertTrue(content, content.contains("line5"));
        assertTrue(content, !content.contains("line3\n"));
    }

    @Test
    public void test_editWorkspaceFile_not_found() {
        // GIVEN
        var tool = new EclipseWorkspaceWriteFileTool();
        var fileName = "/org.sterl.llmpeon.test/foo.txt";
        var editString = "  " + OffsetDateTime.now().toString();
        var message = """
                  Hello world
                  Line to replace
                  foo
                  This should stay
                """ + editString;
        tool.eclipseWriteFile(fileName, message);
        
        // WHEN
        try {
            tool.eclipseEditFile(fileName, "  Line to replace\n  fooooooo", editString);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {}
        // THEN
        message = readTool.eclipseReadFile(fileName, null, null);
        assertTrue(message, message.contains(editString));
        assertTrue(message, message.contains("Line to replace"));
        assertTrue(message, message.contains("foo"));
    }

    @Test
    public void test_deleteResource_recursiveDirectory() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        // GIVEN
        var tool = new EclipseWorkspaceWriteFileTool();
        var dirName = "/org.sterl.llmpeon.test/testDeleteDir/nested/child";
        tool.eclipseWriteFile(dirName + "/file1.txt", "a");
        tool.eclipseWriteFile(dirName + "/file2.txt", "b");
        tool.eclipseWriteFile("/org.sterl.llmpeon.test/testDeleteDir/parentFile.txt", "c");

        // WHEN
        tool.eclipseDeleteResource("/org.sterl.llmpeon.test/testDeleteDir");

        // THEN — entire directory tree gone
        var result = readTool.eclipseReadFile("/org.sterl.llmpeon.test/testDeleteDir/parentFile.txt", 0, 0);
        assertTrue("Directory should be deleted, but parentFile.txt still exists", result.contains("No eclipse file found"));
    }
}
