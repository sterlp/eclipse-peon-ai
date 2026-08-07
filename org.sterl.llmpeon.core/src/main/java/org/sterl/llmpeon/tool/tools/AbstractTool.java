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

    /**
     * Enforce the current request's {@link org.sterl.llmpeon.tool.WriteValidator} on a raw write path.
     * No-op when the tool is invoked without a request (e.g. direct unit-test calls).
     *
     * @throws IllegalArgumentException if the agent may not write to {@code path}
     */
    protected void validateWrite(String path) {
        if (request != null) request.getWriteValidator().validate(path);
    }

    @Override
    public void withToolRequest(ToolLoopRequest request) {
        this.request = request;
        this.monitor = request == null ? AiMonitor.NULL_MONITOR : AiMonitor.nullSafety(request.getMonitor());
    }
}
