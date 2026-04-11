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
        assertTrue(tool.readDiskFile("hello.txt", 0, 0).contains("world"),
                tool.readDiskFile("hello.txt", 0, 0));
    }

    @Test
    void readDiskFile_missingFile() {
        try {
            tool.readDiskFile("missing.txt", 0, 0);
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
        String result = tool.searchDiskFiles("FooController", 0);
        // THEN
        assertTrue(result.contains("FooController.java"));
        assertFalse(result.contains("BarController.java"));
        
        // WHEN
        result = tool.searchDiskFiles("*Controller*.java", 0);
        // THEN
        assertTrue(result.contains("FooController.java"));
        assertTrue(result.contains("BarController.java"));
        
        // WHEN
        result = tool.searchDiskFiles("**/FooController.java", 0);
        // THEN
        assertTrue(result.contains("FooController.java"));
        assertFalse(result.contains("BarController.java"));
    }

    @Test
    void searchDiskFiles_globExtension() throws IOException {
        Files.writeString(tempDir.resolve("README.md"), "docs");
        Files.writeString(tempDir.resolve("notes.md"), "notes");
        Files.writeString(tempDir.resolve("Other.java"), "code");
        String result = tool.searchDiskFiles("*.md", 0);
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
        String result = tool.searchDiskFiles("Foo*", 0);
        // THEN
        assertTrue(result.contains("FooController.java"));
        assertTrue(result.contains("FooService.java"));
        assertFalse(result.contains("BarHelper.java"));
        
        // AND no error
        tool.readDiskFile(result.split("\n")[0], 0, 0);
    }

    @Test
    void searchDiskFiles_limitRestrictsResults() throws IOException {
        // GIVEN - three files
        Files.writeString(tempDir.resolve("A.java"), "");
        Files.writeString(tempDir.resolve("B.java"), "");
        Files.writeString(tempDir.resolve("C.java"), "");

        // unlimited returns all three
        assertEquals(3, tool.searchDiskFiles("*.java", 0).split("\n").length);

        // limit=1 returns exactly one
        assertEquals(1, tool.searchDiskFiles("*.java", 1).split("\n").length);

        // limit=2 returns exactly two
        assertEquals(2, tool.searchDiskFiles("*.java", 2).split("\n").length);
    }

    @Test
    void searchDiskFiles_emptyQuery() {
        assertThrows(IllegalArgumentException.class, () -> tool.searchDiskFiles("", 0));
        assertThrows(IllegalArgumentException.class, () -> tool.searchDiskFiles(null, 0));
    }

    @Test
    void readDiskFile_absolutePath() throws IOException {
        Path abs = tempDir.resolve("abs.txt");
        Files.writeString(abs, "absolute");
        assertTrue(tool.readDiskFile(abs.toString(), 0, 0).contains("absolute"),
                tool.readDiskFile(abs.toString(), 0, 0));
    }

    @Test
    void readDiskFile_lineNumbersStartAtOne() throws IOException {
        Files.writeString(tempDir.resolve("lines.txt"), "alpha\nbeta\ngamma\ndelta\nepsilon");
        // full file - line numbers must start at 1
        String result = tool.readDiskFile("lines.txt", 0, 0);
        assertEquals(
                "   1: alpha\n" +
                "   2: beta\n" +
                "   3: gamma\n" +
                "   4: delta\n" +
                "   5: epsilon\n",
                result);
    }

    @Test
    void readDiskFile_fromToPreservesActualLineNumbers() throws IOException {
        Files.writeString(tempDir.resolve("lines.txt"), "alpha\nbeta\ngamma\ndelta\nepsilon");
        // reading lines 3-5 must show line numbers 3, 4, 5 - not reset to 1
        String result = tool.readDiskFile("lines.txt", 3, 5);
        assertEquals(
                "   3: gamma\n" +
                "   4: delta\n" +
                "   5: epsilon\n",
                result);
    }

    @Test
    void readDiskFile_fromToMiddleRange() throws IOException {
        Files.writeString(tempDir.resolve("lines.txt"), "alpha\nbeta\ngamma\ndelta\nepsilon");
        // reading lines 2-3 must show line numbers 2 and 3
        String result = tool.readDiskFile("lines.txt", 2, 3);
        assertEquals(
                "   2: beta\n" +
                "   3: gamma\n",
                result);
    }

    @Test
    void readDiskFile_endLineOutOfBoundsReturnsWholeFile() throws IOException {
        Files.writeString(tempDir.resolve("lines.txt"), "alpha\nbeta\ngamma");
        // end line 99 exceeds file length (3 lines) - must return whole file
        String result = tool.readDiskFile("lines.txt", 1, 99);
        assertEquals(
                "   1: alpha\n" +
                "   2: beta\n" +
                "   3: gamma\n",
                result);
    }

    @Test
    void readDiskFile_startLineOutOfBoundsReturnsWholeFile() throws IOException {
        Files.writeString(tempDir.resolve("lines.txt"), "alpha\nbeta\ngamma");
        // start line 99 exceeds file length (3 lines) - must return whole file
        String result = tool.readDiskFile("lines.txt", 99, 100);
        assertEquals(
                "   1: alpha\n" +
                "   2: beta\n" +
                "   3: gamma\n",
                result);
    }
}
