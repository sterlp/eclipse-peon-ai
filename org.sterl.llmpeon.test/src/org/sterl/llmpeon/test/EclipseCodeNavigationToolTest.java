package org.sterl.llmpeon.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.Test;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;

public class EclipseCodeNavigationToolTest extends AbstractIntegrationTest {

    private static final String FIXTURE_PACKAGE = "org.sterl.fixture";
    private EclipseCodeNavigationTool subject = new EclipseCodeNavigationTool();

    @Test
    public void test_JdtUtil_find_type() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        var type = JdtUtil.findType(
                FIXTURE_PACKAGE, "Alpha", new NullProgressMonitor(), PeonTestFixture.PROJECT_NAME);

        assertNotNull(type.get());
    }

    @Test
    public void test_find_type() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        var result = subject.eclipseFindJavaType("Alpha", null, PeonTestFixture.PROJECT_NAME);
        assertContains(result, "Alpha");
        assertContains(result, FIXTURE_PACKAGE);

        result = subject.eclipseFindJavaType("Alpha", FIXTURE_PACKAGE, PeonTestFixture.PROJECT_NAME);
        assertContains(result, "Alpha");
        assertContains(result, FIXTURE_PACKAGE);

        result = subject.eclipseFindJavaType("Alph*", null, PeonTestFixture.PROJECT_NAME);
        assertContains(result, "Alpha");
        assertContains(result, FIXTURE_PACKAGE);
    }

    @Test
    public void test_type_pattern() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        var result = subject.eclipseFindJavaType(
                "Alpha*", "org.sterl.*", PeonTestFixture.PROJECT_NAME);

        assertContains(result, "Alpha");
        assertContains(result, FIXTURE_PACKAGE);
    }
}
