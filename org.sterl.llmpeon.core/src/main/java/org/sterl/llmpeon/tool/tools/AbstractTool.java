package org.sterl.llmpeon.tool.tools;

import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.SmartTool;
import org.sterl.llmpeon.tool.ToolLoopRequest;

public class AbstractTool implements SmartTool {

    protected AiMonitor monitor = AiMonitor.NULL_MONITOR;
    protected ToolLoopRequest request;
    
    protected void onTool(String m) {
        monitor.onTool(m);
    }

    protected void onProblem(String m) {
        monitor.onProblem(m);
    }

    @Override
    public void withToolRequest(ToolLoopRequest request) {
        this.request = request;
        this.monitor = request == null ? AiMonitor.NULL_MONITOR : AiMonitor.nullSafety(request.getMonitor());
    }
}
