package org.sterl.llmpeon.docslinter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DocsLinterMatchingTest {

    private static final Pattern ID_PATTERN = Pattern.compile(DocsLinterTool.DEFAULT_ID_PATTERN);

    @TempDir
    Path rootDir;

    private Path docsDir;
    private Path testDir;

    @BeforeEach
    void setUp() throws IOException {
        docsDir = rootDir.resolve("docs");
        testDir = rootDir.resolve("src").resolve("test").resolve("java");
        Files.createDirectories(docsDir);
        Files.createDirectories(testDir);
    }

    // --- UC-DL-1: usesDefaultDocAndTestRoots ---
    @Test
    void usesDefaultDocAndTestRoots() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-1 Tested UC ✅
                """);
        writeTest("Test.java", """
                // UC-DL-1
                void testIt() {}""");

        var result = new DocsLinter().lintWithTests(rootDir,
                List.of("docs"), List.of("."), null, ID_PATTERN);

        assertThat(result.useCaseCount()).isEqualTo(1);
        assertThat(result.testEvidenceCount()).isEqualTo(1);
        assertThat(result.findings()).isEmpty();
    }

    // --- UC-DL-2: mergesMultipleDocAndTestRoots ---
    @Test
    void mergesMultipleDocAndTestRoots() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: A
                ---

                # R-A-1 Rule ✅ done

                ## UC-A-1 UC ✅
                """);
        Files.createDirectories(rootDir.resolve("other-docs"));
        Files.writeString(rootDir.resolve("other-docs/b.md"), """
                ---
                idPrefix: B
                ---

                # R-B-1 Rule ✅ done

                ## UC-B-1 UC ✅
                """);

        writeTest("ATest.java", "// UC-A-1\nvoid a(){}");
        var otherTestDir = rootDir.resolve("other-tests");
        Files.createDirectories(otherTestDir);
        Files.writeString(otherTestDir.resolve("BTest.java"), "// UC-B-1\nvoid b(){}");

        var result = new DocsLinter().lintWithTests(rootDir,
                List.of("docs", "other-docs"), List.of("src/test/java", "other-tests"),
                null, ID_PATTERN);

        assertThat(result.useCaseCount()).isEqualTo(2);
        assertThat(result.testEvidenceCount()).isEqualTo(2);
    }

    // --- UC-DL-37: mergesMultipleTestGlobsAndDeduplicatesFiles ---
    @Test
    void mergesMultipleTestGlobsAndDeduplicatesFiles() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-371 UC ✅

                ## UC-DL-372 UC ✅

                ## UC-DL-373 UC ✅
                """);
        writeTest("Comp.ts", "// UC-DL-371\nfunction testTs() {}");
        writeTest("Comp.tsx", "// UC-DL-372\nfunction testTsx() {}");
        writeTest("Comp.go", "// UC-DL-373\nfunc TestGo(t *testing.T) {}");

        // Overlapping glob **/*.ts appears twice on purpose; Comp.ts must still count once.
        var result = new DocsLinter().lintWithTests(rootDir,
                List.of("docs"), List.of("src/test/java"),
                List.of("**/*.ts", "**/*.tsx", "**/*.go", "**/*.ts"), ID_PATTERN);

        assertThat(result.testEvidenceCount()).isEqualTo(3);
        assertThat(result.findings()).isEmpty();
    }

    // --- UC-DL-34: acceptsUnknownTestSyntaxAsCoverage ---
    @Test
    void acceptsUnknownTestSyntaxAsCoverage() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-34 UC ✅
                """);
        writeTest("Test.java", "// UC-DL-34\nit('unknown syntax', () => {});");

        var result = new DocsLinter().lintWithTests(rootDir,
                List.of("docs"), List.of("src/test/java"), null, ID_PATTERN);

        assertThat(result.testEvidenceCount()).isEqualTo(1);
        assertThat(result.findings()).isEmpty();
    }

    // --- UC-DL-3: filtersTestSourcesByConfiguredGlob ---
    @Test
    void filtersTestSourcesByConfiguredGlob() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-1 UC ✅
                """);
        writeTest("Test.java", "// UC-DL-1\nvoid testJava(){}");
        Files.writeString(testDir.resolve("Test.kt"), "// UC-DL-1\nfun testKotlin(){}");

        var result = new DocsLinter().lintWithTests(rootDir,
                List.of("docs"), List.of("src/test/java"), List.of("*.java"), ID_PATTERN);

        // Only Test.java matches; Test.kt is filtered out
        assertThat(result.testEvidenceCount()).isEqualTo(1);
    }

    // --- matchesDefaultGlobForFileDirectlyInTestRoot ---
    @Test
    void matchesDefaultGlobForFileDirectlyInTestRoot() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-1 UC ✅
                """);
        // Java file directly in test root, no subdirectory
        Files.writeString(rootDir.resolve("Test.java"),
                "// UC-DL-1\nvoid testIt(){}");

        var result = new DocsLinter().lintWithTests(rootDir,
                List.of("docs"), List.of("."), null, ID_PATTERN);

        assertThat(result.testEvidenceCount()).isEqualTo(1);
    }

    // --- UC-DL-38: usesTextFileTypeCodeExtensionsByDefault ---
    @Test
    void usesTextFileTypeCodeExtensionsByDefault() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-381 UC ✅

                ## UC-DL-382 UC ✅

                ## UC-DL-383 UC ✅
                """);
        writeTest("a.ts", "// UC-DL-381\nfunction testTs() {}");
        writeTest("b.py", "# UC-DL-382\ndef test_py(): pass");
        writeTest("c.sql", "-- UC-DL-383\nSELECT 1;");

        // No testGlobs given — must discover non-Java code extensions from TextFileTypes.
        var result = new DocsLinter().lintWithTests(rootDir,
                List.of("docs"), List.of("src/test/java"), null, ID_PATTERN);

        assertThat(result.testEvidenceCount()).isEqualTo(3);
        assertThat(result.findings()).isEmpty();
    }

    // --- UC-DL-38: excludesEveryNonCodeTextExtensionFromDefaultScan ---
    static Stream<Arguments> nonCodeTextExtensions() {
        return Stream.of(
                Arguments.of("md", "UC-DL-101"),
                Arguments.of("txt", "UC-DL-102"),
                Arguments.of("json", "UC-DL-103"),
                Arguments.of("xml", "UC-DL-104"),
                Arguments.of("csv", "UC-DL-105"),
                Arguments.of("yaml", "UC-DL-106"),
                Arguments.of("yml", "UC-DL-107"),
                Arguments.of("properties", "UC-DL-108"),
                Arguments.of("cfg", "UC-DL-109"),
                Arguments.of("ini", "UC-DL-110"),
                Arguments.of("toml", "UC-DL-111"));
    }

    @ParameterizedTest
    @MethodSource("nonCodeTextExtensions")
    void excludesEveryNonCodeTextExtensionFromDefaultScan(String extension, String ucId)
            throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## %s UC ✅
                """.formatted(ucId));
        writeTest("ignored." + extension, "// " + ucId + "\ncontent");

        var result = new DocsLinter().lintWithTests(rootDir,
                List.of("docs"), List.of("src/test/java"), null, ID_PATTERN);

        // The file must not be read as a test source; the UC stays uncovered-done.
        assertThat(result.testEvidenceCount()).isEqualTo(0);
        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.UNBELEGT_ERLEDIGT && f.id().equals(ucId));
    }

    // --- UC-DL-39: excludesDocRootsFromTestSources ---
    @Test
    void excludesDocRootsFromTestSources() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-391 UC ✅

                ## UC-DL-392 UC ✅
                """);
        // Real test source outside docs covers UC-DL-391.
        Files.writeString(rootDir.resolve("RealTest.java"), "// UC-DL-391\nvoid real(){}");
        // Code file inside docs must never count as test evidence.
        Files.writeString(docsDir.resolve("FakeTest.java"), "// UC-DL-392\nvoid fake(){}");

        var result = new DocsLinter().lintWithTests(rootDir,
                List.of("docs"), List.of("."), null, ID_PATTERN);

        assertThat(result.findings()).noneMatch(f -> f.id().equals("UC-DL-391"));
        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.UNBELEGT_ERLEDIGT && f.id().equals("UC-DL-392"));
    }

    // --- UC-DL-11: ignoresTestsWithoutUseCaseId ---
    @Test
    void ignoresTestsWithoutUseCaseId() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-1 UC ✅
                """);
        // Test that covers UC-DL-1
        writeTest("CoveredTest.java", "// UC-DL-1\nvoid testIt(){}");
        // Test without ID comment
        writeTest("NoIdTest.java", "void noCommentMethod(){}");

        var result = new DocsLinter().lintWithTests(rootDir,
                List.of("docs"), List.of("src/test/java"), null, ID_PATTERN);

        // NoIdTest.java should not produce any findings
        assertThat(result.findings()).isEmpty();
        assertThat(result.testEvidenceCount()).isEqualTo(1);
    }

    // --- UC-DL-12: reportsUncoveredDoneUseCaseFirst ---
    @Test
    void reportsUncoveredDoneUseCaseFirst() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-5 Uncovered Done ✅

                ## UC-DL-6 Open with explicit ❌ specified
                """);

        var result = new DocsLinter().lintWithTests(rootDir,
                List.of("docs"), List.of("src/test/java"), null, ID_PATTERN);

        // Must have at least two findings of different types to prove ordering
        assertThat(result.findings().stream()
                .anyMatch(f -> f.type() == FindingType.UNBELEGT_ERLEDIGT
                        && f.id().equals("UC-DL-5"))).isTrue();
        assertThat(result.findings().stream()
                .anyMatch(f -> f.type() == FindingType.UNBELEGT
                        && f.id().equals("UC-DL-6"))).isTrue();
        assertThat(result.findings().size()).isGreaterThanOrEqualTo(2);

        // UNBELEGT_ERLEDIGT must come before UNBELEGT in sorted findings
        int idxDone = indexOfType(result, FindingType.UNBELEGT_ERLEDIGT);
        int idxOpen = indexOfType(result, FindingType.UNBELEGT);
        assertThat(idxDone).isNotNegative();
        assertThat(idxOpen).isNotNegative();
        assertThat(idxDone).isLessThan(idxOpen);
    }

    // --- UC-DL-13: reportsUncoveredOpenUseCaseAsInventory ---
    @Test
    void reportsUncoveredOpenUseCaseAsInventory() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ❌ specified

                ## UC-DL-6 Uncovered Open
                """);

        var result = new DocsLinter().lintWithTests(rootDir,
                List.of("docs"), List.of("src/test/java"), null, ID_PATTERN);

        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.UNBELEGT
                        && f.id().equals("UC-DL-6"));
    }

    // --- UC-DL-14: reportsOrphanTestIdAtCommentLocation ---
    @Test
    void reportsOrphanTestIdAtCommentLocation() throws IOException {
        writeTest("OrphanTest.java", """
                // UC-DL-7
                void orphanMethod(){}""");

        var result = new DocsLinter().lintWithTests(rootDir,
                List.of("docs"), List.of("src/test/java"), null, ID_PATTERN);

        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.VERWAIST && f.id().equals("UC-DL-7"));
        var orphan = result.findings().stream()
                .filter(f -> f.type() == FindingType.VERWAIST).findFirst().orElseThrow();
        assertThat(orphan.file()).contains("OrphanTest");
        assertThat(orphan.line()).isEqualTo(1);
    }

    // --- UC-DL-15: acceptsMultipleTestsForOneUseCase ---
    @Test
    void acceptsMultipleTestsForOneUseCase() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-4 UC ✅
                """);
        writeTest("A.java", "// UC-DL-4\nvoid a(){}");
        writeTest("B.java", "// UC-DL-4\nvoid b(){}");

        var result = new DocsLinter().lintWithTests(rootDir,
                List.of("docs"), List.of("src/test/java"), null, ID_PATTERN);

        assertThat(result.testEvidenceCount()).isEqualTo(2);
        assertThat(result.findings().stream()
                .noneMatch(f -> f.id().equals("UC-DL-4"))).isTrue();
    }

    // --- Counts correct ---
    @Test
    void countsUseCaseDefinitionsAndTestIdOccurrencesEvenWhenNoFindingExists() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-1 UC ✅

                ## UC-DL-2 UC ✅
                """);
        writeTest("Test.java", "// UC-DL-1\nvoid a(){}\n// UC-DL-2\nvoid b(){}");

        var result = new DocsLinter().lintWithTests(rootDir,
                List.of("docs"), List.of("src/test/java"), null, ID_PATTERN);

        assertThat(result.useCaseCount()).isEqualTo(2);
        assertThat(result.testEvidenceCount()).isEqualTo(2);
        assertThat(result.uniqueTestIdCount()).isEqualTo(2);
        assertThat(result.findings()).isEmpty();
    }

    // --- lintDocs doesn't read tests ---
    @Test
    void lintDocsReportsTestsNotRead() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-1 UC ✅
                """);

        var result = new DocsLinter().lint(rootDir, List.of("docs"), ID_PATTERN);

        assertThat(result.testsRead()).isFalse();
        assertThat(result.testEvidenceCount()).isEqualTo(0);
    }

    // --- UC-DL-65: a ✅ UC with a manuell annotation is exempt from UNBELEGT_ERLEDIGT ---
    // UC-DL-65
    @Test
    void manuellMarkerExemptsDoneUcFromUnbelegtErledigt() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-XX-1 Manuell UC ✅ *(manuell verifiziert 2026-09-21, ADR-0051)*
                """);

        var result = new DocsLinter().lintWithTests(rootDir,
                List.of("docs"), List.of("src/test/java"), null, ID_PATTERN);

        assertThat(result.findings()).noneMatch(f ->
                f.type() == FindingType.UNBELEGT_ERLEDIGT && f.id().equals("UC-XX-1"));
        var manuell = result.findings().stream()
                .filter(f -> f.type() == FindingType.MANUELL && f.id().equals("UC-XX-1"))
                .findFirst().orElseThrow();
        assertThat(manuell.file()).isEqualTo("docs/a.md");
        assertThat(manuell.line()).isPositive();
    }

    // --- UC-DL-65: real form without asterisks (manuelle …) also exempts ---
    // UC-DL-65
    @Test
    void manuellMarkerWithoutAsterisksExemptsDoneUc() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-XX-1 Manuell UC ✅ (manuelle Verifikation 2026-09-21, ADR-0051)
                """);

        var result = new DocsLinter().lintWithTests(rootDir,
                List.of("docs"), List.of("src/test/java"), null, ID_PATTERN);

        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.MANUELL && f.id().equals("UC-XX-1"));
        assertThat(result.findings()).noneMatch(f ->
                f.type() == FindingType.UNBELEGT_ERLEDIGT && f.id().equals("UC-XX-1"));
    }

    // --- UC-DL-66: ✅ without a manuell annotation stays UNBELEGT_ERLEDIGT ---
    // UC-DL-66
    @Test
    void doneWithoutManuellMarkerStaysUnbelegtErledigt() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-XX-2 Plain Done UC ✅
                """);

        var result = new DocsLinter().lintWithTests(rootDir,
                List.of("docs"), List.of("src/test/java"), null, ID_PATTERN);

        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.UNBELEGT_ERLEDIGT && f.id().equals("UC-XX-2"));
        assertThat(result.findings()).noneMatch(f ->
                f.type() == FindingType.MANUELL && f.id().equals("UC-XX-2"));
    }

    private int indexOfType(DocsLintResult result, FindingType type) {
        for (int i = 0; i < result.findings().size(); i++) {
            if (result.findings().get(i).type() == type) return i;
        }
        return -1;
    }

    private void writeDoc(String name, String content) throws IOException {
        Files.writeString(docsDir.resolve(name), content);
    }

    private void writeTest(String name, String content) throws IOException {
        Files.writeString(testDir.resolve(name), content);
    }
}
