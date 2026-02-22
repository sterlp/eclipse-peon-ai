package org.sterl.llmpeon.tool;

import java.lang.reflect.Method;

import org.sterl.llmpeon.agent.AiMonitor;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.DefaultToolExecutor;

public class SmartToolExecutor extends DefaultToolExecutor {
    private final SmartTool tool;
    private final ToolSpecification spec;

    public SmartToolExecutor(SmartTool tool, Method method, ToolSpecification spec) {
        super(tool, method);
        this.tool = tool;
        this.spec = spec;
    }
    public SmartTool getTool() {
        return tool;
    }
    public ToolSpecification getSpec() {
        return spec;
    }
    public boolean isActive() {
        return tool.isActive();
    }
    
    public String run(ToolExecutionRequest request, AiMonitor monitor) {
        try {
            tool.withMonitor(monitor);
            return execute(request, request.id());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            if (monitor != null) monitor.onAction(spec.name() + " failed. " + e.getMessage());
            throw e;
        } finally {
            tool.withMonitor(null);
        }
    }
}