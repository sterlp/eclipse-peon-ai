package org.sterl.llmpeon.parts.tools;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.sterl.llmpeon.parts.monitor.EclipseAiMonitor;
import org.sterl.llmpeon.tool.AbstractTool;

public class AbstractEclipseTool extends AbstractTool {

    private static final NullProgressMonitor NULL_MONITOR = new NullProgressMonitor();

    protected IProgressMonitor getProgressMonitor() {
        if (monitor instanceof EclipseAiMonitor eai) {
            return IProgressMonitor.nullSafe(eai.getIProgressMonitor());
        }
        return NULL_MONITOR;
    }
}
