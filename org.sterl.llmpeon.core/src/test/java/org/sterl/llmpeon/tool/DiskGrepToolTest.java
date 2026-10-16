package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.shared.SearchQuery;
import org.sterl.llmpeon.tool.tools.DiskGrepTool;

class DiskGrepToolTest {

    @TempDir
    Path tempDir;

    DiskGrepTool tool;

    @BeforeEach
    void setUp() {
        tool = new DiskGrepTool(tempDir);
    }

    @Test
    void invalidPatternFallsBackToLiteral() throws IOException {
        Files.writeString(tempDir.resolve("MetaChars.java"), "FOO(BAR and foo(bar");

        String result = tool.diskGrepFiles("foo(bar", null, ".java");

        assertThat(result)
                .contains("MetaChars.java:1: FOO(BAR and foo(bar")
                .contains("literal search — query is not a valid regex");
    }

    @Test
    void grepListsMatchLinesWithLineNumbers() throws IOException {
        // R8
        // GIVEN a file where the match sits on a known 1-based line
        Files.writeString(tempDir.resolve("Known.java"), "line one\nline two\nTARGET line three");
        // WHEN grepped
        String result = tool.diskGrepFiles("TARGET", null, ".java");
        // THEN the matched line appears with its line number (unpadded), no cap disclosure (under cap)
        assertThat(result)
                .contains("Known.java:3: TARGET line three")
                .doesNotContain("matched lines — narrow your search");
    }

    @Test
    void noMatchesReportsEmpty() throws IOException {
        Files.writeString(tempDir.resolve("Example.java"), "class Example {}");

        String result = tool.diskGrepFiles("zzz-does-not-exist", null, ".java");

        assertThat(result)
                .contains("no matches")
                .contains("regex search")
                .doesNotContain("No files found.")
                .doesNotContain("File type filter: known text extensions and filenames only.");
    }

    @Test
    void sharedExtensionsApplyToDisk() throws IOException {
        Files.writeString(tempDir.resolve("Build.bnd"), "sharedExtensionToken");

        String result = tool.diskGrepFiles("sharedExtensionToken", null, null);

        assertThat(result).contains("Build.bnd:1: sharedExtensionToken");
    }

    @Test
    void namesTypeFilterOnEmptyResult() throws IOException {
        Files.writeString(tempDir.resolve("notes.peonx"), "peonx-only-token");

        String result = tool.diskGrepFiles("peonx-only-token", null, null);

        assertThat(result)
                .contains("no matches")
                .contains("File type filter: known text extensions and filenames only.");
    }

    @Test
    void matchesSharedSearchSemantics() throws IOException {
        String content = "ModelBigWidget\nModelSmallWidget\nModelService";
        Files.writeString(tempDir.resolve("Models.java"), content);
        var query = SearchQuery.of("Model.*Widget");

        String result = tool.diskGrepFiles(query.query(), null, ".java");

        assertThat(result)
                .contains("Models.java:1: ModelBigWidget")
                .contains("Models.java:2: ModelSmallWidget")
                .doesNotContain("Models.java:3: ModelService")
                .contains(query.modeHint());
    }

    @Test
    void fileTargetReturnsMatchesWithLineNumbers() throws IOException {
        // GIVEN a 3-line file, the match sits on line 3 — grepped via the explicit file path
        var file = tempDir.resolve("Known.java");
        Files.writeString(file, "line one\nline two\nTARGET line three");
        // WHEN grepped with the file path as target
        String result = tool.diskGrepFiles("TARGET", file.toString(), null);
        // THEN the hit comes in R8 format like the directory grep, no cap disclosure
        assertThat(result)
                .contains("Known.java:3: TARGET line three")
                .doesNotContain("matched lines — narrow your search")
                .doesNotContain("capped at");
    }

    @Test
    void fileTargetWithoutMatchesReportsEmpty() throws IOException {
        // GIVEN a file that does not contain the query
        var file = tempDir.resolve("Quiet.java");
        Files.writeString(file, "nothing to see here");
        // WHEN grepped via the file path
        String result = tool.diskGrepFiles("zzz-does-not-exist", file.toString(), null);
        // THEN an honest empty report with the mode hint — no IAE, no false negative
        assertThat(result)
                .contains("no matches")
                .contains("regex search");
    }

    @Test
    void fileTargetBeatsExtensionFilter() throws IOException {
        // GIVEN a .py file with the token — an explicit file target beats the extension filter
        var file = tempDir.resolve("foo.py");
        Files.writeString(file, "needle = 1");
        // WHEN grepped via the file path with an extension that would exclude it in a directory walk
        String result = tool.diskGrepFiles("needle", file.toString(), ".java");
        // THEN the hit still comes through
        assertThat(result).contains("foo.py:1: needle = 1");
        // AND — no-match case with the extension: the disclosure must not claim a filter that
        // never happened on a file target (C-EXT) — the standard no-extension filter hint is shown
        String empty = tool.diskGrepFiles("zzz-does-not-exist", file.toString(), ".java");
        assertThat(empty)
                .contains("no matches")
                .contains("File type filter: known text extensions and filenames only.");
    }

    @Test
    void nonExistentPathThrowsPathNotFound() {
        // WHEN the target path does not exist (neither directory nor file)
        // THEN an honest error with the Eclipse-family message — no silent false negative
        assertThatThrownBy(() -> tool.diskGrepFiles("anything", tempDir.resolve("no-such-path").toString(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path not found:");
    }

    @Test
    void binaryFileTargetFailsHonestly() throws IOException {
        // GIVEN a file with invalid UTF-8 (binary content)
        var file = tempDir.resolve("binary.bin");
        Files.write(file, new byte[] { (byte) 0xFF, (byte) 0xFE, (byte) 0x00, (byte) 0x81 });
        // WHEN grepped via the file path
        // THEN an honest IAE naming the file — no silent false negative
        assertThatThrownBy(() -> tool.diskGrepFiles("anything", file.toString(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Grep file");
    }
}
