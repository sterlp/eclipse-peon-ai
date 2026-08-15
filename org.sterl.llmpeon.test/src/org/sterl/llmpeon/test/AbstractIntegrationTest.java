package org.sterl.llmpeon.test;

import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

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

        try {
            importProject(new File("./").getCanonicalFile());
        } catch (CoreException e) {
            assumeTrue("Cannot import project (likely Maven/Tycho workspace overlap): " + e.getMessage(), false);
        }
    }

    protected static void importProject(File projectDir) throws Exception {
        final var latch = new CountDownLatch(1);

        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IProjectDescription desc = workspace.loadProjectDescription(
                IPath.fromOSString(new File(projectDir, ".project").getAbsolutePath()));

        desc.setLocation(IPath.fromOSString(projectDir.getAbsolutePath()));

        project = workspace.getRoot().getProject(desc.getName());
        workspace.run(monitor -> {
            if (project.exists()) {
                project.delete(false, true, monitor);
            }
            project.create(desc, monitor);
            project.open(monitor);
            project.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());
            latch.countDown();
        }, new NullProgressMonitor());

        latch.await();
    }

    protected static boolean isWorkspaceAvailable() {
        try {
            ResourcesPlugin.getWorkspace();
            for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects())
                p.open(new NullProgressMonitor());
            return true;
        } catch (IllegalStateException | CoreException e) {
            return false;
        }
    }
}
