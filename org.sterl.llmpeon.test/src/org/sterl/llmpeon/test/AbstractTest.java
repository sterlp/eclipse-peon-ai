package org.sterl.llmpeon.test;

import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.util.concurrent.CountDownLatch;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.BeforeClass;

public abstract class AbstractTest {

    @BeforeClass
    public static void importProjectIntoWorkspace() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // Eclipse sets the working directory to the project root when running a JUnit Plug-in Test.
        // In Maven/Tycho the runtime workspace lives inside target/work/data which is a subdirectory
        // of the project, so Eclipse refuses to import it (path overlap). Skip in that case.
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

        // Required for projects outside the runtime workspace directory
        desc.setLocation(IPath.fromOSString(projectDir.getAbsolutePath()));

        IProject project = workspace.getRoot().getProject(desc.getName());
        workspace.run(monitor -> {
            // Remove stale registration from previous runs (does not delete files on disk)
            if (project.exists()) {
                project.delete(false, true, monitor);
            }
            project.create(desc, monitor);
            project.open(monitor);
            project.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());
            //System.err.println("Project imported: " + project.getName() + " @ " + project.getLocation());
            latch.countDown();
        }, new NullProgressMonitor());

        latch.await();
    }

    protected static boolean isWorkspaceAvailable() {
        try {
            ResourcesPlugin.getWorkspace();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
