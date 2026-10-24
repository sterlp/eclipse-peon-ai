package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.tools.EclipseBuildTool;
import org.sterl.llmpeon.parts.tools.EclipseGrepTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;

/**
 * R-WN-4 / R-WN-5 (org.sterl.llmpeon/docs/workspace-nesting.md): a path an {@code eclipse*} tool delivered
 * stays readable — also the aggregator folder form while the aggregator itself is closed, resolved through
 * its Disk location and disclosed as such — and a lookup that really cannot resolve names its cause instead
 * of a bare "not found".
 * <p>
 * The promise covers every tool that consumes a path, so it is checked on read, open, list and grep — a
 * wiring that only exists on the read path would otherwise pass unnoticed (review finding F3).
 */
public class EclipsePathRoundTripTest extends AbstractIntegrationTest {

    private static final NullProgressMonitor MONITOR = new NullProgressMonitor();
    private static final String FILE = NestedProjectFixture.MARKER_FILE;
    private static final String MARKER = NestedProjectFixture.MARKER;
    private static final String ROOT_NAME = NestedProjectFixture.ROOT_NAME;
    private static final String MID_NAME = NestedProjectFixture.MID_NAME;
    private static final String LEAF_NAME = NestedProjectFixture.LEAF_NAME;
    /** The aggregator folder form: segments 2+ are folder names, not the project name. */
    private static final String ROOT_VIEW = "/" + ROOT_NAME + "/" + MID_NAME + "/" + LEAF_NAME + "/" + FILE;
    private static final String LEAF_VIEW = "/" + LEAF_NAME + "/" + FILE;
    /** The same two views of the folder itself — open and list are asked for a folder, not a file. */
    private static final String ROOT_FOLDER_VIEW = "/" + ROOT_NAME + "/" + MID_NAME + "/" + LEAF_NAME;
    private static final String MID_FOLDER_VIEW = "/" + MID_NAME + "/" + LEAF_NAME;

    private NestedProjectFixture nested;

    @Before
    public void createNestedFixture() {
        // isWorkspaceAvailable() opens every project — the close() calls below must come after it
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        nested = new NestedProjectFixture();
        nested.create();
    }

    @After
    public void destroyNestedFixture() {
        if (nested == null) return;
        try {
            openAgain(nested.leaf());
            openAgain(nested.mid());
            openAgain(nested.root());
        } finally {
            nested.destroy();
        }
    }

    // R-WN-4
    // UC-WN-4
    @Test
    public void aggregatorFolderViewStaysReadableWhileTheAggregatorIsClosed() throws Exception {
        // GIVEN - only the aggregator is closed: its folder form is no longer part of the workspace tree
        nested.root().close(MONITOR);
        try {
            // WHEN - that delivered form is read
            String result = read(ROOT_VIEW);

            // THEN - the same file is read through the Disk location, and the other view is disclosed
            assertTrue("expected the file content in:\n" + result, result.contains(MARKER));
            assertTrue("expected the resolved view to be disclosed in:\n" + result,
                    result.contains("resolved via /" + MID_NAME + "/" + LEAF_NAME + "/" + FILE));
        } finally {
            openAgain(nested.root());
        }
    }

    // R-WN-4
    // UC-WN-4
    @Test
    public void leafViewStaysReadableThroughTheOpenAggregator() throws Exception {
        // GIVEN - both module projects closed, only the aggregator still covers the Disk folder
        nested.leaf().close(MONITOR);
        nested.mid().close(MONITOR);
        try {
            // WHEN - the leaf view of the same file is read
            String result = read(LEAF_VIEW);

            // THEN - the deepest still-open view of that Disk file answers, disclosed
            assertTrue("expected the file content in:\n" + result, result.contains(MARKER));
            assertTrue("expected the resolved view to be disclosed in:\n" + result,
                    result.contains("resolved via " + ROOT_VIEW));
        } finally {
            openAgain(nested.leaf());
            openAgain(nested.mid());
        }
    }

    /**
     * Contract pin without a UC marker: green with and without the Disk last-resort, because search never
     * delivers the closed project's own view but an open ancestor's. It pins the R-WN-4 round-trip promise,
     * not the last-resort itself (skill test-honesty).
     */
    // R-WN-4
    @Test
    public void deliveredPathStaysReadableAfterTheLeafProjectIsClosed() throws Exception {
        // GIVEN - the leaf is closed and search delivers the path it delivers in that state
        nested.leaf().close(MONITOR);
        try {
            String delivered = firstDeliveredPath(
                    new EclipseWorkspaceReadFileTool().eclipseSearchFiles(FILE, null, null));

            // WHEN
            String result = read(delivered);

            // THEN
            assertTrue("expected the file content in:\n" + result, result.contains(MARKER));
            assertFalse("a path delivered one moment ago must not be reported missing:\n" + result,
                    result.contains("No eclipse file found"));
        } finally {
            openAgain(nested.leaf());
        }
    }

    /**
     * Contract pin without a UC marker: while every project is open the workspace tree already resolves the
     * aggregator folder form, so this is green without the last-resort (skill test-honesty). It pins that
     * an exact hit is never re-translated into another project's view.
     */
    // R-WN-4
    @Test
    public void aggregatorFolderViewIsExactWhileEveryProjectIsOpen() {
        String result = read(ROOT_VIEW);

        assertTrue("expected the file content in:\n" + result, result.contains(MARKER));
        assertFalse("an exact hit must not be resolved through another project:\n" + result,
                result.contains("resolved via"));
    }

    // R-WN-5
    // UC-WN-5
    @Test
    public void closedProjectFailureNamesTheCause() throws Exception {
        // GIVEN - no open project covers the Disk folder any more, so the last-resort cannot resolve either
        nested.leaf().close(MONITOR);
        nested.mid().close(MONITOR);
        nested.root().close(MONITOR);
        try {
            // WHEN
            String result = read(LEAF_VIEW);

            // THEN - the closed project is the named cause; refresh is not, because the project is closed
            assertTrue("expected the closed-project cause in:\n" + result,
                    result.contains("exists but is closed"));
            assertTrue("expected the closed project to be named in:\n" + result, result.contains(LEAF_NAME));
            assertFalse("a closed project must not be blamed on a missing refresh:\n" + result,
                    result.contains("eclipseRefreshProject"));
        } finally {
            openAgain(nested.leaf());
            openAgain(nested.mid());
            openAgain(nested.root());
        }
    }

    // R-WN-5
    // UC-WN-5
    @Test
    public void findOpenProjectMatchesNameExactlyOnly() {
        assertEquals("a prefix of a project name must not match",
                "<empty>", EclipseUtil.findOpenProject("wn").map(IProject::getName).orElse("<empty>"));
        assertEquals(ROOT_NAME,
                EclipseUtil.findOpenProject(ROOT_NAME).map(IProject::getName).orElse("<empty>"));
        assertEquals(ROOT_NAME,
                EclipseUtil.findOpenProject("/" + ROOT_NAME).map(IProject::getName).orElse("<empty>"));
        assertEquals("the first segment selects the project, deeper segments must not",
                ROOT_NAME,
                EclipseUtil.findOpenProject("/" + ROOT_NAME + "/" + MID_NAME)
                        .map(IProject::getName).orElse("<empty>"));
    }

    // R-WN-5
    // UC-WN-5
    @Test
    public void diskOnlyFileIsNamedAsRefreshCandidate() throws Exception {
        // GIVEN - a file on disk under the open aggregator that the workspace tree does not know yet
        var diskFile = nested.rootDir().resolve("WnDiskOnly.txt");
        Files.writeString(diskFile, "wnDiskOnly", StandardCharsets.UTF_8);
        try {
            // WHEN
            String result = read("/" + ROOT_NAME + "/WnDiskOnly.txt");

            // THEN - the refresh candidate is named, not a bare "not found"
            assertTrue("expected the disk-only cause in:\n" + result, result.contains("exists on disk"));
            assertTrue("expected the refresh hint in:\n" + result, result.contains("eclipseRefreshProject"));
        } finally {
            Files.deleteIfExists(diskFile);
        }
    }

    // R-WN-5
    // UC-WN-5
    @Test
    public void projectToolsNameClosedProjectInsteadOfMissingOne() throws Exception {
        // GIVEN - a closed project the agent addresses by name
        nested.leaf().close(MONITOR);
        try {
            // WHEN
            String result = new EclipseBuildTool().eclipseBuildProject(LEAF_NAME);

            // THEN - "closed" is reported, not "not found"
            assertTrue("expected the closed-project cause in:\n" + result,
                    result.contains("exists but is closed"));
        } finally {
            openAgain(nested.leaf());
        }
    }

    // R-WN-4
    @Test
    public void openingTheAggregatorViewDisclosesTheResolvedView() throws Exception {
        SwtUi.assumeUiAvailable();
        // GIVEN - the aggregator is closed: its folder form is not part of the workspace tree
        nested.root().close(MONITOR);
        try {
            // WHEN - the editor tool is asked for that view. The editor itself opens asynchronously
            // (asyncExec) and the PDE suite runs on the UI thread, so what is asserted is the tool's
            // own contract: the line it answers with.
            String result = new EclipseWorkspaceReadFileTool().eclipseOpenFileInEditor(ROOT_VIEW);

            // THEN - it opened something, and names the view the path resolved to
            assertTrue("expected a successful open in:\n" + result, result.startsWith("Opened: " + ROOT_VIEW));
            assertTrue("expected the resolved view to be disclosed in:\n" + result,
                    result.contains("resolved via /" + MID_NAME + "/" + LEAF_NAME + "/" + FILE));
        } finally {
            openAgain(nested.root());
        }
    }

    // R-WN-4
    @Test
    public void listingTheAggregatorViewDisclosesTheResolvedView() throws Exception {
        // GIVEN - the aggregator is closed, its folder form is asked for
        nested.root().close(MONITOR);
        try {
            // WHEN
            String result = new EclipseWorkspaceReadFileTool().eclipseList(ROOT_FOLDER_VIEW);

            // THEN - the listing is of the resolved view, and that is named on top of it
            assertTrue("expected the resolved view to be disclosed in:\n" + result,
                    result.contains("resolved via " + MID_FOLDER_VIEW));
            assertTrue("expected the marker file of the resolved view in:\n" + result,
                    result.contains("[FILE] " + MID_FOLDER_VIEW + "/" + FILE));
        } finally {
            openAgain(nested.root());
        }
    }

    // R-WN-5
    @Test
    public void grepOnADiskOnlyPathNamesTheRefreshCandidate() throws Exception {
        // GIVEN - a file on disk under the open aggregator that the workspace tree does not know
        var diskFile = nested.rootDir().resolve("WnDiskOnlyGrep.txt");
        Files.writeString(diskFile, MARKER, StandardCharsets.UTF_8);
        try {
            // WHEN - grep is scoped to exactly that path
            var failure = assertThrows(IllegalArgumentException.class, () -> new EclipseGrepTool()
                    .eclipseGrepFiles(MARKER, "/" + ROOT_NAME + "/WnDiskOnlyGrep.txt", null));

            // THEN - the leading line stays, and the refresh candidate is named with it
            assertTrue("expected the scope failure in:\n" + failure.getMessage(),
                    failure.getMessage().contains("Path not found"));
            assertTrue("expected the disk-only cause in:\n" + failure.getMessage(),
                    failure.getMessage().contains("exists on disk"));
            assertTrue("expected the refresh hint in:\n" + failure.getMessage(),
                    failure.getMessage().contains("eclipseRefreshProject"));
        } finally {
            Files.deleteIfExists(diskFile);
        }
    }

    // R-WN-5
    @Test
    public void grepOnAProjectRelativeScopeNeverNamesAProject() {
        // GIVEN - a project-relative scope: its first segment is a folder of the scoped project
        var grepper = new EclipseGrepTool();
        grepper.setCurrentProject(nested.root());

        // WHEN - that folder does not exist
        var failure = assertThrows(IllegalArgumentException.class,
                () -> grepper.eclipseGrepFiles(MARKER, "src/" + LEAF_NAME + "-missing", null));

        // THEN - the diagnosis stays with the path instead of blaming a project nobody named
        assertTrue("expected the generic path diagnosis in:\n" + failure.getMessage(),
                failure.getMessage().contains("No resource at that path"));
        assertFalse("a project-relative first segment is a folder, not a project name:\n" + failure.getMessage(),
                failure.getMessage().contains("No project named"));
    }

    // R-WN-5
    @Test
    public void anUnknownProjectNameIsAnsweredWithTheOpenProjects() {
        // WHEN - a project name that exists nowhere
        String result = new EclipseBuildTool().eclipseBuildProject("wnNoSuchProject");

        // THEN - not a bare not-found: the name is declared unknown and the open projects answer
        assertTrue("expected the unknown-project cause in:\n" + result,
                result.contains("No project named 'wnNoSuchProject'"));
        assertTrue("expected the open projects to be listed in:\n" + result,
                result.contains("Open projects:"));
        assertTrue("expected the open fixture project among them in:\n" + result,
                result.contains(ROOT_NAME));
    }

    private static String read(String path) {
        return new EclipseWorkspaceReadFileTool().eclipseReadFile(path, 0, 0);
    }

    private static String firstDeliveredPath(String searchResult) {
        for (var line : searchResult.split("\\R")) {
            var atFile = line.indexOf(FILE);
            if (line.startsWith("/") && atFile >= 0)
                return line.substring(0, atFile + FILE.length());
        }
        throw new IllegalStateException("search delivered no path:\n" + searchResult);
    }

    private static void openAgain(IProject project) {
        try {
            if (project != null && project.exists() && !project.isOpen()) {
                project.open(MONITOR);
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot reopen nested fixture project " + project.getName(), e);
        }
    }
}
