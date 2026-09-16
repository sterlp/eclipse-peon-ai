package org.sterl.llmpeon.docslinter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocsLinterDocsTest {

    private static final Pattern DEFAULT_PATTERN = Pattern.compile(
            DocsLinterTool.DEFAULT_ID_PATTERN);

    @TempDir
    Path rootDir;

    private Path docsDir;

    @BeforeEach
    void setUp() throws IOException {
        docsDir = rootDir.resolve("docs");
        Files.createDirectories(docsDir);
    }

    @Test
    void reportsDuplicateDefinitionWithEveryLocation() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule

                ## UC-DL-1 First
                """);

        writeDoc("b.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-2 Other Rule

                ## UC-DL-1 Also here
                """);

        var result = lint();

        assertThat(result.findings())
                .filteredOn(f -> f.type() == FindingType.DOPPELT_DEFINIERT && f.id().equals("UC-DL-1"))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.file()).isEqualTo("docs/a.md");
                    assertThat(f.line()).isEqualTo(7);
                    assertThat(f.extraLines()).containsExactly("docs/b.md:7");
                });
    }

    @Test
    void reportsDuplicateDefinitionWithinSameFile() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule

                ## UC-DL-1 First

                ## UC-DL-1 Second
                """);

        var result = lint();

        assertThat(result.findings())
                .filteredOn(f -> f.type() == FindingType.DOPPELT_DEFINIERT && f.id().equals("UC-DL-1"))
                .singleElement()
                .satisfies(f -> {
                    assertThat(f.file()).isEqualTo("docs/a.md");
                    assertThat(f.line()).isEqualTo(7);
                    assertThat(f.extraLines()).containsExactly("docs/a.md:9");
                });
    }
    @Test
    void reportsForeignPrefixInParticipatingDoc() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-XX-1 Foreign Rule

                ## UC-DL-1 Good UC
                """);

        var result = lint();

        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.PRAEFIX_FREMD && f.id().equals("R-XX-1"));
    }

    // UC-DL-58
    @Test
    void allowsMultipleDocsToShareFeaturePrefix() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: READTOOLS
                ---

                # R-READTOOLS-1 First ✅ done

                ## UC-READTOOLS-1 Alpha ✅
                """);

        writeDoc("b.md", """
                ---
                idPrefix: READTOOLS
                ---

                # R-READTOOLS-2 Second ✅ done

                ## UC-READTOOLS-2 Beta ✅
                """);

        var result = lint();

        assertThat(result.lintedDocs()).containsExactly("docs/a.md", "docs/b.md");
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void reportsMissingPrefixWhenDocDefinesUseCases() throws IOException {
        writeDoc("a.md", """
                # R-DL-1 Rule

                ## UC-DL-1 UC
                """);

        var result = lint();

        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.PRAEFIX_FEHLT && f.id().equals("R-DL-1"));
    }

    @Test
    void reportsUseCaseWithoutParentRule() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                ## UC-DL-1 Orphan
                """);

        var result = lint();

        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.UC_OHNE_REGEL && f.id().equals("UC-DL-1"));
    }

    @Test
    void reportsMissingEffectiveStatus() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 No Status Rule

                ## UC-DL-1 No Status UC
                """);

        var result = lint();

        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.STATUS_FEHLT && f.id().equals("UC-DL-1"));
    }

    @Test
    void reportsUseCaseDefinitionWrittenAsBullet() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                - UC-DL-1 Bullet style
                """);

        var result = lint();

        assertThat(result.findings()).anyMatch(f ->
                f.type() == FindingType.FORM_ABWEICHEND && f.id().equals("UC-DL-1"));
    }

    @Test
    void skipsPlainDocAndNamesItAsNotParticipating() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Test Rule
                """);

        writeDoc("plain.md", """
                # Just a heading
                Some text.
                """);

        var result = lint();

        assertThat(result.lintedDocs()).contains("docs/a.md");
        assertThat(result.skippedDocs()).contains("docs/plain.md");
    }

    @Test
    void mergesMultipleDocRoots() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: A
                ---

                # R-A-1 First
                """);

        Path otherDir = rootDir.resolve("other");
        Files.createDirectories(otherDir);
        Files.writeString(otherDir.resolve("b.md"), """
                ---
                idPrefix: B
                ---

                # R-B-1 Second
                """);

        var result = new DocsLinter().lint(rootDir, List.of("docs", "other"), DEFAULT_PATTERN);

        assertThat(result.definitions()).hasSize(2);
        assertThat(result.lintedDocs()).contains("docs/a.md", "other/b.md");
    }

    @Test
    void sortsDiscoveredFilesForDeterministicResults() throws IOException {
        writeDoc("c.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 C
                """);
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-2 A
                """);
        writeDoc("b.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-3 B
                """);

        var result = lint();

        assertThat(result.lintedDocs()).containsExactly("docs/a.md", "docs/b.md", "docs/c.md");
    }

    @Test
    void handlesEmptyDocRoot() throws IOException {
        var result = lint();

        assertThat(result.definitions()).isEmpty();
        assertThat(result.findings()).isEmpty();
        assertThat(result.lintedDocs()).isEmpty();
        assertThat(result.useCaseCount()).isEqualTo(0);
    }

    @Test
    void rejectsMissingDocRoot() {
        assertThatThrownBy(() -> new DocsLinter().lint(rootDir, List.of("nonexistent"), DEFAULT_PATTERN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Doc root not found");
    }

    @Test
    void rejectsEscapingDocRoot() {
        assertThatThrownBy(() -> new DocsLinter().lint(rootDir, List.of("../outside"), DEFAULT_PATTERN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findingsAreDeterministicallySorted() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule

                ## UC-DL-1 UC

                ## UC-DL-1 UC
                """);

        var result1 = lint();
        var result2 = lint();

        assertThat(result1.findings()).isEqualTo(result2.findings());
    }

    @Test
    void countsUseCaseDefinitionsCorrectly() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-1 First

                ## UC-DL-2 Second

                ## UC-DL-3 Third
                """);

        var result = lint();

        assertThat(result.useCaseCount()).isEqualTo(3);
    }

    @Test
    void excludesUseCaseReferencesFromDefinitions() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-1 Defined UC

                See also UC-DL-2 in text.
                """);

        var result = lint();

        assertThat(result.definitions()).hasSize(2);
        assertThat(result.useCaseCount()).isEqualTo(1);
        assertThat(result.definitions()).extracting(DocDefinition::id)
                .containsExactly("R-DL-1", "UC-DL-1");
    }

    @Test
    void sortsDiscoveredFilesAlphabetically() throws IOException {
        // Characterization test: proves Files are sorted after discovery, not left in walk order.
        // Write in reverse alphabetical order; assert sorted output.
        writeDoc("z.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-1 UC

                ## UC-DL-2 UC
                """);
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-3 Another ✅

                ## UC-DL-3 UC
                """);

        var result = lint();

        // Files must be discovered in sorted order: a.md before z.md
        assertThat(result.lintedDocs()).containsExactly("docs/a.md", "docs/z.md");
    }

    // --- UC-DL-42: ignoresDuplicateHeadingInsideBacktickFence ---
    @Test
    void ignoresDuplicateHeadingInsideBacktickFence() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule

                ## UC-DL-42 First

                ```markdown
                # R-DL-1 Rule

                ## UC-DL-42 First
                ```

                ## UC-DL-43 Other
                """);

        var result = lint();

        assertThat(result.definitions()).extracting(DocDefinition::id)
                .containsExactly("R-DL-1", "UC-DL-42", "UC-DL-43");
        assertThat(result.findings()).noneMatch(f ->
                f.type() == FindingType.DOPPELT_DEFINIERT && f.id().equals("UC-DL-42"));
        assertThat(result.useCaseCount()).isEqualTo(2);
    }

    private void writeDoc(String name, String content) throws IOException {
        Files.writeString(docsDir.resolve(name), content);
    }

    private DocsLintResult lint() {
        try {
            return new DocsLinter().lint(rootDir, List.of("docs"), DEFAULT_PATTERN);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}