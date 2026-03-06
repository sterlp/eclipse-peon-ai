package org.sterl.llmpeon.tool.file;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.tool.DiskFileReadTools;
import org.sterl.llmpeon.tool.DiskFileWriteTools;

class DiskFileReadToolsTest {

    @TempDir
    Path tempDir;

    DiskFileReadTools tool;

    @BeforeEach
    void setUp() {
        tool = new DiskFileReadTools(tempDir);
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
