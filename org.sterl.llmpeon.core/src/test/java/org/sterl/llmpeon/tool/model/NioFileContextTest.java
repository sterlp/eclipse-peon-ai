package org.sterl.llmpeon.tool.model;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NioFileContextTest {

    @TempDir
    Path tempDir;

    NioFileContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new NioFileContext(tempDir);
    }

    @Test
    void readFile_existingFile() throws IOException {
        Files.writeString(tempDir.resolve("hello.txt"), "world");
        assertEquals("world", ctx.readFile("hello.txt"));
    }

    @Test
    void readFile_missingFile() {
        String result = ctx.readFile("missing.txt");
        assertTrue(result.contains("File not found"));
    }

    @Test
    void createFile_newFile() {
        String result = ctx.createFile("sub/dir/test.txt", "content");
        assertTrue(result.contains("Created"));
        assertTrue(Files.exists(tempDir.resolve("sub/dir/test.txt")));
    }

    @Test
    void createFile_overwriteExisting() throws IOException {
        Files.writeString(tempDir.resolve("existing.txt"), "old");
        String result = ctx.createFile("existing.txt", "new");
        assertTrue(result.contains("Updated"));
        assertEquals("new", Files.readString(tempDir.resolve("existing.txt")));
    }

    @Test
    void writeFile_existingFile() throws IOException {
        Files.writeString(tempDir.resolve("data.txt"), "before");
        var update = ctx.writeFile("data.txt", "after");
        assertEquals("before", update.oldContent());
        assertEquals("after", update.newContent());
        assertEquals("after", Files.readString(tempDir.resolve("data.txt")));
    }

    @Test
    void writeFile_missingFile_throws() {
        assertThrows(IllegalArgumentException.class, () -> ctx.writeFile("nope.txt", "x"));
    }

    @Test
    void deleteFile_existingFile() throws IOException {
        Files.writeString(tempDir.resolve("del.txt"), "bye");
        String result = ctx.deleteFile("del.txt");
        assertTrue(result.contains("Deleted"));
        assertFalse(Files.exists(tempDir.resolve("del.txt")));
    }

    @Test
    void deleteFile_missingFile() {
        String result = ctx.deleteFile("nope.txt");
        assertTrue(result.contains("File not found"));
    }

    @Test
    void searchFiles_findsMatch() throws IOException {
        Files.writeString(tempDir.resolve("FooController.java"), "class Foo {}");
        Files.writeString(tempDir.resolve("BarService.java"), "class Bar {}");
        var results = ctx.searchFiles("Controller");
        assertEquals(1, results.size());
        assertTrue(results.get(0).contains("FooController.java"));
    }

    @Test
    void searchFiles_emptyQuery() {
        assertTrue(ctx.searchFiles("").isEmpty());
        assertTrue(ctx.searchFiles(null).isEmpty());
    }

    @Test
    void readFile_absolutePath() throws IOException {
        Path abs = tempDir.resolve("abs.txt");
        Files.writeString(abs, "absolute");
        assertEquals("absolute", ctx.readFile(abs.toString()));
    }
}
