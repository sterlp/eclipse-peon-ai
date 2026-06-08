package org.sterl.llmpeon.tool.component;

import java.lang.reflect.Method;

import org.sterl.llmpeon.tool.SmartTool;
import org.sterl.llmpeon.tool.ToolLoopRequest;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.service.tool.DefaultToolExecutor;

public class SmartToolExecutor {
    private final DefaultToolExecutor executor;
    private final SmartTool tool;
    private final ToolSpecification spec;

    public SmartToolExecutor(SmartTool tool, Method method, ToolSpecification spec) {
        this.executor = DefaultToolExecutor.builder()
                .object(tool)
                .originalMethod(method)
                .methodToInvoke(method)
                .propagateToolExecutionExceptions(true)
                .build();

        this.tool = tool;
        this.spec = spec;
    }
    public SmartTool getTool() {
        return tool;
    }
    public ToolSpecification getSpec() {
        return spec;
    }
    
    public String run(ToolExecutionRequest request, ToolLoopRequest req) {
        try {
            tool.withToolRequest(req);
            return executor.execute(request, request.id());
        } catch (IllegalArgumentException e) {
            req.getMonitor().onProblem(request.name() + ": " + e.getMessage());
            return e.getMessage();
        } catch (ToolExecutionException e) {
            if (e.getCause() instanceof IllegalArgumentException ex) {
                req.getMonitor().onProblem(request.name() + ": " + ex.getMessage());
                return ex.getMessage();
            }
            throw e;
        } finally {
            tool.withToolRequest(null);
        }
    }
}