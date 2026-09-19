package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

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
}
