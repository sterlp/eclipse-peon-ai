package org.sterl.llmpeon.tool;

import org.sterl.llmpeon.agent.AiMonitor;

public class AbstractTool implements SmartTool {

    protected AiMonitor monitor;
    
    @Override
    public void withMonitor(AiMonitor monitor) {
        this.monitor = monitor;
    }
    
    protected void monitorMessage(String m) {
        if (monitor != null) monitor.onAction(m);
    }

}
