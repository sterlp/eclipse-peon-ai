package org.sterl.llmpeon.tool;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.sterl.llmpeon.agent.AgentService;
import org.sterl.llmpeon.agent.AiMonitor;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
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
    
    public boolean shouldClearMemory() {
        return tool.clearMemory();
    }

    public String run(ToolExecutionRequest request, AiMonitor monitor, AgentService agentService, List<ChatMessage> memory) {
        try {
            tool.withMonitor(monitor);
            tool.withAgentService(agentService);
            tool.withMemory(new ArrayList<ChatMessage>(memory)); // copy
            return execute(request, request.id());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            if (monitor != null) monitor.onAction(spec.name() + " failed. " + e.getMessage());
            throw e;
        } finally {
            tool.withMonitor(AiMonitor.NULL_MONITOR);
            tool.withMemory(Collections.emptyList());
        }
    }
}