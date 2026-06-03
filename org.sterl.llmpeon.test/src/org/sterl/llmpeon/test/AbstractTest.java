package org.sterl.llmpeon.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.BeforeClass;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;

public abstract class AbstractTest {
    
    protected static IProject project;
    
    public static void assertContains(String value, String expected) {
        assertNotNull("Extected to find " + expected, value);
        assertTrue("Expected:\n"
                + value + "\n"
                + "to contain:\n"
                + expected,
                value.contains(expected));
    }
    
    public static void assertIsEmpty(Optional<?> v) {
        assertTrue("Expected optional to has a value", v.isEmpty());
    }
    
    public static void assertIsPresent(Optional<?> v) {
        assertTrue("Expected optional to have a value", v.isPresent());
    }

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

        project = workspace.getRoot().getProject(desc.getName());
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
    
    public static void assertHasUserMessageWith(Collection<ChatMessage> messages, String content) {
        var textMessages = messages.stream()
            .filter(m -> m instanceof UserMessage)
            .map(m -> ((UserMessage)m).singleText())
            .toList();
        assertHasMessageWith(textMessages, content);
    }
    
    public static void assertHasMessageWith(Collection<String> textMessages, String content) {
        var match = textMessages.stream().filter(m -> m.contains(content)).findAny();
        assertTrue("Could not find: \n" + content
                + "\nin:\n" + textMessages.stream().collect(Collectors.joining("\n")), 
                match.isPresent());
    }

    protected static boolean isWorkspaceAvailable() {
        try {
            ResourcesPlugin.getWorkspace();
            for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) p.open(new NullProgressMonitor());
            return true;
        } catch (IllegalStateException | CoreException e) {
            return false;
        }
    }
}
