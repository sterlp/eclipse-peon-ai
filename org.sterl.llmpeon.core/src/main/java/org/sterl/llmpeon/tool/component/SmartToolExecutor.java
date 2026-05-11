package org.sterl.llmpeon.tool.component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.SmartTool;

import dev.langchain4j.model.chat.StreamingChatModel;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
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
    
    public boolean shouldClearMemory() {
        return tool.clearMemory();
    }

    public String run(ToolExecutionRequest request, AiMonitor monitor, StreamingChatModel chatModel, List<ChatMessage> memory) {
        monitor = AiMonitor.nullSafety(monitor);
        try {
            tool.withMonitor(monitor);
            tool.withChatModel(chatModel);
            tool.withMemory(new ArrayList<ChatMessage>(memory)); // copy
            return executor.execute(request, request.id());
        } catch (IllegalArgumentException e) {
            monitor.onProblem(request.name() + ": " + e.getMessage());
            return e.getMessage();
        } catch (ToolExecutionException e) {
            if (e.getCause() instanceof IllegalArgumentException ex) {
                monitor.onProblem(request.name() + ": " + ex.getMessage());
                return ex.getMessage();
            }
            throw e;
        } catch (RuntimeException e) {
            if (monitor != null) monitor.onProblem(spec.name() + " failed: " + e.getMessage());
            throw e;
        } finally {
            tool.withMonitor(AiMonitor.NULL_MONITOR);
            tool.withChatModel(null);
            tool.withMemory(Collections.emptyList());
        }
    }
}