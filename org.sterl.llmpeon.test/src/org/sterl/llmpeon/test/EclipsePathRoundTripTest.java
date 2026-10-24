package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;

/**
 * R-WN-4 / R-WN-5 (org.sterl.llmpeon/docs/workspace-nesting.md): a path an {@code eclipse*} tool delivered
 * stays readable — also the aggregator folder form while the aggregator itself is closed, resolved through
 * its Disk location and disclosed as such — and a lookup that really cannot resolve names its cause instead
 * of a bare "not found".
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
