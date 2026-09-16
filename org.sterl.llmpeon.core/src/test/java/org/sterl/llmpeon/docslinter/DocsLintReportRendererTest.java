package org.sterl.llmpeon.docslinter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocsLintReportRendererTest {

    private static final Pattern ID_PATTERN = Pattern.compile(DocsLinterTool.DEFAULT_ID_PATTERN);

    @TempDir
    Path rootDir;

    private Path docsDir;
    private Path testDir;
    private DocsLintReportRenderer renderer;

    @BeforeEach
    void setUp() throws IOException {
        docsDir = rootDir.resolve("docs");
        testDir = rootDir.resolve("src").resolve("test").resolve("java");
        Files.createDirectories(docsDir);
        Files.createDirectories(testDir);
        renderer = new DocsLintReportRenderer();
    }

    // --- UC-DL-4: summary names disk as read source ---
    // UC-DL-4
    @Test
    void namesDiskAsReadSource() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done
                """);

        var result = lint(rootDir, List.of("docs"), null);
        String summary = renderer.summary(result, rootDir);

        assertThat(summary).startsWith("Disk read — unsaved editor changes are not included.");
    }

    // --- UC-DL-23: 2 opt-in + 66 opt-out ---
    @Test
    void listsEveryNonParticipatingDocWithoutTruncation() throws IOException {
        // 2 opt-in docs
        writeDoc("optin-a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done
                """);
        writeDoc("optin-b.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-2 Rule ✅ done
                """);

        // 66 non-participating docs (more than any reasonable cap)
        Path plainDir = rootDir.resolve("plain");
        Files.createDirectories(plainDir);
        for (int i = 1; i <= 66; i++) {
            String name = String.format("plain-%02d.md", i);
            Files.writeString(plainDir.resolve(name), "# Just a doc\n\nNo prefix here.");
        }

        var result = lint(rootDir, List.of("docs", "plain"), null);
        String summary = renderer.summary(result, rootDir);

        assertThat(summary).contains("Not participating (no idPrefix):");
        // All 66 must be listed
        for (int i = 1; i <= 66; i++) {
            assertThat(summary).contains(String.format("plain/plain-%02d.md", i));
        }
        // Count: 2 linted + 66 skipped
        assertThat(result.lintedDocs().size()).isEqualTo(2);
        assertThat(result.skippedDocs().size()).isEqualTo(66);
    }

    // --- UC-DL-27: empty vs clean populated ---
    @Test
    void distinguishesEmptyInputFromCleanInput() throws IOException {
        // Create a second temp dir for the contrasting run
        Path emptyRoot = rootDir.resolve("empty-project");
        Files.createDirectories(emptyRoot.resolve("docs"));

        var emptyResult = lint(emptyRoot, List.of("docs"), null);
        String emptySummary = renderer.summary(emptyResult, emptyRoot);

        assertThat(emptyResult.useCaseCount()).isEqualTo(0);
        assertThat(emptyResult.lintedDocs()).isEmpty();
        assertThat(emptyResult.skippedDocs()).isEmpty();
        assertThat(emptyResult.docFileCount()).isEqualTo(0);
        assertThat(emptySummary).contains("0 doc file(s)");

        // Now a clean populated run
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Covered Rule ✅ done

                ## UC-DL-1 Covered UC ✅
                """);
        writeTest("Test.java", "// UC-DL-1\nvoid testIt() {}");

        var cleanResult = lintWithTests(rootDir, List.of("docs"), List.of("."), null);
        String cleanSummary = renderer.summary(cleanResult, rootDir);

        assertThat(cleanResult.useCaseCount()).isEqualTo(1);
        assertThat(cleanResult.findings()).isEmpty();
        assertThat(cleanResult.docFileCount()).isEqualTo(1);
        assertThat(cleanSummary).contains("1 doc file(s)");
        assertThat(cleanSummary).doesNotContain("UNBELEGT");

        // The summaries must differ
        assertThat(emptySummary).isNotEqualTo(cleanSummary);
    }

    // --- UC-DL-40: reports found file scope in summary ---
    @Test
    void reportsFoundFileScopeInSummary() throws IOException {
        writeDoc("linted-a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-401 UC ✅
                """);
        writeDoc("linted-b.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-2 Rule ✅ done

                ## UC-DL-402 UC ✅
                """);
        Files.writeString(docsDir.resolve("skipped.md"), "# No prefix\n\nNot participating.");

        // Two test source files; only one carries UC IDs.
        writeTest("WithId.java", "// UC-DL-401\nvoid withId(){}");
        writeTest("WithoutId.java", "void withoutId(){}");

        var result = lintWithTests(rootDir, List.of("docs"), List.of("src/test/java"), null);
        String summary = renderer.summary(result, rootDir);

        assertThat(result.docFileCount()).isEqualTo(3);
        assertThat(result.testSourceFileCount()).isEqualTo(2);
        assertThat(result.testSourceFilesWithEvidenceCount()).isEqualTo(1);

        assertThat(summary).contains("3 doc file(s): 2 linted, 1 not participating");
        assertThat(summary).contains("2 test source file(s), 1 carrying UC ids");
    }

    // --- UC-DL-41: distinguishes zero test source files from zero evidence ---
    @Test
    void reportsZeroTestSourceFilesForEmptyTestRoot() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-411 UC ✅
                """);

        // Run A: existing but empty test root -> 0 source files, 0 evidence.
        Path emptyTests = rootDir.resolve("empty-tests");
        Files.createDirectories(emptyTests);
        var emptyResult = lintWithTests(rootDir, List.of("docs"), List.of("empty-tests"), null);
        String emptySummary = renderer.summary(emptyResult, rootDir);

        // Run B: one source file, but it has no UC ID -> 1 source file, 0 evidence.
        writeTest("NoId.java", "void noId(){}");
        var noEvidenceResult = lintWithTests(rootDir, List.of("docs"), List.of("src/test/java"), null);
        String noEvidenceSummary = renderer.summary(noEvidenceResult, rootDir);

        assertThat(emptyResult.testEvidenceCount()).isEqualTo(0);
        assertThat(noEvidenceResult.testEvidenceCount()).isEqualTo(0);
        assertThat(emptyResult.testSourceFileCount()).isEqualTo(0);
        assertThat(noEvidenceResult.testSourceFileCount()).isEqualTo(1);

        assertThat(emptySummary).contains("0 test source file(s)");
        assertThat(noEvidenceSummary).contains("1 test source file(s), 0 carrying UC ids");
    }

    // --- Counts occurrences and unique IDs, including orphan test IDs ---
    @Test
    void countsOccurrencesAndUniqueIdsIncludingOrphans() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-461 UC ✅

                ## UC-DL-461 UC ✅

                ## UC-DL-462 UC ✅
                """);
        // UC-DL-461 is covered; UC-DL-463 is orphan.
        writeTest("Test.java", "// UC-DL-461, UC-DL-463\nvoid mixed(){}");

        var result = lintWithTests(rootDir, List.of("docs"), List.of("src/test/java"), null);
        String summary = renderer.summary(result, rootDir);

        // Definitions: 3 occurrences, 2 unique UCs.
        assertThat(result.useCaseCount()).isEqualTo(3);
        assertThat(result.uniqueUseCaseCount()).isEqualTo(2);
        // Test IDs: 2 occurrences, 2 unique (one known, one orphan).
        assertThat(result.testEvidenceCount()).isEqualTo(2);
        assertThat(result.uniqueTestIdCount()).isEqualTo(2);
        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.VERWAIST && f.id().equals("UC-DL-463"));

        assertThat(summary).contains("UC definitions: 3 / 2");
        assertThat(summary).contains("test IDs: 2 / 2");
    }

    // --- lintDocs summary: tests not read ---
    @Test
    void summarySaysTestsNotReadForLintDocs() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done
                """);

        var result = lint(rootDir, List.of("docs"), null);
        String summary = renderer.summary(result, rootDir);

        assertThat(summary).contains("tests: not read");
        assertThat(summary).doesNotContain("test IDs:");
    }

    // --- lintDocsAndTests summary: includes test counts ---
    @Test
    void summaryIncludesTestCountsForLintDocsAndTests() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-1 Covered UC ✅
                """);
        writeTest("Test.java", "// UC-DL-1\nvoid testIt() {}");

        var result = lintWithTests(rootDir, List.of("docs"), List.of("."), null);
        String summary = renderer.summary(result, rootDir);

        assertThat(summary).contains("UC definitions: 1 / 1");
        assertThat(summary).contains("test IDs: 1 / 1");
    }

    private void writeDoc(String name, String content) throws IOException {
        Files.writeString(docsDir.resolve(name), content);
    }

    private void writeTest(String name, String content) throws IOException {
        Files.writeString(testDir.resolve(name), content);
    }

    private DocsLintResult lint(Path root, List<String> docRoots, String testGlob) throws IOException {
        return new DocsLinter().lint(root, docRoots, ID_PATTERN);
    }

    private DocsLintResult lintWithTests(Path root, List<String> docRoots,
            List<String> testRoots, List<String> testGlobs) throws IOException {
        return new DocsLinter().lintWithTests(root, docRoots, testRoots, testGlobs, ID_PATTERN);
    }
}
