package org.sterl.llmpeon.tool.file;

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
import org.sterl.llmpeon.tool.DiskFileWriteTools;

class DiskFilesToolTest {

    @TempDir
    Path tempDir;

    DiskFileWriteTools tool;

    @BeforeEach
    void setUp() {
        tool = new DiskFileWriteTools(tempDir);
    }

    @Test
    void createDiskFile_newFile() {
        String result = tool.createDiskFile("sub/dir/test.txt", "content");
        assertTrue(result.contains("Created"));
        assertTrue(Files.exists(tempDir.resolve("sub/dir/test.txt")));
    }

    @Test
    void createDiskFile_overwriteExisting() throws IOException {
        Files.writeString(tempDir.resolve("existing.txt"), "old");
        String result = tool.createDiskFile("existing.txt", "new");
        assertTrue(result.contains("Updated"));
        assertEquals("new", Files.readString(tempDir.resolve("existing.txt")));
    }

    @Test
    void writeDiskFile_existingFile() throws IOException {
        Files.writeString(tempDir.resolve("data.txt"), "before");
        String result = tool.writeDiskFile("data.txt", "after");
        assertTrue(result.contains("updated"));
        assertEquals("after", Files.readString(tempDir.resolve("data.txt")));
    }

    @Test
    void writeDiskFile_missingFile_throws() {
        assertThrows(IllegalArgumentException.class, () -> tool.writeDiskFile("nope.txt", "x"));
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
