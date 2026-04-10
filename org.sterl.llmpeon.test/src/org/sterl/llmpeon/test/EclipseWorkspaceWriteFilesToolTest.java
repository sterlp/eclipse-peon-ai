package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.time.OffsetDateTime;

import org.junit.Test;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceWriteFileTool;

public class EclipseWorkspaceWriteFilesToolTest extends AbstractTest {

    private final EclipseWorkspaceReadFileTool readTool = new EclipseWorkspaceReadFileTool();

    @Test
    public void test_createWorkspaceFile() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        // GIVEN
        var tool = new EclipseWorkspaceWriteFileTool();
        var fileName = "/org.sterl.llmpeon.test/foo.txt";
        var message = "Hello world " + OffsetDateTime.now();
        // WHEN
        var result = tool.createWorkspaceFile(fileName, message);
        
        // THEN
        assertTrue(result, result.contains(fileName));
        assertEquals(message, readTool.readWorkspaceFile(fileName, 0, 0));
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
        tool.createWorkspaceFile(fileName, message);
        
        // WHEN
        var result = tool.editWorkspaceFile(fileName, 
                "    private void updateSelectedProject(IProject project) {", 
                editMessage);
        // THEN
        assertTrue(result, result.contains("successfully"));
        
        message = readTool.readWorkspaceFile(fileName, 0, 0);
        assertTrue("Missing edit text in:\n" + message, message.contains(editMessage));
    }
    
    @Test
    public void test_replaceWorkspaceLine_middle() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        // GIVEN
        var tool = new EclipseWorkspaceWriteFileTool();
        var fileName = "/org.sterl.llmpeon.test/foo.txt";
        tool.createWorkspaceFile(fileName, "line1\nline2\nline3\nline4\nline5");

        // WHEN — replace middle line 3, expanding it to two lines
        tool.replaceWorkspaceLine(fileName, 3, "replaced3a\nreplaced3b");

        // THEN — surrounding lines untouched, middle replaced
        var content = readTool.readWorkspaceFile(fileName, 0, 0);
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
                """ + OffsetDateTime.now();
        tool.createWorkspaceFile(fileName, message);
        
        // WHEN
        var result = tool.editWorkspaceFile(fileName, "  Line to replace\n  fooo", editString);
        // THEN
        assertTrue(result, result.contains("successfully"));
        
        message = readTool.readWorkspaceFile(fileName, 0, 0);
        assertTrue(message, message.contains(editString));
        assertTrue(message, message.contains("Line to replace"));
        assertTrue(message, message.contains("foo"));
    }
}
