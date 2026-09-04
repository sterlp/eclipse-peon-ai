package org.sterl.llmpeon.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.junit.Test;
import org.sterl.llmpeon.parts.shared.JdtUtil;

/**
 * Test for Issue #66: diskPathOf returns null for sub-resources (folders/files inside projects)
 */
public class JdtUtilDiskPathTest extends AbstractIntegrationTest {

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
        
        // THEN - diskPathOf should return non-null for sub-folder
        var diskPath = JdtUtil.diskPathOf(srcFolder);
        assertNotNull("diskPathOf should return disk path for sub-folder: " + srcFolder.getFullPath(), diskPath);
    }
    
    @Test
    public void testDiskPathOfNestedResource() {
        // GIVEN a nested resource path
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        
        // WHEN - try to find any existing sub-resource
        IResource nestedResource = project.getFolder("src/org/sterl/fixture/sub");
        
        // THEN - diskPathOf should return non-null
        var diskPath = JdtUtil.diskPathOf(nestedResource);
        assertNotNull("diskPathOf should return disk path for nested resource: " + nestedResource.getFullPath(), diskPath);
    }

    @Test
    public void projectScopeIsHonoured() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var monitor = new org.eclipse.core.runtime.NullProgressMonitor();

        var fixtureType = JdtUtil.findType(
                "org.sterl.fixture", "Alpha", monitor, PeonTestFixture.PROJECT_NAME);
        var externalType = JdtUtil.findType(
                "org.sterl.llmpeon.parts.shared", "JdtUtil", monitor, PeonTestFixture.PROJECT_NAME);

        org.junit.Assert.assertTrue("Expected Alpha in Fixture project", fixtureType.isPresent());
        org.junit.Assert.assertTrue("Project scope leaked to another project", externalType.isEmpty());
    }

}
