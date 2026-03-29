package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.time.OffsetDateTime;

import org.junit.Test;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFilesTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceWriteFilesTool;

public class EclipseWorkspaceWriteFilesToolTest extends AbstractTest {

    private final EclipseWorkspaceReadFilesTool readTool = new EclipseWorkspaceReadFilesTool();

    @Test
    public void test_createWorkspaceFile() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        // GIVEN
        var tool = new EclipseWorkspaceWriteFilesTool();
        var fileName = "/org.sterl.llmpeon.test/foo.txt";
        var message = "Hello world " + OffsetDateTime.now();
        // WHEN
        var result = tool.createWorkspaceFile(fileName, message);
        
        // THEN
        assertTrue(result, result.contains(fileName));
        assertEquals(message, readTool.readWorkspaceFile(fileName));
    }
    
    @Test
    public void test_editWorkspaceFile() {
        // GIVEN
        var tool = new EclipseWorkspaceWriteFilesTool();
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
        
        message = readTool.readWorkspaceFile(fileName);
        assertTrue("Missing edit text in:\n" + message, message.contains(editMessage));
    }
    
    @Test
    public void test_editWorkspaceFile_not_found() {
        // GIVEN
        var tool = new EclipseWorkspaceWriteFilesTool();
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
        
        message = readTool.readWorkspaceFile(fileName);
        assertTrue(message, message.contains(editString));
        assertTrue(message, message.contains("Line to replace"));
        assertTrue(message, message.contains("foo"));
    }
}
