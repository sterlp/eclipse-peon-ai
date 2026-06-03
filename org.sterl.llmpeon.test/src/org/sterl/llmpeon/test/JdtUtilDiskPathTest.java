package org.sterl.llmpeon.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.junit.Test;
import org.sterl.llmpeon.parts.shared.JdtUtil;

/**
 * Test for Issue #66: diskPathOf returns null for sub-resources (folders/files inside projects)
 */
public class JdtUtilDiskPathTest extends AbstractTest {

    @Test
    public void testDiskPathOfProject() {
        // GIVEN a project
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        
        // WHEN
        var diskPath = JdtUtil.diskPathOf(project);
        
        // THEN - should return non-null for project
        assertNotNull("diskPathOf should return disk path for project", diskPath);
    }
    
    @Test
    public void testDiskPathOfSubFolder() {
        // GIVEN a sub-folder inside the project
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        
        // WHEN - get a sub-folder (e.g., src folder)
        IFolder srcFolder = project.getFolder("src");
        if (!srcFolder.exists()) {
            // Try commands folder from ai-skill-codex if it exists
            return; // Skip if folder doesn't exist
        }
        
        // THEN - diskPathOf should return non-null for sub-folder
        var diskPath = JdtUtil.diskPathOf(srcFolder);
        assertNotNull("diskPathOf should return disk path for sub-folder: " + srcFolder.getFullPath(), diskPath);
    }
    
    @Test
    public void testDiskPathOfNestedResource() {
        // GIVEN a nested resource path
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        
        // WHEN - try to find any existing sub-resource
        IResource nestedResource = findAnySubResource(project);
        if (nestedResource == null) {
            return; // Skip if no sub-resources found
        }
        
        // THEN - diskPathOf should return non-null
        var diskPath = JdtUtil.diskPathOf(nestedResource);
        assertNotNull("diskPathOf should return disk path for nested resource: " + nestedResource.getFullPath(), diskPath);
    }
    
    private IResource findAnySubResource(IResource parent) {
        try {
            if (parent instanceof IContainer container) {
                for (IResource member : container.members()) {
                    if (member.getType() == IResource.FOLDER) {
                        return member;
                    }
                    // Recurse into folders
                    if (member instanceof IContainer subContainer) {
                        var found = findAnySubResource(member);
                        if (found != null) return found;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
}
