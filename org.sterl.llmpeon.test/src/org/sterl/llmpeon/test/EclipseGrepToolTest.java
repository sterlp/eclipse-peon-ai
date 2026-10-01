package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.parts.tools.EclipseGrepTool;

public class EclipseGrepToolTest extends AbstractIntegrationTest {

    private static final String OTHER_PROJECT_NAME = "aaa_other";
    private static final String EXTERNALLY_WRITTEN_FILE = "ExternallyGrepped.java";

    private final EclipseGrepTool tool = new EclipseGrepTool();
    private IProject otherProject;
    private Path externallyWrittenFile;

    @Before
    public void createOtherProject() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        otherProject = ResourcesPlugin.getWorkspace().getRoot().getProject(OTHER_PROJECT_NAME);
        if (otherProject.exists()) otherProject.delete(true, true, new NullProgressMonitor());
        otherProject.create(new NullProgressMonitor());
        otherProject.open(new NullProgressMonitor());
        otherProject.getFile("Other.java").create(new ByteArrayInputStream(
                "public class Other {}".getBytes(StandardCharsets.UTF_8)), true,
                new NullProgressMonitor());
    }

    @Override
    @After
    public void after() {
        try {
            var monitor = new NullProgressMonitor();
            if (otherProject != null && otherProject.exists()) {
                otherProject.delete(true, true, monitor);
            }
            var externalResource = project.getFile(EXTERNALLY_WRITTEN_FILE);
            if (externalResource.exists()) {
                externalResource.delete(true, monitor);
            } else if (externallyWrittenFile != null) {
                Files.deleteIfExists(externallyWrittenFile);
                project.refreshLocal(IProject.DEPTH_INFINITE, monitor);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            super.after();
        }
    }

    @Test
    public void invalidRegexFallsBackToLiteral() {
        String result = tool.eclipseGrepFiles("foo(bar", PeonTestFixture.PROJECT_NAME, ".java");

        assertContains(result, "MetaChars.java:6:     // foo(bar unbalanced");
        assertContains(result, "literal search — query is not a valid regex");
    }

    @Test
    public void grepListsMatchLinesWithLineNumbers() {
        // R8 — fixture MetaChars.java line 4 holds "// C++ marker" (numbering from the fixture layout)
        String result = tool.eclipseGrepFiles("C\\+\\+ marker", PeonTestFixture.PROJECT_NAME, ".java");

        assertContains(result, "MetaChars.java:4:     // C++ marker");
        assertFalse("under the line cap there must be no disclosure:\n" + result,
                result.contains("matched lines — narrow your search"));
    }

    @Test
    public void noMatchesReportsEmpty() {
        String result = tool.eclipseGrepFiles("zzz-does-not-exist", PeonTestFixture.PROJECT_NAME, ".java");

        assertContains(result, "no matches");
        assertContains(result, "regex search");
        assertContains(result, "Searched: " + PeonTestFixture.PROJECT_NAME);
        assertContains(result, "pattern: zzz-does-not-exist");
        assertFalse(result.contains("File type filter: known text extensions and filenames only."));
    }

    @Test
    public void emptyMdGrepPayloadStaysSmallAndHonest() {
        // GIVEN the fixture's .md files (README.md, data/notes.md) do not contain "einem text"
        // WHEN grepping "einem text" with the .md type filter (Paul's 0-hit case)
        String result = tool.eclipseGrepFiles("einem text", PeonTestFixture.PROJECT_NAME, ".md");

        // THEN the 0-hit payload is honest: R2d "no matches" + R2c mode + searched scope + pattern
        assertContains(result, "no matches");
        assertContains(result, "regex search"); // "einem text" is a valid regex -> regex mode
        assertContains(result, "Searched: " + PeonTestFixture.PROJECT_NAME);
        assertContains(result, "pattern: einem text");
        // ... and no file content / bulk data leaks into the empty payload ...
        assertFalse("empty payload must not leak README.md content:\n" + result,
                result.contains("Test fixture"));
        assertFalse("empty payload must not leak notes.md content:\n" + result,
                result.contains("Fixture notes"));
        // ... and the FULL captured payload stays far below any token-overflow threshold.
        assertTrue("empty grep payload must stay < 2KB, was " + result.length() + " chars:\n" + result,
                result.length() < 2048);
    }

    @Test(timeout = 60_000)
    public void manyMdMatchesAreCappedAndDisclosed() {
        // GIVEN 110 .md files, each with 5 lines containing "einem text" (550 potential matches —
        // well past MAX_GREP_FILES=100 and MAX_GREP_LINES=100, so both caps must engage)
        for (int i = 0; i < 110; i++) {
            eclipseWriteFile(String.format("grepmany_%03d.md", i),
                    "# fixture note\n"
                    + "einem text one\n"
                    + "einem text two\n"
                    + "einem text three\n"
                    + "einem text four\n"
                    + "einem text five\n");
        }

        // WHEN grepping "einem text" with the .md type filter
        String result = tool.eclipseGrepFiles("einem text", PeonTestFixture.PROJECT_NAME, ".md");

        // THEN the line cap renders exactly MAX_GREP_LINES(100) hit lines ...
        var hitLines = result.lines()
                .filter(l -> l.matches(".*\\.md:\\d+: .*"))
                .toList();
        assertEquals("line cap must render exactly 100 hit lines:\n" + result, 100, hitLines.size());
        // ... the FULL captured payload stays far below any token-overflow threshold
        // (a leaked line cap would render ~5x more lines and blow past this bound)
        assertTrue("many-match payload must stay < 15KB, was " + result.length() + " chars",
                result.length() < 15000);
        // ... every truncation is honestly disclosed: showing N of M + the file cap + the mode
        assertContains(result, "showing 100 of 500 matched lines — narrow your search");
        assertContains(result, "... result capped at 100 files. Narrow your search path.");
        assertContains(result, "regex search");
        // ... and every shown hit is a real .md match on the query, not garbage
        hitLines.forEach(l -> assertTrue("hit must contain the query:\n" + l, l.contains("einem text")));
    }

    @Test
    public void longLineIsClampedAndDisclosed() {
        // GIVEN one .md file with a single 20013-char line containing the query (minified)
        eclipseWriteFile("grepminified.md", "minifiedline " + "a".repeat(20_000) + "\n");

        // WHEN grepping the query with the .md type filter
        String result = tool.eclipseGrepFiles("minifiedline", PeonTestFixture.PROJECT_NAME, ".md");

        // THEN the hit line is clamped to exactly 2000 chars of text (13-char token + 1987 'a', nothing more)
        var hitLines = result.lines().filter(l -> l.contains("grepminified.md:1: ")).toList();
        assertEquals("exactly one hit line expected:\n" + result, 1, hitLines.size());
        assertTrue("hit line must end with exactly the clamped 2000 chars:\n" + hitLines.get(0),
                hitLines.get(0).matches(".*grepminified\\.md:1: minifiedline a{1987}$"));
        // ... and the clamp is honestly disclosed (family consistency with the disk tool)
        assertContains(result, "1 line(s) truncated at 2000 chars");
    }

    @Test
    public void validRegexReportsRegexMode() {
        String result = tool.eclipseGrepFiles("C++", PeonTestFixture.PROJECT_NAME, ".java");

        assertContains(result, "MetaChars.java");
        assertContains(result, "regex search");
    }

    @Test
    public void selectedProjectComesFirst() {
        tool.setCurrentProject(project);

        List<String> results = Arrays.stream(tool.eclipseGrepFiles("class", null, ".java").split("\\n"))
                .filter(line -> !line.isBlank())
                .toList();
        int lastSelected = lastIndexContaining(results, "/" + PeonTestFixture.PROJECT_NAME + "/");
        int firstOther = firstIndexContaining(results, "/" + OTHER_PROJECT_NAME + "/");

        assertTrue("Expected selected Fixture hits: " + results, lastSelected >= 0);
        assertTrue("Expected foreign project hit: " + results, firstOther >= 0);
        assertTrue("Expected every selected Fixture hit before foreign hits: " + results,
                lastSelected < firstOther);
    }

    @Test(timeout = 30_000)
    public void findsFileWrittenOutsideEclipse() throws Exception {
        externallyWrittenFile = project.getLocation().toFile().toPath().resolve(EXTERNALLY_WRITTEN_FILE);
        Files.write(externallyWrittenFile,
                "public class ExternalGrepToken {}".getBytes(StandardCharsets.UTF_8));

        String result = tool.eclipseGrepFiles(
                "ExternalGrepToken", PeonTestFixture.PROJECT_NAME, ".java");

        assertContains(result, "/" + PeonTestFixture.PROJECT_NAME + "/" + EXTERNALLY_WRITTEN_FILE);
    }

    @Test
    public void noRefreshWhenResultsFound() {
        var refreshCalls = new int[1];
        var countingTool = new EclipseGrepTool() {
            @Override
            protected void refreshScope(List<IContainer> scope) {
                refreshCalls[0]++;
            }
        };

        String result = countingTool.eclipseGrepFiles(
                "grepMe", PeonTestFixture.PROJECT_NAME, ".java");

        assertContains(result, "GrepTarget.java");
        assertEquals("Refresh must not run when the first search finds results", 0, refreshCalls[0]);
    }

    @Test
    public void reportsExtensionFilter() {
        String result = tool.eclipseGrepFiles("dockerGrepMe", PeonTestFixture.PROJECT_NAME, null);

        assertContains(result, "Dockerfile");
        assertFalse(result.contains("notes.peonx"));
    }

    @Test
    public void refreshesOnlySelectedProject() {
        var refreshCalls = new int[1];
        var refreshed = new ArrayList<IContainer>();
        var countingTool = new EclipseGrepTool() {
            @Override
            protected void refreshScope(List<IContainer> scope) {
                refreshCalls[0]++;
                refreshed.addAll(scope);
            }
        };
        countingTool.setCurrentProject(project);

        countingTool.eclipseGrepFiles("missing-" + System.nanoTime(), null, ".java");

        assertEquals("Expected exactly one refresh call", 1, refreshCalls[0]);
        assertEquals("Expected only the selected project as Refresh-Ziel", List.of(project), refreshed);
    }

    @Test
    public void refreshesExplicitScope() {
        var refreshCalls = new int[1];
        var refreshed = new ArrayList<IContainer>();
        var countingTool = new EclipseGrepTool() {
            @Override
            protected void refreshScope(List<IContainer> scope) {
                refreshCalls[0]++;
                refreshed.addAll(scope);
            }
        };

        countingTool.eclipseGrepFiles("zzz-does-not-exist", PeonTestFixture.PROJECT_NAME, ".java");

        assertEquals("Expected exactly one refresh call", 1, refreshCalls[0]);
        assertEquals("Expected only the explicit scope as Refresh-Ziel", List.of(project), refreshed);
    }

    @Test
    public void noRefreshWithoutSelectedProject() {
        var refreshCalls = new int[1];
        var countingTool = new EclipseGrepTool() {
            @Override
            protected void refreshScope(List<IContainer> scope) {
                refreshCalls[0]++;
            }
        };

        countingTool.eclipseGrepFiles("missing-" + System.nanoTime(), null, ".java");

        assertEquals("Refresh must not run without a selected or explicit scope", 0, refreshCalls[0]);
    }


    @Test
    public void namesTypeFilterOnEmptyResult() {
        String result = tool.eclipseGrepFiles("peonx-only-token", PeonTestFixture.PROJECT_NAME, null);

        assertContains(result, "no matches");
        assertContains(result, "File type filter: known text extensions and filenames only.");
    }

    private static int firstIndexContaining(List<String> lines, String value) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(value)) return i;
        }
        return -1;
    }

    private static int lastIndexContaining(List<String> lines, String value) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).contains(value)) return i;
        }
        return -1;
    }
}
