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
        var editString = OffsetDateTime.now().toString();
        var message = """
                Hello world
                Line to replace
                This should stay
                """ + OffsetDateTime.now();
        tool.createWorkspaceFile(fileName, message);
        
        // WHEN
        var result = tool.editWorkspaceFile(fileName, "Line to replace", editString);
        // THEN
        assertTrue(result, result.contains("successfully"));
        
        message = readTool.readWorkspaceFile(fileName);
        assertTrue(message, message.contains(editString));
        assertFalse(message, message.contains("Line to replace"));
    }
}
