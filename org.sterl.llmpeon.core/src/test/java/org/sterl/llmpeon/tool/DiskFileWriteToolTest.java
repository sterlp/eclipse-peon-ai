package org.sterl.llmpeon.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;

class DiskFileWriteToolTest {

    @TempDir
    Path tempDir;

    DiskFileWriteTool tool;

    @BeforeEach
    void setUp() {
        tool = new DiskFileWriteTool(tempDir);
    }

    @Test
    void writeDiskFile_newFile() {
        tool.diskWriteFile("sub/dir/test.txt", "content");
        assertTrue(Files.exists(tempDir.resolve("sub/dir/test.txt")));
    }

    @Test
    void writeDiskFile_overwriteExisting() throws IOException {
        Files.writeString(tempDir.resolve("existing.txt"), "old");
        tool.diskWriteFile("existing.txt", "new");
        assertEquals("new", Files.readString(tempDir.resolve("existing.txt")));
    }

    @Test
    void writeDiskFile_existingFile() throws IOException {
        Files.writeString(tempDir.resolve("data.txt"), "before");
        tool.diskWriteFile("data.txt", "after");
        assertEquals("after", Files.readString(tempDir.resolve("data.txt")));
    }

    @Test
    void writeDiskFile_emptyContentAllowed() throws IOException {
        Files.writeString(tempDir.resolve("truncate.txt"), "before");
        tool.diskWriteFile("truncate.txt", "");
        assertEquals("", Files.readString(tempDir.resolve("truncate.txt")));
    }

    @Test
    void deleteDiskFile_existingFile() throws IOException {
        Files.writeString(tempDir.resolve("del.txt"), "bye");
        tool.diskDeleteFile("del.txt");
        assertFalse(Files.exists(tempDir.resolve("del.txt")));
    }

    @Test
    void deleteDiskFile_missingFile() {
        assertThrows(IllegalArgumentException.class, () -> tool.diskDeleteFile("nope.txt"));
    }

    @Test
    void insertDiskLines_afterLine() throws IOException {
        Files.writeString(tempDir.resolve("ins.txt"), "a\nb\nc");
        tool.diskInsertLines("ins.txt", 2, "x\ny");
        assertEquals("a\nb\nx\ny\nc", Files.readString(tempDir.resolve("ins.txt")));
    }

    @Test
    void insertDiskLines_prepend() throws IOException {
        Files.writeString(tempDir.resolve("ins.txt"), "a\nb");
        tool.diskInsertLines("ins.txt", 0, "x");
        assertEquals("x\na\nb", Files.readString(tempDir.resolve("ins.txt")));
    }

    @Test
    void insertDiskLines_append() throws IOException {
        Files.writeString(tempDir.resolve("ins.txt"), "a\nb");
        tool.diskInsertLines("ins.txt", null, "x");
        assertEquals("a\nb\nx", Files.readString(tempDir.resolve("ins.txt")));
    }

    @Test
    void replaceDiskLines_basic() throws IOException {
        Files.writeString(tempDir.resolve("rep.txt"), "line1\nline2\nline3");
        tool.diskReplaceLines("rep.txt", 2, "replaced");
        assertEquals("line1\nreplaced\nline3", Files.readString(tempDir.resolve("rep.txt")));
    }

    @Test
    void replaceDiskLines_multiLine() throws IOException {
        Files.writeString(tempDir.resolve("rep.txt"), "a\nb\nc\nd");
        tool.diskReplaceLines("rep.txt", 2, "x\ny");
        assertEquals("a\nx\ny\nc\nd", Files.readString(tempDir.resolve("rep.txt")));
    }
}
