package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.core.resources.ResourcesPlugin;
import org.junit.Test;

public class AbstractIntegrationTestSetupTest extends AbstractIntegrationTest {

    @Test
    public void importsFixtureProject() {
        assertEquals(PeonTestFixture.PROJECT_NAME, project.getName());
    }

    @Test
    public void writesOnlyIntoFixture() {
        var path = "/test_project/tmp/setup-write.txt";

        eclipseWriteFile(path, "fixture only");

        assertTrue(project.getFile("tmp/setup-write.txt").exists());
        assertFalse(ResourcesPlugin.getWorkspace().getRoot()
                .getProject("org.sterl.llmpeon.test").getFile("tmp/setup-write.txt").exists());
    }
}
