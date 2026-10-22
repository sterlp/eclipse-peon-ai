package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.context.UserContext;
import org.sterl.llmpeon.parts.shared.EclipseUtil;

/**
 * R-WN-3 (org.sterl.llmpeon/docs/workspace-nesting.md): the project info carries the nesting — at most one extra
 * line per direction, only for real nesting of open projects, compared via canonical folders.
 */
public class NestedProjectInfoTest extends AbstractIntegrationTest {

    private NestedProjectFixture nested;

    @Before
    public void createNestedFixture() {
        nested = new NestedProjectFixture();
        nested.create();
    }

    @After
    public void destroyNestedFixture() {
        if (nested != null) nested.destroy();
    }

    // R-WN-3
    @Test
    public void turnContextCarriesNestingLine() {
        // GIVEN the active project is the leaf of the nested chain
        var context = new UserContext();
        context.setCurrentProject(nested.leaf());

        // WHEN the turn context is built
        var info = projectInfoItem(context).render().body();

        // THEN the leaf's project info carries the exact nesting line (deepest ancestor)
        assertContains(info, "Nested in Eclipse project /" + NestedProjectFixture.MID_NAME
                + " — the same files are also visible under /" + NestedProjectFixture.MID_NAME + "/"
                + NestedProjectFixture.LEAF_NAME + "/…; tools return the most specific project path.");
    }

    // R-WN-3
    @Test
    public void projectInfoNamesDeepestAncestorForNestedProject() {
        // GIVEN the leaf project is nested under two open ancestors (wnb_mid and wna_root)
        // WHEN the project info is built
        var info = EclipseUtil.projectInfo(nested.leaf());

        // THEN exactly one "Nested in" line exists and names the deepest ancestor, not the root
        assertEquals(1, countOccurrences(info, "Nested in Eclipse project"));
        assertContains(info, "Nested in Eclipse project /" + NestedProjectFixture.MID_NAME);
        assertFalse(info, info.contains("Nested in Eclipse project /" + NestedProjectFixture.ROOT_NAME));
    }

    // R-WN-3
    @Test
    public void projectInfoNamesContainedProjectsForAggregator() {
        // GIVEN the root project contains two open projects (wnb_mid and wnc_leaf)
        // WHEN the project info is built
        var info = EclipseUtil.projectInfo(nested.root());

        // THEN one "Contains" line lists all contained projects, alphabetically sorted
        assertEquals(1, countOccurrences(info, "Contains Eclipse projects:"));
        assertContains(info, "Contains Eclipse projects: " + NestedProjectFixture.MID_NAME + ", "
                + NestedProjectFixture.LEAF_NAME
                + " — their files are also visible under this path; tools return the most specific project path.");
    }

    // R-WN-3
    @Test
    public void projectInfoListsBothDirectionsForMiddleProject() {
        // GIVEN the middle project is nested under the root and contains the leaf
        // WHEN the project info is built
        var info = EclipseUtil.projectInfo(nested.mid());

        // THEN both lines are present: nested in the root, contains the leaf
        assertContains(info, "Nested in Eclipse project /" + NestedProjectFixture.ROOT_NAME
                + " — the same files are also visible under /" + NestedProjectFixture.ROOT_NAME + "/"
                + NestedProjectFixture.MID_NAME + "/…; tools return the most specific project path.");
        assertContains(info, "Contains Eclipse projects: " + NestedProjectFixture.LEAF_NAME
                + " — their files are also visible under this path; tools return the most specific project path.");
    }

    // R-WN-3
    @Test
    public void projectInfoHasNoNestingLinesWithoutNesting() {
        // GIVEN the fixture project is not nested in or containing any other project
        // (regression guard: green before the fix, must stay green after)
        // WHEN the project info is built
        var info = EclipseUtil.projectInfo(project);

        // THEN no nesting line appears
        assertFalse(info, info.contains("Nested in Eclipse project"));
        assertFalse(info, info.contains("Contains Eclipse projects:"));
    }

    // R-WN-3
    @Test
    public void closedProjectsAreNotListed() throws Exception {
        // GIVEN the leaf project is closed again
        nested.leaf().close(new NullProgressMonitor());

        // WHEN the root's project info is built
        var info = EclipseUtil.projectInfo(nested.root());

        // THEN only the still open mid project is listed as contained
        assertContains(info, "Contains Eclipse projects: " + NestedProjectFixture.MID_NAME
                + " — their files are also visible under this path; tools return the most specific project path.");
        assertFalse(info, info.contains(NestedProjectFixture.LEAF_NAME));
    }

    private static ContextItem projectInfoItem(UserContext context) {
        var expectedLabel = "Project info " + NestedProjectFixture.LEAF_NAME;
        return context.get().stream()
                .filter(item -> expectedLabel.equals(item.label()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no project info item with label " + expectedLabel));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int from = 0; (from = haystack.indexOf(needle, from)) >= 0; from += needle.length()) count++;
        return count;
    }
}
