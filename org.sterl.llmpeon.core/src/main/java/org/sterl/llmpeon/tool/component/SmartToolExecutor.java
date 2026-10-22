package org.sterl.llmpeon.tool.component;

import java.lang.reflect.Method;

import org.sterl.llmpeon.exception.ExceptionUtil;
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
            var msg = messageOf(e);
            reportProblem(request, req, msg);
            return msg;
        } catch (ToolExecutionException e) {
            if (ExceptionUtil.isCanceled(e)) throw e; // cancellation wins — not a tool error
            // R5: a denial anywhere in the cause chain is a tool error — the model gets its message verbatim
            var denial = ExceptionUtil.findCause(e, IllegalArgumentException.class);
            if (denial != null) {
                var msg = messageOf(denial);
                reportProblem(request, req, msg);
                return msg;
            }
            throw e;
        } catch (Exception e) {
            if (ExceptionUtil.isCanceled(e)) throw e; // cancellation wrapped in another exception
            if (e instanceof RuntimeException ex) throw ex;
            throw new RuntimeException(e);
        } finally {
            tool.withToolRequest(null);
        }
    }
    private void reportProblem(ToolExecutionRequest request,
            ToolLoopRequest req, String msg) {
        if (msg != null && msg.length() > 200) msg = msg.substring(0, 180) + "...";
        req.getMonitor().onProblem(request.name() + ": " + msg);
    }

    /** D10: a model-facing message is never null — a null message means the class's toString(). */
    private static String messageOf(Throwable t) {
        var msg = t.getMessage();
        return msg != null ? msg : t.toString();
    }
}