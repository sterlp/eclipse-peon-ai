package org.sterl.llmpeon.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;

class DiskFileReadToolsTest {

    @TempDir
    Path tempDir;

    DiskFileReadTool tool;

    @BeforeEach
    void setUp() {
        tool = new DiskFileReadTool(tempDir);
    }

    @Test
    void readDiskFile_existingFile() throws IOException {
        Files.writeString(tempDir.resolve("hello.txt"), "world");
        assertEquals("world", tool.readDiskFile("hello.txt"));
    }

    @Test
    void readDiskFile_missingFile() {
        try {
            tool.readDiskFile("missing.txt");
            fail("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("File not found"));
            assertTrue(e.getMessage().contains("missing.txt"));
        }
    }

    @Test
    void searchDiskFiles_findsMatch() throws IOException {
        // GIVEN
        Files.createDirectories(tempDir.resolve("foo"));
        Files.createDirectories(tempDir.resolve("bar"));
        Files.writeString(tempDir.resolve("foo/FooController.java"), "class Foo {}");
        Files.writeString(tempDir.resolve("bar/BarController.java"), "class Bar {}");
        // WHEN
        String result = tool.searchDiskFiles("FooController");
        // THEN
        assertTrue(result.contains("FooController.java"));
        assertFalse(result.contains("BarController.java"));
        
        // WHEN
        result = tool.searchDiskFiles("*Controller*.java");
        // THEN
        assertTrue(result.contains("FooController.java"));
        assertTrue(result.contains("BarController.java"));
        
        // WHEN
        result = tool.searchDiskFiles("**/FooController.java");
        // THEN
        assertTrue(result.contains("FooController.java"));
        assertFalse(result.contains("BarController.java"));
    }

    @Test
    void searchDiskFiles_globExtension() throws IOException {
        Files.writeString(tempDir.resolve("README.md"), "docs");
        Files.writeString(tempDir.resolve("notes.md"), "notes");
        Files.writeString(tempDir.resolve("Other.java"), "code");
        String result = tool.searchDiskFiles("*.md");
        assertTrue(result.contains("README.md"), "should find README.md");
        assertTrue(result.contains("notes.md"), "should find notes.md");
        assertFalse(result.contains("Other.java"), "should not find .java file");
    }

    @Test
    void searchDiskFiles_globPrefix() throws IOException {
        // GIVEN
        Files.writeString(tempDir.resolve("FooController.java"), "");
        Files.writeString(tempDir.resolve("FooService.java"), "");
        Files.writeString(tempDir.resolve("BarHelper.java"), "");
        // WHEN
        String result = tool.searchDiskFiles("Foo*");
        // THEN
        assertTrue(result.contains("FooController.java"));
        assertTrue(result.contains("FooService.java"));
        assertFalse(result.contains("BarHelper.java"));
        
        // AND no error
        tool.readDiskFile(result.split("\n")[0]);
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
