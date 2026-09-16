package org.sterl.llmpeon.docslinter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.model.chat.request.json.JsonArraySchema;

class DocsLinterToolTest {

    @TempDir
    Path rootDir;

    private Path docsDir;
    private DocsLinterTool tool;

    @BeforeEach
    void setUp() throws IOException {
        docsDir = rootDir.resolve("docs");
        Files.createDirectories(docsDir);
        tool = new DocsLinterTool(rootDir);
    }

    // --- UC-DL-4: disk-read disclosure in every summary ---
    // UC-DL-4
    @Test
    void returnsDiskReadDisclosureForBothLintMethods() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-1 Covered UC ✅
                """);

        String docsOnly = tool.lintDocs(null, List.of("docs"), null);
        assertThat(docsOnly).startsWith("Disk read — unsaved editor changes are not included.");

        String docsAndTests = tool.lintDocsAndTests(null, List.of("docs"), null, null, null);
        assertThat(docsAndTests).startsWith("Disk read — unsaved editor changes are not included.");
    }

    // --- UC-DL-45: every finding is returned without truncation ---
    // UC-DL-45
    @Test
    void returnsEveryFindingWithoutTruncation() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-1 Covered UC ✅
                ## UC-DL-2 Uncovered Done ✅
                """);
        Files.createDirectories(rootDir.resolve("src/test/java"));
        Files.writeString(rootDir.resolve("src/test/java/Test.java"), """
                // UC-DL-1
                // UC-DL-99
                void testIt() {}
                """);

        String output = tool.lintDocsAndTests(null, List.of("docs"), List.of("src/test/java"),
                null, null);

        assertThat(output).contains("UNBELEGT_ERLEDIGT UC-DL-2 docs/a.md:8");
        assertThat(output).contains("VERWAIST UC-DL-99 src/test/java/Test.java:2");
    }

    // --- UC-DL-46: lint specs must expose only the expected read parameters ---
    // UC-DL-46
    @Test
    void exposesOnlyExpectedReadParameters() {
        var toolService = new ToolService(false);
        toolService.addTool(tool);

        var lintDocsSpec = toolService.toolSpecifications().stream()
                .filter(s -> "lintDocs".equals(s.name()))
                .findFirst()
                .orElseThrow();
        assertThat(lintDocsSpec.parameters().properties().keySet())
                .containsExactlyInAnyOrder("root", "docRoots", "idPattern");

        var lintDocsAndTestsSpec = toolService.toolSpecifications().stream()
                .filter(s -> "lintDocsAndTests".equals(s.name()))
                .findFirst()
                .orElseThrow();
        assertThat(lintDocsAndTestsSpec.parameters().properties().keySet())
                .containsExactlyInAnyOrder("root", "docRoots", "testRoots", "testGlobs", "idPattern");
    }

    // --- UC-DL-47: lint methods do not touch the fixture tree ---
    // UC-DL-47
    @Test
    void lintMethodsDoNotCreateModifyOrDeleteFiles() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-1 Covered UC ✅
                ## UC-DL-2 Uncovered Done ✅
                """);
        Files.createDirectories(rootDir.resolve("src/test/java"));
        Files.writeString(rootDir.resolve("src/test/java/Test.java"), "// UC-DL-1\nvoid testIt() {}");
        Files.writeString(rootDir.resolve("side-file.txt"), "untouched");

        var before = snapshotTree(rootDir);

        tool.lintDocs(null, List.of("docs"), null);
        assertThat(snapshotTree(rootDir)).isEqualTo(before);

        tool.lintDocsAndTests(null, List.of("docs"), List.of("src/test/java"),
                null, null);
        assertThat(snapshotTree(rootDir)).isEqualTo(before);
    }

    private java.util.Map<String, String> snapshotTree(Path dir) throws IOException {
        var result = new java.util.TreeMap<String, String>();
        try (var walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                try {
                    result.put(dir.relativize(p).toString().replace('\\', '/'), Files.readString(p));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return result;
    }

    // --- UC-DL-29: full summary, no write ---
    @Test
    void returnsCompleteSummary() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-1 Covered UC ✅
                """);

        String output = tool.lintDocs(null, List.of("docs"), null);

        assertThat(output).contains("1 doc file(s): 1 linted, 0 not participating");
        assertThat(output).contains("UC definitions: 1 / 1");
        assertThat(output).contains("findings: 0");
        assertThat(output).doesNotContain("Report written");
    }

    // --- UC-DL-31: findings never throw or modify sources ---
    @Test
    void findingsNeverThrowOrModifySources() throws IOException {
        writeDoc("bad.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-12 Uncovered Done ✅
                """);

        // Snapshot source content before
        String originalContent = Files.readString(docsDir.resolve("bad.md"));

        String output = tool.lintDocsAndTests(null, List.of("docs"), null,
                null, null);

        // Must return normally
        assertThat(output).contains("UNBELEGT_ERLEDIGT");
        assertThat(output).contains("UC-DL-12");

        // Source files must be unchanged
        assertThat(Files.readString(docsDir.resolve("bad.md"))).isEqualTo(originalContent);
    }

    // --- root/scope regression tests ---
    @Test
    void usesWorkingDirectoryWhenRootIsAbsent() throws IOException {
        writeDoc("a.md", """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done
                """);

        String output = tool.lintDocs(null, List.of("docs"), null);
        assertThat(output).contains("1 doc file(s): 1 linted, 0 not participating");
    }

    @Test
    void acceptsAbsoluteExplicitRootInsteadOfWorkingDirectory() throws IOException {
        String absRoot = rootDir.toAbsolutePath().toString();
        DocsLinterTool altTool = new DocsLinterTool(rootDir.resolve("unused"));

        String output = altTool.lintDocs(absRoot, List.of("docs"), null);
        assertThat(output).contains("0 doc file(s): 0 linted, 0 not participating");
    }

    @Test
    void rejectsRelativeExplicitRoot() {
        assertThatThrownBy(() ->
                tool.lintDocs("not-absolute", List.of("docs"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fully qualified");
    }

    @Test
    void rejectsMissingConfiguredRoot() {
        assertThatThrownBy(() ->
                tool.lintDocs(rootDir.resolve("nonexistent").toString(), List.of("docs"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a directory");
    }

    // --- UC-DL-60: workspace-qualified root resolves against workingDir ---
    // UC-DL-60
    @Test
    void workspaceQualifiedRootResolvesAgainstWorkingDirectory() throws IOException {
        // GIVEN the root is workspace-qualified — not a directory on disk,
        // but the name exists relative to the tool's workingDir
        assertThat(Files.isDirectory(Path.of("/llmpeon-parent"))).as("fixture precondition").isFalse();
        Path nested = rootDir.resolve("llmpeon-parent");
        Files.createDirectories(nested.resolve("docs"));
        Files.writeString(nested.resolve("docs/a.md"), """
                ---
                idPrefix: DL
                ---

                # R-DL-1 Rule ✅ done

                ## UC-DL-1 Covered UC ✅
                """);

        // WHEN any of the three methods runs with the workspace-qualified root
        String docsOnly = tool.lintDocs("/llmpeon-parent", List.of("docs"), null);
        String docsAndTests = tool.lintDocsAndTests("/llmpeon-parent", List.of("docs"), null, null, null);
        String next = tool.nextIds("/llmpeon-parent", List.of("docs"), null);

        // THEN the workingDir-relative directory is found — real numbers, no 0/0
        assertThat(docsOnly).contains("1 doc file(s): 1 linted, 0 not participating");
        assertThat(docsOnly).contains("UC definitions: 1 / 1");
        assertThat(docsAndTests).contains("1 doc file(s): 1 linted, 0 not participating");
        assertThat(next).contains("1 doc file(s): 1 linted, 0 not participating");
    }

    // --- UC-DL-61: unresolvable root names both tried paths ---
    // UC-DL-61
    @Test
    void unresolvableRootNamesBothTriedPaths() {
        // GIVEN the root exists neither as a directory nor relative to workingDir
        var missing = "/no-such-peon-root";
        assertThat(Files.isDirectory(Path.of(missing))).as("fixture precondition").isFalse();
        assertThat(Files.isDirectory(rootDir.resolve("no-such-peon-root"))).as("fixture precondition").isFalse();
        var expected = "Root is not a directory: " + missing
                + " (also tried: " + rootDir.resolve("no-such-peon-root") + ")";

        // WHEN any of the three methods runs — THEN the error names both tried paths
        assertThatThrownBy(() -> tool.lintDocs(missing, List.of("docs"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expected);
        assertThatThrownBy(() -> tool.lintDocsAndTests(missing, List.of("docs"), null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expected);
        assertThatThrownBy(() -> tool.nextIds(missing, List.of("docs"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expected);
    }

    @Test
    void rejectsInvalidIdPattern() {
        assertThatThrownBy(() ->
                tool.lintDocs(null, List.of("docs"), "[invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid idPattern");
    }

    @Test
    void rejectsAbsoluteOrEscapingChildRoots() {
        assertThatThrownBy(() ->
                tool.lintDocs(null, List.of("/absolute/path"), null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                tool.lintDocs(null, List.of("../escape"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Public API: testGlob removed, testGlobs is array ---
    @Test
    void exposesTestGlobsAsArrayAndRemovesSingularTestGlob() {
        var tool = new DocsLinterTool(rootDir);
        var toolService = new ToolService(false);
        toolService.addTool(tool);

        var spec = toolService.toolSpecifications().stream()
                .filter(s -> "lintDocsAndTests".equals(s.name()))
                .findFirst()
                .orElseThrow();

        var testGlobs = spec.parameters().properties().get("testGlobs");
        assertThat(testGlobs).isInstanceOf(JsonArraySchema.class);

        assertThat(spec.parameters().properties()).doesNotContainKey("testGlob");
    }

    private void writeDoc(String name, String content) throws IOException {
        Files.writeString(docsDir.resolve(name), content);
    }
}
