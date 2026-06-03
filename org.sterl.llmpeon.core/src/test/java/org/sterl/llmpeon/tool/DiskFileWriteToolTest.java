package org.sterl.llmpeon.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        String result = tool.writeDiskFile("sub/dir/test.txt", "content");
        assertTrue(result.contains("Created"));
        assertTrue(Files.exists(tempDir.resolve("sub/dir/test.txt")));
    }

    @Test
    void writeDiskFile_overwriteExisting() throws IOException {
        Files.writeString(tempDir.resolve("existing.txt"), "old");
        String result = tool.writeDiskFile("existing.txt", "new");
        assertTrue(result.contains("Updated"));
        assertEquals("new", Files.readString(tempDir.resolve("existing.txt")));
    }

    @Test
    void writeDiskFile_existingFile() throws IOException {
        Files.writeString(tempDir.resolve("data.txt"), "before");
        String result = tool.writeDiskFile("data.txt", "after");
        assertTrue(result.contains("Updated"));
        assertEquals("after", Files.readString(tempDir.resolve("data.txt")));
    }

    @Test
    void writeDiskFile_emptyContentAllowed() throws IOException {
        Files.writeString(tempDir.resolve("truncate.txt"), "before");
        String result = tool.writeDiskFile("truncate.txt", "");
        assertTrue(result.contains("Updated"));
        assertEquals("", Files.readString(tempDir.resolve("truncate.txt")));
    }

    @Test
    void deleteDiskFile_existingFile() throws IOException {
        Files.writeString(tempDir.resolve("del.txt"), "bye");
        String result = tool.deleteDiskFile("del.txt");
        assertTrue(result.contains("Deleted"));
        assertFalse(Files.exists(tempDir.resolve("del.txt")));
    }

    @Test
    void deleteDiskFile_missingFile() {
        String result = tool.deleteDiskFile("nope.txt");
        assertTrue(result.contains("File not found"));
    }
}
