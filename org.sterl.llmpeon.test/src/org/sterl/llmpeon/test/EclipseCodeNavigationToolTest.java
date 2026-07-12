package org.sterl.llmpeon.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.Test;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;

public class EclipseCodeNavigationToolTest extends AbstractTest {

    private EclipseCodeNavigationTool subject = new EclipseCodeNavigationTool();
    
    @Test
    public void test_JdtUtil_find_type() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        
        // WHEN
        var type = JdtUtil.findType(getClass().getPackageName(), getClass().getSimpleName(), new NullProgressMonitor());
        
        // THEN
        assertNotNull(type.get());
    }

    @Test
    public void test_find_type() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var name = this.getClass().getSimpleName();
        
        // WHEN
        var result = subject.eclipseFindJavaType(this.getClass().getSimpleName(), null, null);
        
        // THEN
        assertContains(result, name);
        assertContains(result, this.getClass().getPackageName());
        
        // WHEN
        result = subject.eclipseFindJavaType(this.getClass().getSimpleName(), this.getClass().getPackageName(), null);
        // THEN
        assertContains(result, name);
        assertContains(result, this.getClass().getPackageName());
        
        
        // WHEN
        result = subject.eclipseFindJavaType("*" 
                + name.substring(2, name.length() - 2) + "*", null, null);
        // THEN
        assertContains(result, name);
        assertContains(result, this.getClass().getPackageName());

    }
    
    @Test
    public void test_type_pattern() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        
        var result = subject.eclipseFindJavaType("EclipseCodeNavigationTool*", "org*", null);
        
        System.err.println(result);
        
        assertContains(result, "EclipseCodeNavigationTool");
    }

}
