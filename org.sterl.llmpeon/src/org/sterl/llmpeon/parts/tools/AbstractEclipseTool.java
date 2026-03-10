package org.sterl.llmpeon.parts.tools;

import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.sterl.llmpeon.parts.monitor.EclipseAiMonitor;
import org.sterl.llmpeon.tool.AbstractTool;

public class AbstractEclipseTool extends AbstractTool {

    private static final NullProgressMonitor NULL_MONITOR = new NullProgressMonitor();
    
    protected static final Set<String> DERIVED_SOURCES = Set.of(
            "/target/", "/bin/", ".class", ".git");

    protected boolean isNotDerived(String file) {
        for (var v : DERIVED_SOURCES) {
            if (file.contains(v)) return false;
        }
        return true;
    }
    
    protected IProgressMonitor getProgressMonitor() {
        if (monitor instanceof EclipseAiMonitor eai) {
            return IProgressMonitor.nullSafe(eai.getIProgressMonitor());
        }
        return NULL_MONITOR;
    }
    
    @Override
    protected void monitorMessage(String m) {
        super.monitorMessage(m);
        getProgressMonitor().subTask(m);
    }
}
