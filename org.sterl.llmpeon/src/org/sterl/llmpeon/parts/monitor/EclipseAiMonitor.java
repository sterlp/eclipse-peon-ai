package org.sterl.llmpeon.parts.monitor;

import org.eclipse.core.runtime.IProgressMonitor;
import org.sterl.llmpeon.agent.AiMonitor;

public interface EclipseAiMonitor extends AiMonitor {
    IProgressMonitor getIProgressMonitor();
}
