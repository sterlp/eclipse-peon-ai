package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.time.OffsetDateTime;

import org.junit.Test;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceWriteFileTool;

public class EclipseWorkspaceWriteFileToolTest extends AbstractIntegrationTest {

    private final EclipseWorkspaceReadFileTool readTool = new EclipseWorkspaceReadFileTool();
    EclipseWorkspaceWriteFileTool tool = new EclipseWorkspaceWriteFileTool();

    @Test
    public void test_writeWorkspaceFile() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        // GIVEN
        var fileName = "/test_project/foo.txt";
        var message = "Hello world " + OffsetDateTime.now();
        tool.setCurrentProject(project);

        // WHEN
        eclipseWriteFile(fileName, message);
        
        // THEN
        assertEquals(message, readTool.eclipseReadFile(fileName, 0, 0));
    }
    
    @Test
    public void test_editWorkspaceFile() {
        // GIVEN
        tool.setCurrentProject(project);
        var fileName = "/test_project/foo.txt";
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
        eclipseWriteFile(fileName, message);
        
        // WHEN
        tool.eclipseEditFile(fileName, 
                "    private void updateSelectedProject(IProject project) {", 
                editMessage);
        // THEN
        message = readTool.eclipseReadFile(fileName, 0, 0);
        assertTrue("Missing edit text in:\n" + message, message.contains(editMessage));
    }

    @Test
    public void writeUtf8() throws Exception {
        // GIVEN
        // WHEN
        eclipseWriteFile("/test_project/foo.java", "äüß Ö ⚡");

        // THEN
        var c = new EclipseWorkspaceReadFileTool().eclipseReadFile(JdtUtil.pathOf(project) + "/foo.java", null, null);
        assertEquals("äüß Ö ⚡", c);
    }

    @Test
    public void test_replaceWorkspaceLine_middle() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        // GIVEN
        tool.setCurrentProject(project);
        var fileName = "/test_project/foo.txt";
        eclipseWriteFile(fileName, "line1\nline2\nline3\nline4\nline5");

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
        tool.setCurrentProject(project);
        var fileName = "/test_project/foo.txt";
        var editString = "  " + OffsetDateTime.now().toString();
        var message = """
                  Hello world
                  Line to replace
                  foo
                  This should stay
                """ + editString;
        eclipseWriteFile(fileName, message);
        
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
        tool.setCurrentProject(project);
        var dirName = "/test_project/testDeleteDir/nested/child";
        eclipseWriteFile(dirName + "/file1.txt", "a");
        eclipseWriteFile(dirName + "/file2.txt", "b");
        eclipseWriteFile("/test_project/testDeleteDir/parentFile.txt", "c");

        // WHEN
        tool.eclipseDeleteResource("/test_project/testDeleteDir");

        // THEN — entire directory tree gone
        var result = readTool.eclipseReadFile("/test_project/testDeleteDir/parentFile.txt", 0, 0);
        assertTrue("Directory should be deleted, but parentFile.txt still exists", result.contains("No eclipse file found"));
    }
}
