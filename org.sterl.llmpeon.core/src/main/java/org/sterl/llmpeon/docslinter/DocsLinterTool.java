package org.sterl.llmpeon.docslinter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.sterl.llmpeon.shared.QualifiedPathValidator;
import org.sterl.llmpeon.tool.tools.AbstractTool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class DocsLinterTool extends AbstractTool {

    private static final String DEFAULT_DOC_ROOT = "docs";
    static final String DEFAULT_ID_PATTERN = "\\bUC-[A-Z]+-\\d+(?:-\\d+[a-z]?)*\\b";
    static final String ID_PATTERN_DESCRIPTION =
            "regex that FULL-matches UC ids (e.g. UC-PP-\\d+); full-match, not a prefix";

    private volatile Path workingDir;

    public DocsLinterTool(Path workingDir) {
        setWorkingDir(workingDir);
    }

    public DocsLinterTool(String workingDir) {
        setWorkingDir(workingDir);
    }

    @Override
    public boolean isEditTool() {
        return false;
    }

    public void setWorkingDir(Path workingDir) {
        this.workingDir = workingDir.toAbsolutePath().normalize();
    }

    public void setWorkingDir(String workingDir) {
        if (workingDir == null) return;
        setWorkingDir(Path.of(workingDir));
    }

    public Path getWorkingDir() {
        return workingDir;
    }

    @Tool("Lint opted-in Markdown docs from disk without reading tests.")
    public String lintDocs(
            @P(required = false, name = "root") String root,
            @P(required = false, name = "docRoots") List<String> docRoots,
            @P(required = false, name = "idPattern", description = ID_PATTERN_DESCRIPTION) String idPattern) {

        Path effectiveRoot = resolveRoot(root);
        if (docRoots == null || docRoots.isEmpty()) {
            docRoots = List.of(DEFAULT_DOC_ROOT);
        }
        Pattern pattern = compilePattern(idPattern);

        validateChildRoots(effectiveRoot, docRoots);

        DocsLinter linter = new DocsLinter();
        DocsLintReportRenderer renderer = new DocsLintReportRenderer();
        try {
            DocsLintResult result = linter.lint(effectiveRoot, docRoots, pattern);
            onTool(renderer.statusLine("lintDocs", result));
            return renderer.summary(result);
        } catch (IOException e) {
            throw new RuntimeException("Failed to lint docs: " + e.getMessage(), e);
        }
    }

    @Tool("Lint opted-in Markdown docs and test ID comments from disk.")
    public String lintDocsAndTests(
            @P(required = false, name = "root") String root,
            @P(required = false, name = "docRoots") List<String> docRoots,
            @P(required = false, name = "testRoots") List<String> testRoots,
            @P(required = false, name = "testGlobs") List<String> testGlobs,
            @P(required = false, name = "idPattern", description = ID_PATTERN_DESCRIPTION) String idPattern) {

        Path effectiveRoot = resolveRoot(root);
        if (docRoots == null || docRoots.isEmpty()) {
            docRoots = List.of(DEFAULT_DOC_ROOT);
        }
        Pattern pattern = compilePattern(idPattern);

        validateChildRoots(effectiveRoot, docRoots);
        if (testRoots != null) {
            validateChildRoots(effectiveRoot, testRoots);
        }

        DocsLinter linter = new DocsLinter();
        DocsLintReportRenderer renderer = new DocsLintReportRenderer();
        try {
            DocsLintResult result = linter.lintWithTests(
                    effectiveRoot, docRoots, testRoots, testGlobs, pattern);
            onTool(renderer.statusLine("lintDocsAndTests", result));
            return renderer.summary(result);
        } catch (IOException e) {
            throw new RuntimeException("Failed to lint docs and tests: " + e.getMessage(), e);
        }
    }

    // Delegation target for DocsIdTool — deliberately not exposed as a tool here.
    String nextIds(String root, List<String> docRoots, String prefix) {

        Path effectiveRoot = resolveRoot(root);
        if (docRoots == null || docRoots.isEmpty()) {
            docRoots = List.of(DEFAULT_DOC_ROOT);
        }
        Pattern pattern = Pattern.compile(DEFAULT_ID_PATTERN);
        validateChildRoots(effectiveRoot, docRoots);

        // Input normalization at the boundary; validation lives in DocsLinter.requireValidPrefix (R-DL-23).
        if (prefix != null) {
            prefix = prefix.trim();
        }

        DocsLinter linter = new DocsLinter();
        try {
            NextIdsResult result = linter.nextIds(effectiveRoot, docRoots, pattern, prefix);
            return formatNextIds(result);
        } catch (IOException e) {
            throw new RuntimeException("Failed to compute next IDs: " + e.getMessage(), e);
        }
    }

    // --- package-private helpers visible for tests ---

    private String formatNextIds(NextIdsResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(new DocsLintReportRenderer().docScanSummary(
                result.lintedDocCount(), result.skippedDocCount()));
        for (var r : result.nextIds()) {
            sb.append("\n");
            if (r.occupied()) {
                sb.append(r.prefix()).append(": occupied, next: R-").append(r.prefix())
                        .append("-").append(r.nextRule())
                        .append(", UC-").append(r.prefix())
                        .append("-").append(r.nextUseCase());
            } else {
                sb.append(r.prefix()).append(": free, would start: R-").append(r.prefix())
                        .append("-1, UC-").append(r.prefix()).append("-1");
            }
        }
        return sb.toString();
    }

    private void validateChildRoots(Path effectiveRoot, List<String> childRoots) {
        for (String cr : childRoots) {
            if (cr == null || cr.isBlank()) continue;
            Path resolved = effectiveRoot.resolve(cr).normalize();
            if (!resolved.startsWith(effectiveRoot)) {
                throw new IllegalArgumentException("Child root escapes root: " + cr);
            }
        }
    }

    /**
     * Single choke point for root resolution in {@code lintDocs}, {@code lintDocsAndTests} and
     * {@code nextIds} (R-DL-18): a given root that is a directory is used as-is; otherwise the
     * path without its leading {@code /} is resolved against the tool's workingDir (workspace-
     * qualified dialect, e.g. {@code /llmpeon-parent}); if both fail, the error names both tried
     * paths — a wrong root must never look like a clean 0/0 scan.
     */
    private Path resolveRoot(String root) {
        if (root == null || root.isBlank()) {
            if (!java.nio.file.Files.isDirectory(workingDir)) {
                throw new IllegalArgumentException("Root is not a directory: " + workingDir);
            }
            return workingDir;
        }
        QualifiedPathValidator.requireQualifiedDisk("lintDocs", root);
        Path given = Path.of(root).normalize();
        if (java.nio.file.Files.isDirectory(given)) {
            return given;
        }
        Path fallback = workingDir.resolve(root.substring(1)).normalize();
        if (java.nio.file.Files.isDirectory(fallback)) {
            return fallback;
        }
        throw new IllegalArgumentException(
                "Root is not a directory: " + given + " (also tried: " + fallback + ")");
    }

    static Pattern compilePattern(String idPattern) {
        if (idPattern == null || idPattern.isBlank()) {
            return Pattern.compile(DEFAULT_ID_PATTERN);
        }
        try {
            return Pattern.compile(idPattern);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid idPattern regex: " + idPattern + " — " + e.getMessage());
        }
    }
}
