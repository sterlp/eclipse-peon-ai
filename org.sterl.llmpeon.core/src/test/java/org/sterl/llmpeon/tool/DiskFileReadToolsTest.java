package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URISyntaxException;
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
        assertTrue(tool.diskReadFile("hello.txt", 0, 0).contains("world"),
                tool.diskReadFile("hello.txt", 0, 0));
    }

    @Test
    void readDiskFile_missingFile() {
        try {
            tool.diskReadFile("missing.txt", 0, 0);
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
        String result = tool.diskSearchFiles("FooController", 0);
        // THEN
        assertTrue(result.contains("FooController.java"));
        assertFalse(result.contains("BarController.java"));
        
        // WHEN
        result = tool.diskSearchFiles("*Controller*.java", 0);
        // THEN
        assertTrue(result.contains("FooController.java"));
        assertTrue(result.contains("BarController.java"));
        
        // WHEN
        result = tool.diskSearchFiles("**/FooController.java", 0);
        // THEN
        assertThat(result).contains("FooController.java");
        assertThat(result).doesNotContain("BarController.java");
    }

    @Test
    void searchDiskFiles_globExtension() throws IOException {
        Files.writeString(tempDir.resolve("README.md"), "docs");
        Files.writeString(tempDir.resolve("notes.md"), "notes");
        Files.writeString(tempDir.resolve("Other.java"), "code");
        String result = tool.diskSearchFiles("*.md", 0);
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
        String result = tool.diskSearchFiles("Foo*", 0);
        // THEN
        assertTrue(result.contains("FooController.java"));
        assertTrue(result.contains("FooService.java"));
        assertFalse(result.contains("BarHelper.java"));
        
        // AND no error
        tool.diskReadFile(result.split("\n")[0], 0, 0);
    }

    @Test
    void searchDiskFiles_limitRestrictsResults() throws IOException {
        // GIVEN - three files
        Files.writeString(tempDir.resolve("A.java"), "");
        Files.writeString(tempDir.resolve("B.java"), "");
        Files.writeString(tempDir.resolve("C.java"), "");

        // unlimited returns all three
        assertEquals(3, countHits(tool.diskSearchFiles("*.java", 0)));

        // limit=1 returns exactly one + cap disclosure
        String limited1 = tool.diskSearchFiles("*.java", 1);
        assertEquals(1, countHits(limited1));
        assertTrue(limited1.contains("capped at 1 — narrow your search"), limited1);

        // limit=2 returns exactly two + cap disclosure
        String limited2 = tool.diskSearchFiles("*.java", 2);
        assertEquals(2, countHits(limited2));
        assertTrue(limited2.contains("capped at 2 — narrow your search"), limited2);
    }

    @Test
    void searchDiskFilesDisclosesCap() throws IOException {
        // UC-OD-2
        // GIVEN - 80 matching files, default limit 50
        for (int i = 0; i < 80; i++) {
            Files.writeString(tempDir.resolve(String.format("f%02d.java", i)), "");
        }

        // WHEN
        String result = tool.diskSearchFiles("f*.java", null);

        // THEN - 50 hits and the cap is named
        assertEquals(50, countHits(result));
        assertTrue(result.contains("capped at 50 — narrow your search"), result);
    }

    @Test
    void searchDiskFilesUnlimitedNoDisclosure() throws IOException {
        // UC-OD-3
        // GIVEN - 30 matching files
        for (int i = 0; i < 30; i++) {
            Files.writeString(tempDir.resolve(String.format("g%02d.java", i)), "");
        }

        // WHEN
        String result = tool.diskSearchFiles("g*.java", 0);

        // THEN - all 30 hits, nothing was cropped → no disclosure
        assertEquals(30, countHits(result));
        assertFalse(result.contains("capped at"), result);
    }

    private static long countHits(String result) {
        return result.lines().filter(l -> l.endsWith(".java")).count();
    }

    @Test
    void searchDiskFiles_emptyQuery() {
        assertThrows(IllegalArgumentException.class, () -> tool.diskSearchFiles("", 0));
        assertThrows(IllegalArgumentException.class, () -> tool.diskSearchFiles(null, 0));
    }

    @Test
    void readDiskFile_absolutePath() throws IOException {
        Path abs = tempDir.resolve("abs.txt");
        Files.writeString(abs, "absolute");
        assertTrue(tool.diskReadFile(abs.toString(), 0, 0).contains("absolute"),
                tool.diskReadFile(abs.toString(), 0, 0));
    }

    // TODO: https://github.com/sterlp/eclipse-peon-ai/pull/58
    @Test
    void readDiskFile_lineNumbersStartAtOne() throws IOException {
        Files.writeString(tempDir.resolve("lines.txt"), "alpha\nbeta\ngamma\ndelta\nepsilon");
        // R9: full file - line numbers must start at 1
        String result = tool.diskReadFile("lines.txt", 0, 0);
        assertEquals("   1: alpha\n   2: beta\n   3: gamma\n   4: delta\n   5: epsilon\n",
                result);
    }

    @Test
    void readDiskFile_fromToPreservesActualLineNumbers() throws IOException {
        Files.writeString(tempDir.resolve("lines.txt"), "alpha\nbeta\ngamma\ndelta\nepsilon");
        // reading lines 3-5 must show line numbers 3, 4, 5 - not reset to 1
        String result = tool.diskReadFile("lines.txt", 3, 5);
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
        String result = tool.diskReadFile("lines.txt", 2, 3);
        assertEquals(
                "   2: beta\n" +
                "   3: gamma\n",
                result);
    }

    @Test
    void readDiskFile_endLineBeyondEndIsClamped() throws IOException {
        Files.writeString(tempDir.resolve("lines.txt"), "alpha\nbeta\ngamma");

        String result = tool.diskReadFile("lines.txt", 2, 99);

        assertEquals("   2: beta\n   3: gamma\n", result);
    }

    @Test
    void readDiskFile_startBeyondEndReturnsHint() throws IOException {
        Files.writeString(tempDir.resolve("lines.txt"), "alpha\nbeta\ngamma");

        String result = tool.diskReadFile("lines.txt", 99, 0);

        assertEquals("file has 3 lines, requested start 99", result);
    }
    
    @Test
    void testUtf8() throws IOException, URISyntaxException {
        // GIVEN
        Path resource = Path.of(getClass().getClassLoader().getResource("utf-8-test.txt").toURI());

        // WHEN
        var result = tool.diskReadFile(resource.normalize().toString(), null, null);

        // THEN
        assertThat(result).isEqualTo("   1: äüß Ö ⚡\n");
    }
}
