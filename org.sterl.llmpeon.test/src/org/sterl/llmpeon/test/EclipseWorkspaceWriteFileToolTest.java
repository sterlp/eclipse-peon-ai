package org.sterl.llmpeon.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.nio.file.Files;
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
    public void test_editWorkspaceFile_reportsCount() {
        // GIVEN
        tool.setCurrentProject(project);
        var fileName = "/test_project/foo.txt";
        eclipseWriteFile(fileName, "x\nmid\nx");

        // WHEN
        var result = tool.eclipseEditFile(fileName, "x", "y");

        // THEN
        assertTrue("Expected replacement count in: " + result, result.contains("replaced 2 occurrence(s)"));
        assertEquals("y\nmid\ny", readTool.eclipseReadFile(fileName, 0, 0));
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
    public void test_writeFile_usesFileCharset_iso88591() throws Exception {
        // GIVEN a file with an explicit ISO-8859-1 charset (fixture persisted before the SUT call)
        tool.setCurrentProject(project);
        var fileName = "/test_project/latin1.txt";
        eclipseWriteFile(fileName, "init");
        var iFile = project.getFile("latin1.txt");
        iFile.setCharset("ISO-8859-1");
        try {
            // WHEN writing umlauts via the tool
            tool.eclipseWriteFile(fileName, "äüß Ö");

            // THEN bytes on disk are ISO-8859-1 (E4 FC DF 20 D6), not UTF-8 (C3 A4 C3 BC C3 9F 20 C3 96)
            var bytes = Files.readAllBytes(project.getLocation().append("latin1.txt").toFile().toPath());
            assertArrayEquals(new byte[]{(byte) 0xE4, (byte) 0xFC, (byte) 0xDF, 0x20, (byte) 0xD6}, bytes);
        } finally {
            // drop the explicit charset — no cross-run residue in .settings/org.eclipse.core.resources.prefs
            try {
                iFile.setCharset(null);
            } catch (Exception ignored) {
            }
        }
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

    // ------------------------------------------------------------------ Copy tool (file-copy-tool.md R1-R4)

    @Test
    public void test_copyWorkspaceFile() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        // GIVEN an existing file
        tool.setCurrentProject(project);
        var src = "/test_project/copySrc.txt";
        eclipseWriteFile(src, "data");
        var dst = "/test_project/sub/copyDst_" + System.nanoTime() + ".txt";

        // WHEN copy into a nested path
        var result = tool.eclipseCopyFile(src, dst);

        // THEN copy has the same content, original is kept, R6 result is "Copied <s> -> <t>" with resolved /project paths
        assertEquals("data", readTool.eclipseReadFile(dst, 0, 0));
        assertEquals("data", readTool.eclipseReadFile(src, 0, 0));
        assertEquals("Copied " + src + " -> " + dst, result);

        tool.eclipseDeleteResource(dst); // the copy is not auto-tracked by eclipseWriteFile
    }

    @Test
    public void test_renameWorkspaceFile_resultCarriesResolvedPaths() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        // GIVEN an existing file
        tool.setCurrentProject(project);
        var src = "/test_project/renameSrc_" + System.nanoTime() + ".txt";
        eclipseWriteFile(src, "data");
        // nested target (top-level targets are covered by test_renameWorkspaceFile_intoProjectRoot)
        var dst = "/test_project/sub/renameDst_" + System.nanoTime() + ".txt";

        // WHEN rename to a nested path in the same project
        var result = tool.eclipseRenameResource(src, dst);

        // THEN R6 result is "Renamed <s> -> <t>" with resolved /project paths, file moved
        assertEquals("Renamed " + src + " -> " + dst, result);
        assertEquals("data", readTool.eclipseReadFile(dst, 0, 0));
        assertTrue(readTool.eclipseReadFile(src, 0, 0).contains("No eclipse file found"));
    }

    @Test
    public void test_copyWorkspaceFile_failsWhenTargetExists() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        // GIVEN source + an existing target
        tool.setCurrentProject(project);
        var src = "/test_project/copySrc2.txt";
        var dst = "/test_project/copyDst2.txt";
        eclipseWriteFile(src, "a");
        eclipseWriteFile(dst, "b");

        // WHEN copy onto the existing target
        try {
            tool.eclipseCopyFile(src, dst);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        // THEN source and target unchanged (R3 no overwrite)
        assertEquals("a", readTool.eclipseReadFile(src, 0, 0));
        assertEquals("b", readTool.eclipseReadFile(dst, 0, 0));
    }

    @Test
    public void test_copyWorkspaceFile_failsWhenSourceMissing() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        // GIVEN no source file
        tool.setCurrentProject(project);

        // WHEN copy a missing source
        try {
            tool.eclipseCopyFile("/test_project/no_such_src.txt", "/test_project/no_such_dst.txt");
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        // THEN no target was created
        assertTrue(readTool.eclipseReadFile("/test_project/no_such_dst.txt", 0, 0).contains("No eclipse file found"));
    }

    @Test
    public void test_copyWorkspaceFile_failsWhenSourceIsDirectory() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        // GIVEN a directory source (the inner file is auto-registered for cleanup)
        tool.setCurrentProject(project);
        eclipseWriteFile("/test_project/copyDirSrc/inner.txt", "x");

        // WHEN copying the directory itself
        try {
            tool.eclipseCopyFile("/test_project/copyDirSrc", "/test_project/copyDirDst.txt");
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue("Expected 'Not a file', was: " + e.getMessage(), e.getMessage().contains("Not a file"));
        }

        // THEN no target was created, source unchanged (R1 parity with FileUtils.copy)
        assertTrue(readTool.eclipseReadFile("/test_project/copyDirDst.txt", 0, 0).contains("No eclipse file found"));
        assertEquals("x", readTool.eclipseReadFile("/test_project/copyDirSrc/inner.txt", 0, 0));
    }

    // ------------------------------------------------------------------ R5: fully qualified paths only

    @Test
    public void test_copyWorkspaceFile_rejectsRelativePaths() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        // GIVEN an existing file
        tool.setCurrentProject(project);
        var src = "/test_project/relCopySrc_" + System.nanoTime() + ".txt";
        eclipseWriteFile(src, "data");

        // WHEN copy with a relative source or a relative target — the validator must fire BEFORE resolution
        try {
            tool.eclipseCopyFile("test_project/relCopySrc.txt", "/test_project/relCopyDst1.txt");
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue("Expected contract error, was: " + e.getMessage(), e.getMessage().contains("must be fully qualified"));
        }
        try {
            tool.eclipseCopyFile(src, "test_project/relCopyDst2.txt");
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue("Expected contract error, was: " + e.getMessage(), e.getMessage().contains("must be fully qualified"));
        }

        // THEN no operation happened
        assertTrue(readTool.eclipseReadFile("/test_project/relCopyDst1.txt", 0, 0).contains("No eclipse file found"));
        assertTrue(readTool.eclipseReadFile("/test_project/relCopyDst2.txt", 0, 0).contains("No eclipse file found"));
        assertEquals("data", readTool.eclipseReadFile(src, 0, 0));
    }

    @Test
    public void test_renameWorkspaceFile_rejectsRelativePaths() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        // GIVEN an existing file
        tool.setCurrentProject(project);
        var src = "/test_project/relRenameSrc_" + System.nanoTime() + ".txt";
        eclipseWriteFile(src, "data");

        // WHEN rename with a relative source or a relative target — the validator must fire BEFORE resolution
        try {
            tool.eclipseRenameResource("test_project/relRenameSrc.txt", "/test_project/relRenameDst1.txt");
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue("Expected contract error, was: " + e.getMessage(), e.getMessage().contains("must be fully qualified"));
        }
        try {
            tool.eclipseRenameResource(src, "test_project/relRenameDst2.txt");
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue("Expected contract error, was: " + e.getMessage(), e.getMessage().contains("must be fully qualified"));
        }

        // THEN no operation happened
        assertEquals("data", readTool.eclipseReadFile(src, 0, 0));
        assertTrue(readTool.eclipseReadFile("/test_project/relRenameDst1.txt", 0, 0).contains("No eclipse file found"));
        assertTrue(readTool.eclipseReadFile("/test_project/relRenameDst2.txt", 0, 0).contains("No eclipse file found"));
    }

    @Test
    public void test_copyWorkspaceFile_intoProjectRoot() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        // GIVEN an existing file
        tool.setCurrentProject(project);
        var src = "/test_project/copyRootSrc_" + System.nanoTime() + ".txt";
        eclipseWriteFile(src, "data");
        var dst = "/test_project/copyRootDst_" + System.nanoTime() + ".txt";

        // WHEN copy directly into the project root — the parent is the project itself (1 segment),
        // which crashed the old IWorkspaceRoot.getFolder(destPath.removeLastSegments(1)) call
        var result = tool.eclipseCopyFile(src, dst);

        // THEN copy exists, original kept, R6 result
        assertEquals("Copied " + src + " -> " + dst, result);
        assertEquals("data", readTool.eclipseReadFile(dst, 0, 0));
        assertEquals("data", readTool.eclipseReadFile(src, 0, 0));

        tool.eclipseDeleteResource(dst);
    }

    @Test
    public void test_renameWorkspaceFile_intoProjectRoot() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        // GIVEN an existing file in a subfolder
        tool.setCurrentProject(project);
        var src = "/test_project/sub/renameRootSrc_" + System.nanoTime() + ".txt";
        eclipseWriteFile(src, "data");
        var dst = "/test_project/renameRootDst_" + System.nanoTime() + ".txt";

        // WHEN rename into the project root (parent = project itself)
        var result = tool.eclipseRenameResource(src, dst);

        // THEN file moved, R6 result
        assertEquals("Renamed " + src + " -> " + dst, result);
        assertEquals("data", readTool.eclipseReadFile(dst, 0, 0));
        assertTrue(readTool.eclipseReadFile(src, 0, 0).contains("No eclipse file found"));
    }

}
