package org.sterl.llmpeon.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.junit.After;
import org.junit.Test;
import org.sterl.llmpeon.parts.tools.EclipseBuildTool;

/**
 * eclipseReadProjectProblems file/severity filters (docs/project-problems-tool.md R-PP-1…4).
 */
public class EclipseBuildToolTest extends AbstractIntegrationTest {

    private static final String FILE_A = "src/org/sterl/fixture/Alpha.java";
    private static final String FILE_B = "src/org/sterl/fixture/Beta.java";
    private static final String FILE_C = "src/org/sterl/fixture/GrepTarget.java";
    private static final String FILE_CLEAN = "src/org/sterl/fixture/Alphabet.java";

    private final EclipseBuildTool tool = new EclipseBuildTool();
    private final List<IFile> markerFiles = new ArrayList<>();

    @After
    public void cleanupMarkers() throws Exception {
        // safety net; every fixture-creating test already cleans up in its own finally
        deleteTestMarkers();
        super.after();
    }

    private void deleteTestMarkers() {
        for (IFile file : markerFiles) {
            try {
                file.deleteMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO);
            } catch (CoreException e) {
                // cleanup must not mask the test outcome
            }
        }
        markerFiles.clear();
    }

    private static IFile file(String projectRelativePath) {
        return project.getFile(projectRelativePath);
    }

    private void addMarker(String projectRelativePath, int severity, int line, String message) throws Exception {
        IFile file = file(projectRelativePath);
        file.createMarker(IMarker.PROBLEM, Map.of(
                IMarker.MESSAGE, message,
                IMarker.SEVERITY, severity,
                IMarker.LINE_NUMBER, line));
        markerFiles.add(file);
    }

    // UC-PP-1
    @Test
    public void problemsForSingleFileReturnsOnlyThatFile() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        try {
            addMarker(FILE_A, IMarker.SEVERITY_ERROR, 5, "alpha problem");
            addMarker(FILE_B, IMarker.SEVERITY_WARNING, 9, "beta problem");

            String result = tool.eclipseReadProjectProblems(PeonTestFixture.PROJECT_NAME, FILE_A, null);

            // THEN only the markers of file A, header names A + project
            assertTrue("expected A in header:\n" + result,
                    result.startsWith("Problems in " + FILE_A + " (project " + PeonTestFixture.PROJECT_NAME + "):"));
            assertTrue("expected A marker:\n" + result, result.contains("alpha problem"));
            assertFalse("B marker must not leak into the filtered result:\n" + result,
                    result.contains("beta problem"));
        } finally {
            deleteTestMarkers();
        }
    }

    // UC-PP-2
    @Test
    public void filteredPathOutsideProjectFailsHonest() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        try {
            addMarker(FILE_A, IMarker.SEVERITY_ERROR, 5, "alpha problem");

            String result = tool.eclipseReadProjectProblems(
                    PeonTestFixture.PROJECT_NAME, "src/not/there.java", null);

            // THEN honest error naming the path + project, no project-wide fallback
            assertTrue("expected honest not-found message:\n" + result,
                    result.contains("No problems found for src/not/there.java in project "
                            + PeonTestFixture.PROJECT_NAME + " (project-relative path expected)"));
            assertFalse("existing markers must not leak as fallback:\n" + result,
                    result.contains("alpha problem"));
        } finally {
            deleteTestMarkers();
        }
    }

    // UC-PP-3
    @Test
    public void severityFilterReturnsOnlyErrors() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        try {
            addMarker(FILE_A, IMarker.SEVERITY_ERROR, 5, "alpha error");
            addMarker(FILE_A, IMarker.SEVERITY_WARNING, 6, "alpha warning 1");
            addMarker(FILE_A, IMarker.SEVERITY_WARNING, 7, "alpha warning 2");
            addMarker(FILE_A, IMarker.SEVERITY_WARNING, 8, "alpha warning 3");

            String result = tool.eclipseReadProjectProblems(PeonTestFixture.PROJECT_NAME, FILE_A, "ERROR");

            // THEN only the error marker, severity named in the header
            assertTrue("expected error marker:\n" + result, result.contains("alpha error"));
            assertFalse("warnings must be filtered out:\n" + result, result.contains("alpha warning"));
            assertTrue("severity must be named in the header:\n" + result,
                    result.contains("(project " + PeonTestFixture.PROJECT_NAME + ", severity ERROR):"));
        } finally {
            deleteTestMarkers();
        }
    }

    // UC-PP-4
    @Test
    public void cleanFileReportsNoProblemsHonest() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        try {
            addMarker(FILE_C, IMarker.SEVERITY_ERROR, 3, "grep problem");

            String result = tool.eclipseReadProjectProblems(PeonTestFixture.PROJECT_NAME, FILE_CLEAN, null);

            // THEN honest empty message with explicit scope, no project-wide fallback
            assertTrue("expected honest empty message with scope:\n" + result,
                    result.contains("No problems in " + FILE_CLEAN
                            + " (scope: project " + PeonTestFixture.PROJECT_NAME + ")"));
            assertFalse("markers of other files must not leak:\n" + result,
                    result.contains("grep problem"));
        } finally {
            deleteTestMarkers();
        }
    }

    // UC-PP-5
    @Test
    public void invalidSeverityFailsHonest() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        try {
            tool.eclipseReadProjectProblems(PeonTestFixture.PROJECT_NAME, null, "bogus");
            fail("expected IllegalArgumentException for invalid severity 'bogus'");
        } catch (IllegalArgumentException e) {
            // THEN honest error naming the offending value and the allowed values
            assertTrue("expected offending value in message:\n" + e.getMessage(),
                    e.getMessage().contains("bogus"));
            assertTrue("expected allowed values in message:\n" + e.getMessage(),
                    e.getMessage().contains("ERROR, WARNING"));
        }
    }

    // UC-PP-6
    @Test
    public void severityValueIsCaseInsensitive() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        try {
            addMarker(FILE_A, IMarker.SEVERITY_ERROR, 5, "alpha error");
            addMarker(FILE_A, IMarker.SEVERITY_WARNING, 6, "alpha warning");

            String result = tool.eclipseReadProjectProblems(PeonTestFixture.PROJECT_NAME, FILE_A, "error");

            // THEN lowercase "error" filters like "ERROR", header shows the canonical name
            assertTrue("expected error marker:\n" + result, result.contains("alpha error"));
            assertFalse("warning must be filtered out:\n" + result, result.contains("alpha warning"));
            assertTrue("expected canonical severity name in header:\n" + result,
                    result.contains(", severity ERROR):"));
        } finally {
            deleteTestMarkers();
        }
    }

    @Test
    public void defaultModeStillProjectWide() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        try {
            addMarker(FILE_A, IMarker.SEVERITY_ERROR, 5, "alpha problem");
            addMarker(FILE_B, IMarker.SEVERITY_WARNING, 9, "beta problem");

            String result = tool.eclipseReadProjectProblems(PeonTestFixture.PROJECT_NAME, null, null);

            // R-PP-4: no filters = today's project-wide behaviour, old default header
            assertTrue("expected old default header:\n" + result,
                    result.startsWith("Project " + PeonTestFixture.PROJECT_NAME + " problems:\n"));
            assertTrue("expected A marker:\n" + result, result.contains("alpha problem"));
            assertTrue("expected B marker:\n" + result, result.contains("beta problem"));
        } finally {
            deleteTestMarkers();
        }
    }

    // UC-PP-7
    @Test
    public void whitespaceOnlyFilesMeansUnset() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        try {
            addMarker(FILE_A, IMarker.SEVERITY_ERROR, 5, "alpha problem");
            addMarker(FILE_B, IMarker.SEVERITY_WARNING, 9, "beta problem");

            String result = tool.eclipseReadProjectProblems(PeonTestFixture.PROJECT_NAME, " ", null);

            // THEN whitespace-only files behaves exactly like unset (project-wide default)
            assertTrue("expected old default header:\n" + result,
                    result.startsWith("Project " + PeonTestFixture.PROJECT_NAME + " problems:\n"));
            assertTrue("expected A marker:\n" + result, result.contains("alpha problem"));
            assertTrue("expected B marker:\n" + result, result.contains("beta problem"));
        } finally {
            deleteTestMarkers();
        }
    }
}
