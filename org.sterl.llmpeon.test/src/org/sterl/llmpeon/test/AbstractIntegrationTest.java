package org.sterl.llmpeon.test;

import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.After;
import org.junit.BeforeClass;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceWriteFileTool;

/**
 * Base for integration tests that require an Eclipse workspace.
 * Extends AbstractUnitTest and adds workspace import + Eclipse file helpers.
 */
public abstract class AbstractIntegrationTest extends AbstractUnitTest {

    protected static IProject project;
    private final EclipseWorkspaceWriteFileTool writeTool = new EclipseWorkspaceWriteFileTool();
    private final Set<String> toDelete = new HashSet<>();

    @Override
    @After
    public void after() {
        super.after();

        writeTool.setCurrentProject(project);
        toDelete.forEach(f -> writeTool.eclipseDeleteResource(f));
        toDelete.clear();
    }

    protected void eclipseWriteFile(String file, String content) {
        writeTool.setCurrentProject(project);
        writeTool.eclipseWriteFile(file, content);
        toDelete.add(file);
    }

    protected void eclipseDeleteResource(String file) {
        writeTool.setCurrentProject(project);
        writeTool.eclipseDeleteResource(file);
        toDelete.remove(file);
    }

    @BeforeClass
    public static void importProjectIntoWorkspace() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        File dir = PeonTestFixture.dir();
        try {
            importProject(dir);
        } catch (CoreException e) {
            throw new IllegalStateException(
                    "failed to import test fixture " + dir + ": " + e.getMessage(), e);
        }
    }

    protected static void importProject(File projectDir) throws Exception {
        final var waiter = new CompletableFuture<Void>();

        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IProjectDescription desc = workspace.loadProjectDescription(
                IPath.fromOSString(new File(projectDir, ".project").getAbsolutePath()));

        desc.setLocation(IPath.fromOSString(projectDir.getAbsolutePath()));

        project = workspace.getRoot().getProject(desc.getName());
        workspace.run(monitor -> {
            try {
                if (project.exists()) {
                    project.delete(false, true, monitor);
                }
                project.create(desc, monitor);
                project.open(monitor);
                project.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());
                waiter.complete(null);
            } catch (Exception e) {
                waiter.completeExceptionally(e);
            }
        }, new NullProgressMonitor());

        waiter.get(10, TimeUnit.SECONDS);
    }

    protected static boolean isWorkspaceAvailable() {
        try {
            var w = ResourcesPlugin.getWorkspace();
            for (IProject p : w.getRoot().getProjects()) p.open(new NullProgressMonitor());
            return true;
        } catch (IllegalStateException | CoreException e) {
            System.err.println(e.getMessage());
            return false;
        }
    }
}
