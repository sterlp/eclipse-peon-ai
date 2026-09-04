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
import java.util.HashSet;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;

public class EclipseSearchFilesToolTest extends AbstractIntegrationTest {

    private static final String OTHER_PROJECT_NAME = "aaa_other";
    private static final String EXTERNALLY_WRITTEN_FILE = "ExternallyWritten.java";
    private static final String NON_DERIVED_TARGET_FILE = "target/AlphaGenerated.java";

    private IProject otherProject;
    private Path externallyWrittenFile;
    private Boolean targetWasDerived;

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
            if (otherProject != null && otherProject.exists()) {
                otherProject.delete(true, true, new NullProgressMonitor());
            }
        } catch (CoreException e) {
            throw new IllegalStateException(e);
        } finally {
            try {
                var monitor = new NullProgressMonitor();
                var generatedResource = project.getFile(NON_DERIVED_TARGET_FILE);
                if (generatedResource.exists()) generatedResource.delete(true, monitor);
                if (targetWasDerived != null) {
                    project.getFolder("target").setDerived(targetWasDerived, monitor);
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
            }
            super.after();
        }
    }

    @Test
    public void searchAndReadFixture() throws Exception {
        var tool = new EclipseWorkspaceReadFileTool();

        String searchResult = tool.eclipseSearchFiles(
                "Alpha.java", PeonTestFixture.PROJECT_NAME, 0);
        assertTrue("Expected to find Alpha.java in Fixture: " + searchResult,
                searchResult.contains("Alpha.java"));

        String content = tool.eclipseReadFile(searchResult.split("\n")[0], 0, 0);
        assertTrue("Expected to read Fixture source, got: " + content,
                content.contains("public class Alpha"));
    }

    @Test
    public void searchWorkspaceFiles_limitRestrictsResults() {
        var tool = new EclipseWorkspaceReadFileTool();

        String all = tool.eclipseSearchFiles("*.java", PeonTestFixture.PROJECT_NAME, 0);
        int allCount = all.split("\n").length;
        assertTrue("Expected more than 1 Fixture .java file", allCount > 1);

        String limited = tool.eclipseSearchFiles("*.java", PeonTestFixture.PROJECT_NAME, 1);
        assertEquals("Expected exactly 1 result with limit=1", 1, limited.split("\n").length);
    }

    @Test
    public void wildcardVariantsFindSameFile() {
        var tool = new EclipseWorkspaceReadFileTool();

        String prefixWildcard = tool.eclipseSearchFiles(
                "Alpha*", PeonTestFixture.PROJECT_NAME, 0);
        String suffixWildcard = tool.eclipseSearchFiles(
                "*lpha.java", PeonTestFixture.PROJECT_NAME, 0);

        assertContains(prefixWildcard, "Alpha.java");
        assertContains(suffixWildcard, "Alpha.java");
    }

    @Test
    public void selectedProjectComesFirst() {
        var tool = selectedProjectTool();

        List<String> results = resultLines(tool.eclipseSearchFiles("*.java", null, 0));
        int lastSelected = lastIndexContaining(results, "/" + PeonTestFixture.PROJECT_NAME + "/");
        int firstOther = firstIndexContaining(results, "/" + OTHER_PROJECT_NAME + "/");

        assertTrue("Expected selected Fixture hits: " + results, lastSelected >= 0);
        assertTrue("Expected foreign project hit: " + results, firstOther >= 0);
        assertTrue("Expected every selected Fixture hit before foreign hits: " + results,
                lastSelected < firstOther);
    }

    @Test
    public void selectedProjectFirst() {
        var tool = selectedProjectTool();

        String result = tool.eclipseSearchFiles("*.java", null, null);

        assertContains(result, "/" + PeonTestFixture.PROJECT_NAME + "/");
    }

    @Test
    public void foreignProjectsStayReachable() {
        var tool = selectedProjectTool();

        String result = tool.eclipseSearchFiles("*.java", null, 0);

        assertContains(result, "/" + PeonTestFixture.PROJECT_NAME + "/");
        assertContains(result, "/" + OTHER_PROJECT_NAME + "/Other.java");
    }

    @Test
    public void limitIsGlobal() {
        var tool = selectedProjectTool();

        List<String> results = resultLines(tool.eclipseSearchFiles("*.java", null, 3));

        assertEquals("Expected one global limit across projects: " + results, 3, results.size());
    }

    /**
     * Eclipse normally rejects a second project whose location overlaps the Fixture. If the
     * runtime permits it, the duplicate remains open during the workspace-wide search; otherwise
     * this falls back to checking the scoped Fixture result for unique Disk paths.
     */
    @Test
    public void dedupesNestedProjectHits() {
        IProject duplicate = attemptDuplicateFixtureView();
        try {
            var tool = selectedProjectTool();
            String projectName = duplicate == null ? PeonTestFixture.PROJECT_NAME : null;
            List<String> results = resultLines(tool.eclipseSearchFiles("*.java", projectName, 0));
            List<String> diskPaths = results.stream()
                    .map(EclipseUtil::resolveInEclipse)
                    .filter(resource -> resource.isPresent())
                    .map(resource -> JdtUtil.diskPathOf(resource.get()))
                    .toList();

            assertEquals("Expected every Disk path once: " + results,
                    diskPaths.size(), new HashSet<>(diskPaths).size());
        } finally {
            deleteDuplicateFixtureView(duplicate);
        }
    }

    @Test
    public void neverReturnsDerivedHits() throws Exception {
        var target = project.getFolder("target");
        if (!target.exists()) target.create(true, true, new NullProgressMonitor());
        targetWasDerived = target.isDerived();
        target.setDerived(false, new NullProgressMonitor());
        var generated = project.getFile(NON_DERIVED_TARGET_FILE);
        var content = new ByteArrayInputStream(
                "public class AlphaGenerated {}".getBytes(StandardCharsets.UTF_8));
        if (generated.exists()) {
            generated.setContents(content, true, false, new NullProgressMonitor());
        } else {
            generated.create(content, true, new NullProgressMonitor());
        }
        generated.setDerived(false, new NullProgressMonitor());
        var tool = new EclipseWorkspaceReadFileTool();

        String result = tool.eclipseSearchFiles("Alpha*", PeonTestFixture.PROJECT_NAME, 0);

        assertContains(result, "Alpha.java");
        assertFalse("Must not return derived output: " + result,
                result.contains("/bin/") || result.contains("/target/") || result.contains(".git"));
    }

    @Test
    public void emptyResultNamesScopeAndPattern() {
        var tool = new EclipseWorkspaceReadFileTool();

        String result = tool.eclipseSearchFiles(
                "zzz-does-not-exist", PeonTestFixture.PROJECT_NAME, 0);

        assertContains(result, PeonTestFixture.PROJECT_NAME);
        assertContains(result, "*zzz-does-not-exist*");
    }

    @Test
    public void negativeLimitIsClamped() {
        var tool = new EclipseWorkspaceReadFileTool();

        List<String> results = resultLines(tool.eclipseSearchFiles(
                "*.java", PeonTestFixture.PROJECT_NAME, -1));

        assertEquals("Expected lower Clamp to one result", 1, results.size());
    }

    @Test(timeout = 30_000)
    public void findsFileWrittenOutsideEclipse() throws Exception {
        externallyWrittenFile = project.getLocation().toFile().toPath().resolve(EXTERNALLY_WRITTEN_FILE);
        Files.write(externallyWrittenFile,
                "public class ExternallyWritten {}".getBytes(StandardCharsets.UTF_8));
        var tool = new EclipseWorkspaceReadFileTool();

        String result = tool.eclipseSearchFiles(
                "ExternallyWritten.java", PeonTestFixture.PROJECT_NAME, 0);

        assertContains(result, "/" + PeonTestFixture.PROJECT_NAME + "/ExternallyWritten.java");
    }

    @Test
    public void refreshesOnlySelectedProject() {
        var refreshCalls = new int[1];
        var refreshed = new ArrayList<IProject>();
        var tool = new EclipseWorkspaceReadFileTool() {
            @Override
            protected void refreshScope(List<IProject> scope) {
                refreshCalls[0]++;
                refreshed.addAll(scope);
            }
        };
        tool.setCurrentProject(project);

        tool.eclipseSearchFiles("zzz-does-not-exist", null, 0);

        assertEquals("Expected exactly one refresh call", 1, refreshCalls[0]);
        assertEquals("Expected only the selected project as Refresh-Ziel", List.of(project), refreshed);
    }

    @Test
    public void noRefreshWhenResultsFound() {
        var refreshCalls = new int[1];
        var tool = new EclipseWorkspaceReadFileTool() {
            @Override
            protected void refreshScope(List<IProject> scope) {
                refreshCalls[0]++;
            }
        };

        String result = tool.eclipseSearchFiles(
                "Alpha.java", PeonTestFixture.PROJECT_NAME, 0);

        assertContains(result, "Alpha.java");
        assertEquals("Refresh must not run when the first search finds results", 0, refreshCalls[0]);
    }

    private EclipseWorkspaceReadFileTool selectedProjectTool() {
        var tool = new EclipseWorkspaceReadFileTool();
        tool.setCurrentProject(project);
        return tool;
    }

    private IProject attemptDuplicateFixtureView() {
        var duplicate = ResourcesPlugin.getWorkspace().getRoot().getProject("zzz_fixture_duplicate");
        try {
            var description = ResourcesPlugin.getWorkspace().newProjectDescription(duplicate.getName());
            description.setLocation(project.getLocation());
            duplicate.create(description, new NullProgressMonitor());
            duplicate.open(new NullProgressMonitor());
            return duplicate;
        } catch (CoreException expectedForOverlappingLocation) {
            return null;
        }
    }

    private void deleteDuplicateFixtureView(IProject duplicate) {
        if (duplicate == null) return;
        try {
            if (duplicate.exists()) duplicate.delete(false, true, new NullProgressMonitor());
        } catch (CoreException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> resultLines(String result) {
        return Arrays.stream(result.split("\n"))
                .filter(line -> line.startsWith("/"))
                .toList();
    }

    private static int firstIndexContaining(List<String> values, String part) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).contains(part)) return i;
        }
        return -1;
    }

    private static int lastIndexContaining(List<String> values, String part) {
        for (int i = values.size() - 1; i >= 0; i--) {
            if (values.get(i).contains(part)) return i;
        }
        return -1;
    }
}
