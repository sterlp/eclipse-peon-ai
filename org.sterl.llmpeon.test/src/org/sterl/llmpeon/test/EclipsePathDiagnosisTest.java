package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
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
 * R-WN-5 (org.sterl.llmpeon/docs/workspace-nesting.md): a failed {@code eclipse*} lookup names its
 * real cause — closed project, other view of the same disk file, disk-only file — instead of a bare
 * "not found", and {@code findOpenProject} matches names exactly, never as a substring.
 */
public class EclipsePathDiagnosisTest extends AbstractIntegrationTest {

    private static final String MARKER_FILE = NestedProjectFixture.MARKER_FILE;
    private static final String LEAF_NAME = NestedProjectFixture.LEAF_NAME;
    private static final String ROOT_NAME = NestedProjectFixture.ROOT_NAME;
    private static final String LEAF_VIEW = "/" + LEAF_NAME + "/" + MARKER_FILE;

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
        } finally {
            nested.destroy();
        }
    }

    // R-WN-5
    // UC-WN-5
    @Test
    public void closedProjectFailureNamesTheCause() throws Exception {
        // GIVEN - both module projects are closed, only the aggregator still covers the disk folder
        nested.leaf().close(new NullProgressMonitor());
        nested.mid().close(new NullProgressMonitor());
        try {
            // WHEN - the leaf view of the marker file is read
            String result = new EclipseWorkspaceReadFileTool().eclipseReadFile(LEAF_VIEW, 0, 0);

            // THEN - the cause is named, and the one view that is readable is offered as alternative
            assertTrue("expected the closed-project cause in:\n" + result,
                    result.contains("exists but is closed"));
            assertTrue("expected the closed project to be named in:\n" + result,
                    result.contains(LEAF_NAME));
            assertTrue("expected the readable aggregator view to be offered in:\n" + result,
                    result.contains("The same file is readable as: /" + ROOT_NAME + "/"
                            + NestedProjectFixture.MID_NAME + "/" + LEAF_NAME + "/" + MARKER_FILE));
        } finally {
            openAgain(nested.leaf());
            openAgain(nested.mid());
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
                EclipseUtil.findOpenProject("/" + ROOT_NAME + "/" + NestedProjectFixture.MID_NAME)
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
            String result = new EclipseWorkspaceReadFileTool()
                    .eclipseReadFile("/" + ROOT_NAME + "/WnDiskOnly.txt", 0, 0);

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
        nested.leaf().close(new NullProgressMonitor());
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

    private static void openAgain(IProject project) {
        try {
            if (project != null && project.exists() && !project.isOpen()) {
                project.open(new NullProgressMonitor());
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot reopen nested fixture project " + project.getName(), e);
        }
    }
}
