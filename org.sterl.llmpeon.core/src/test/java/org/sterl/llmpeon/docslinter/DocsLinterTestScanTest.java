package org.sterl.llmpeon.docslinter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.docslinter.TestParser.TestEvidence;

/** R-DL-28: test scans never read foreign trees, selected roots stay readable. */
@Timeout(value = 1)
class DocsLinterTestScanTest {

    private static final Pattern ID_PATTERN = Pattern.compile(DocsLinterTool.DEFAULT_ID_PATTERN);

    @TempDir
    Path rootDir;

    // UC-DL-101
    @Test
    void defaultTestScanIgnoresForeignEvidence() throws IOException {
        writeDoc(1, 2, 3);
        write("src/a.test.ts", "// UC-XX-1\n");
        write("node_modules/lib/b.test.ts", "// UC-XX-2\n");
        write("target/c.java", "// UC-XX-3\n");

        String report = new DocsLinterTool(rootDir, () -> 0L).lintDocsAndTests(null, null, null, null, null);

        assertThat(report).isEqualTo("""
                Disk read — unsaved editor changes are not included.
                Doc roots: docs (auto-discovered)

                Scanned: 1 doc file(s): 1 linted, 0 not participating; 1 test source file(s), 1 carrying UC ids
                Root: %s
                Duration: 0 ms
                Sources: docs, .
                UC definitions: 3 / 3; test IDs: 1 / 1; findings: 2

                  UNBELEGT UC-XX-2 docs/a.md:6
                  UNBELEGT UC-XX-3 docs/a.md:7""".formatted(rootDir.toAbsolutePath().normalize()));
    }

    // UC-DL-102
    @Test
    void explicitTestRootIsIncluded() throws IOException {
        writeDoc(4);
        write("build/gen/d.test.ts", "// UC-XX-4\n");

        var result = scan("build/gen");

        assertThat(result.testSourceFileCount()).isEqualTo(1);
        assertThat(result.testEvidence()).containsExactly(
                new TestEvidence("UC-XX-4", "build/gen/d.test.ts", 1, null));
    }

    // UC-DL-102
    @Test
    void selectedRootStillPrunesForeignChildren() throws IOException {
        writeDoc(1, 2);
        write("build/own.test.ts", "// UC-XX-1\n");
        write("build/node_modules/foreign.test.ts", "// UC-XX-2\n");

        var result = scan("build");

        assertThat(result.testSourceFileCount()).isEqualTo(1);
        assertThat(result.testEvidence()).containsExactly(
                new TestEvidence("UC-XX-1", "build/own.test.ts", 1, null));
        assertThat(result.findings()).extracting(f -> f.type() + " " + f.id())
                .containsExactly("UNBELEGT UC-XX-2");
    }

    // UC-DL-102
    @Test
    void overlappingRootsDeduplicateSelectedSources() throws IOException {
        writeDoc(1, 4);
        write("src/a.test.ts", "// UC-XX-1\n");
        write("build/gen/d.test.ts", "// UC-XX-4\n");

        var result = scan(".", "build", "build/gen", "build/gen");

        assertThat(result.testSourceFileCount()).isEqualTo(2);
        assertThat(result.testEvidence()).containsExactly(
                new TestEvidence("UC-XX-4", "build/gen/d.test.ts", 1, null),
                new TestEvidence("UC-XX-1", "src/a.test.ts", 1, null));
        assertThat(result.findings()).isEmpty();
    }

    // UC-DL-101
    @Test
    void foreignNameMatchingIsLiteralAndCaseSensitive() throws IOException {
        writeDoc(1, 2, 3, 4);
        write("node_modules-extra/x.test.ts", "// UC-XX-1\n");
        write("Node_modules/y.test.ts", "// UC-XX-2\n");
        write("module/build/z.test.ts", "// UC-XX-3\n");
        write("src/s.test.ts", "// UC-XX-4\n");

        var result = scan(".");

        assertThat(result.testSourceFileCount()).isEqualTo(3);
        assertThat(result.testEvidence()).containsExactly(
                new TestEvidence("UC-XX-2", "Node_modules/y.test.ts", 1, null),
                new TestEvidence("UC-XX-1", "node_modules-extra/x.test.ts", 1, null),
                new TestEvidence("UC-XX-4", "src/s.test.ts", 1, null));
        assertThat(result.findings()).extracting(f -> f.type() + " " + f.id())
                .containsExactly("UNBELEGT UC-XX-3");
    }

    private DocsLintResult scan(String... testRoots) throws IOException {
        return new DocsLinter().lintWithTests(rootDir, List.of("docs"), List.of(testRoots), null, ID_PATTERN);
    }

    private void writeDoc(int... ucs) throws IOException {
        var sb = new StringBuilder("---\nidPrefix: XX\n---\n### R-XX-1 Rule ❌\n");
        for (int uc : ucs) {
            sb.append("#### UC-XX-").append(uc).append(" Uc ❌\n");
        }
        write("docs/a.md", sb.toString());
    }

    private void write(String relative, String content) throws IOException {
        Path file = rootDir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
