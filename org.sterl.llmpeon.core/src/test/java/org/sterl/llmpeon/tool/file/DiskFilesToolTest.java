package org.sterl.llmpeon.tool.file;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.tool.DiskFilesTool;

class DiskFilesToolTest {

    @TempDir
    Path tempDir;

    DiskFilesTool tool;

    @BeforeEach
    void setUp() {
        tool = new DiskFilesTool(tempDir);
    }

    @Test
    void readDiskFile_existingFile() throws IOException {
        Files.writeString(tempDir.resolve("hello.txt"), "world");
        assertEquals("world", tool.readDiskFile("hello.txt"));
    }

    @Test
    void readDiskFile_missingFile() {
        String result = tool.readDiskFile("missing.txt");
        assertTrue(result.contains("File not found"));
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

    @Test
    void searchDiskFiles_findsMatch() throws IOException {
        Files.writeString(tempDir.resolve("FooController.java"), "class Foo {}");
        Files.writeString(tempDir.resolve("BarService.java"), "class Bar {}");
        String result = tool.searchDiskFiles("Controller");
        assertTrue(result.contains("1 file"));
        assertTrue(result.contains("FooController.java"));
    }

    @Test
    void searchDiskFiles_emptyQuery() {
        assertThrows(IllegalArgumentException.class, () -> tool.searchDiskFiles(""));
        assertThrows(IllegalArgumentException.class, () -> tool.searchDiskFiles(null));
    }

    @Test
    void readDiskFile_absolutePath() throws IOException {
        Path abs = tempDir.resolve("abs.txt");
        Files.writeString(abs, "absolute");
        assertEquals("absolute", tool.readDiskFile(abs.toString()));
    }
}
