package org.sterl.llmpeon.tool;

import org.sterl.llmpeon.agent.AiMonitor;
import org.sterl.llmpeon.tool.model.SmartTool;

public class AbstractTool implements SmartTool {

    protected AiMonitor monitor;
    
    @Override
    public void withMonitor(AiMonitor monitor) {
        this.monitor = monitor;
    }
    
    protected boolean hasMonitor() { return monitor != null; }
    
    protected void monitorMessage(String m) {
        if (hasMonitor()) monitor.onAction(m);
    }

}
