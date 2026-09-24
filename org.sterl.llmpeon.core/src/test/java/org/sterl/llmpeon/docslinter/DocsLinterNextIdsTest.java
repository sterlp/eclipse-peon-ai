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

class DocsLinterNextIdsTest {

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
    void returnsNextRuleAndUseCaseNumberPerPrefix() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule 1

                ## UC-DL-1 First

                ## UC-DL-2 Second

                # R-DL-4 Later rule

                ## UC-DL-7 Last UC
                """);

        var results = nextIds(null);

        assertThat(results).hasSize(1);
        var r = results.get(0);
        assertThat(r.prefix()).isEqualTo("DL");
        assertThat(r.occupied()).isTrue();
        assertThat(r.nextRule()).isEqualTo(5);
        assertThat(r.nextUseCase()).isEqualTo(8);
    }

    @Test
    void neverReusesNumberingGaps() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 First

                ## UC-DL-1 First UC

                ## UC-DL-3 Skipped 2
                """);

        var results = nextIds(null);

        assertThat(results).hasSize(1);
        var r = results.get(0);
        assertThat(r.nextRule()).isEqualTo(2);
        assertThat(r.nextUseCase()).isEqualTo(4);
    }

    @Test
    void reportsUnusedCandidatePrefixAsFree() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule

                ## UC-DL-1 UC
                """);

        var results = nextIds("FOO");

        assertThat(results).hasSize(1);
        var r = results.get(0);
        assertThat(r.prefix()).isEqualTo("FOO");
        assertThat(r.occupied()).isFalse();
        assertThat(r.nextRule()).isEqualTo(1);
        assertThat(r.nextUseCase()).isEqualTo(1);
    }

    @Test
    void listsAllOccupiedPrefixesWhenCandidateIsAbsent() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: AA
                ---

                # R-AA-1 First

                ## UC-AA-1 UC
                """);
        writeDoc("b.md", """
                ---
                idPrefix: BB
                ---

                # R-BB-1 Second

                ## UC-BB-1 UC

                ## UC-BB-2 Another
                """);

        var results = nextIds(null);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).prefix()).isEqualTo("AA");
        assertThat(results.get(0).nextRule()).isEqualTo(2);
        assertThat(results.get(0).nextUseCase()).isEqualTo(2);
        assertThat(results.get(1).prefix()).isEqualTo("BB");
        assertThat(results.get(1).nextRule()).isEqualTo(2);
        assertThat(results.get(1).nextUseCase()).isEqualTo(3);
    }

    // R-DL-23: digit-ending UC ids split at the LAST dash (UC-KUPO-7-1 → family KUPO-7,
    // number 1); non-digit-ending ids keep the legacy first-dash path (UC-KUPO-19-4b → family KUPO).
    @Test
    void handlesLegacyHierarchicalUseCaseIds() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: KUPO
                ---

                # R-KUPO-1 Rule

                ## UC-KUPO-19-4b Legacy

                ## UC-KUPO-7-1 Another legacy
                """);

        var results = nextIds(null);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).prefix()).isEqualTo("KUPO");
        assertThat(results.get(0).nextRule()).isEqualTo(2);
        assertThat(results.get(0).nextUseCase()).isEqualTo(20); // UC-KUPO-19-4b → legacy path, family KUPO
        assertThat(results.get(1).prefix()).isEqualTo("KUPO-7");
        assertThat(results.get(1).nextRule()).isEqualTo(1);
        assertThat(results.get(1).nextUseCase()).isEqualTo(2); // UC-KUPO-7-1 → number 1
    }

    @Test
    void includesForeignPrefixInOccupiedCount() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule

                ## UC-DL-1 UC

                # R-XX-5 Foreign rule
                """);

        var results = nextIds(null);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(NextIds::prefix).containsExactly("DL", "XX");
        assertThat(results.get(1).nextRule()).isEqualTo(6);
        assertThat(results.get(1).nextUseCase()).isEqualTo(1);
    }

    @Test
    void excludesDocsWithoutPrefixFromNextIds() throws IOException {
        writeDoc("a.md", """
                # R-DL-1 No prefix doc

                ## UC-DL-1 UC
                """);

        var results = nextIds(null);

        assertThat(results).isEmpty();
    }

    // UC-DL-67
    @Test
    void acceptsHyphenatedPrefix() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: O-TEST
                ---

                # R-O-TEST-1 Rule
                """);

        var results = nextIds("O-TEST");

        assertThat(results).hasSize(1);
        var r = results.get(0);
        assertThat(r.prefix()).isEqualTo("O-TEST");
        assertThat(r.occupied()).isTrue();
        assertThat(r.nextRule()).isEqualTo(2);
        assertThat(r.nextUseCase()).isEqualTo(1);
    }

    // UC-DL-68
    @Test
    void rejectsPrefixWithTrailingHyphen() {
        assertThatThrownBy(() -> new DocsLinter().nextIds(rootDir, List.of("docs"), DEFAULT_PATTERN, "OP-"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("prefix must not start or end with a hyphen: OP-");
    }

    // UC-DL-68
    @Test
    void rejectsPrefixWithLeadingHyphen() {
        assertThatThrownBy(() -> new DocsLinter().nextIds(rootDir, List.of("docs"), DEFAULT_PATTERN, "-OP"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("prefix must not start or end with a hyphen: -OP");
    }

    // UC-DL-68
    @Test
    void rejectsLowercaseOrSymbolPrefix() {
        assertThatThrownBy(() -> new DocsLinter().nextIds(rootDir, List.of("docs"), DEFAULT_PATTERN, "op"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("prefix must be uppercase letters or digits, e.g. ORD or O-TEST: op");
    }

    @Test
    void handlesNoOccupiedPrefixes() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---
                """);

        var results = nextIds("FOO");

        assertThat(results).hasSize(1);
        var r = results.get(0);
        assertThat(r.prefix()).isEqualTo("FOO");
        assertThat(r.occupied()).isFalse();
    }

    @Test
    void sortsResultsAlphabetically() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: ZZ
                ---

                # R-ZZ-1 Last
                """);
        writeDoc("b.md", """
                ---
                idPrefix: AA
                ---

                # R-AA-1 First
                """);

        var results = nextIds(null);

        assertThat(results).extracting(NextIds::prefix).containsExactly("AA", "ZZ");
    }

    @Test
    void usesOnlyRuleDefinitionsForRuleCount() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-5 Rule

                ## UC-DL-9 UC
                """);

        var results = nextIds(null);

        assertThat(results.get(0).nextRule()).isEqualTo(6);
        assertThat(results.get(0).nextUseCase()).isEqualTo(10);
    }

    @Test
    void emptyDocsDirectoryReturnsNoOccupiedPrefixes() throws IOException {
        var results = nextIds(null);
        assertThat(results).isEmpty();
    }

    // UC-DL-59
    @Test
    void aggregatesNextUseCaseAcrossDocsSharingPrefix() throws IOException {
        writeDoc("a-high.md", """
                ---
                idPrefix: READTOOLS
                ---

                # R-READTOOLS-1 High rule ✅ done

                ## UC-READTOOLS-5 Five ✅

                ## UC-READTOOLS-9 Nine ✅
                """);

        writeDoc("z-low.md", """
                ---
                idPrefix: READTOOLS
                ---

                # R-READTOOLS-2 Low rule ✅ done

                ## UC-READTOOLS-1 One ✅

                ## UC-READTOOLS-4 Four ✅
                """);

        var results = nextIds("READTOOLS");

        assertThat(results).hasSize(1);
        var r = results.get(0);
        assertThat(r.prefix()).isEqualTo("READTOOLS");
        assertThat(r.occupied()).isTrue();
        assertThat(r.nextUseCase()).isEqualTo(10);
    }

    private void writeDoc(String name, String content) {
        try {
            Files.writeString(docsDir.resolve(name), content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // UC-DL-56
    @Test
    void repeatedCallOnUsedInstanceMatchesFreshInstance() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule

                ## UC-DL-1 UC
                """);

        DocsLinter reused = new DocsLinter();
        reused.nextIds(rootDir, List.of("docs"), DEFAULT_PATTERN, null);
        NextIdsResult second = reused.nextIds(rootDir, List.of("docs"), DEFAULT_PATTERN, null);

        DocsLinter fresh = new DocsLinter();
        NextIdsResult first = fresh.nextIds(rootDir, List.of("docs"), DEFAULT_PATTERN, null);

        assertThat(second).isEqualTo(first);
    }

    private List<NextIds> nextIds(String prefix) {
        try {
            DocsLinter linter = new DocsLinter();
            return linter.nextIds(rootDir, List.of("docs"), DEFAULT_PATTERN, prefix).nextIds();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}