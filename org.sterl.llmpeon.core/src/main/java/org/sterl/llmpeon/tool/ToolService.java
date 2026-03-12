package org.sterl.llmpeon.tool;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.sterl.llmpeon.agent.AgentService;
import org.sterl.llmpeon.agent.AiMonitor;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Owns all tools and the tool registry.
 * Tools are stable instances — only the context changes when the user selects a different file/project.
 * 
 * https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/tools.md
 */
public class ToolService {

    private final Map<String, SmartToolExecutor> toolExecutors = new HashMap<>();

    public ToolService() {
        super();
        addTool(new WebFetchTool());
        addTool(new SearchAgentTool(this));
        addTool(new ShellTool());
        addTool(new CompressorAgentTool());
    }

    public List<ToolSpecification> toolSpecifications() {
        return toolExecutors.values().stream()
                .map(SmartToolExecutor::getSpec)
                .toList();
    }

    /** Returns only tool specs for tools where {@link SmartTool#isEditTool()} is false. */
    public List<ToolSpecification> readOnlyToolSpecifications() {
        return toolExecutors.values().stream()
                .filter(e -> !e.getTool().isEditTool())
                .map(SmartToolExecutor::getSpec)
                .toList();
    }
    
    public List<ToolSpecification> toolSpecifications(Predicate<SmartToolExecutor> filter) {
        return toolExecutors.values().stream()
                .filter(filter)
                .map(SmartToolExecutor::getSpec)
                .toList();
    }

    public SmartToolExecutor getExecutor(String toolName) {
        return toolExecutors.get(toolName);
    }
    
    /** Returns true if AI-triggered compression was applied (caller should end the turn). */
    public List<ChatMessage> runAllTools(ChatResponse response, AgentService agentService, AiMonitor monitor, List<ChatMessage> memory) {
        if (!response.aiMessage().hasToolExecutionRequests()) {
            // No tools to run — still add the final AI message to memory
            var newMemory = new ArrayList<ChatMessage>(memory);
            newMemory.add(response.aiMessage());
            return newMemory;
        }

        var toolResults = new ArrayList<ToolExecutionResultMessage>();
        boolean clearMemory = false;

        for (var tr : response.aiMessage().toolExecutionRequests()) {
            var trResult = execute(tr, monitor, agentService, memory);
            toolResults.add(trResult.message());
            if (trResult.clearMemory()) clearMemory = true;
            if (monitor.isCanceled()) break;
        }

        var newMemory = new ArrayList<ChatMessage>();
        if (!clearMemory) newMemory.addAll(memory);
        newMemory.add(response.aiMessage());
        newMemory.addAll(toolResults);
        return newMemory;
    }

    static record ToolResult(boolean clearMemory, ToolExecutionResultMessage message) {}
    
    public ToolResult execute(ToolExecutionRequest tr, AiMonitor monitor, AgentService agentService, List<ChatMessage> memory) {
        var executor = toolExecutors.get(tr.name());
        String result;
        if (executor == null) {
            result = "Error: unknown tool '" + tr.name() + "' check spelling";
            AiMonitor.nullSafty(monitor).onProblem(result);
        } else {
            try {
                result = executor.run(tr, monitor, agentService, memory);
            } catch (IllegalArgumentException e) {
                // user-facing argument error — return as-is so the LLM can correct itself
                result = e.getMessage();
                AiMonitor.nullSafty(monitor).onProblem(tr.name() + ": " + result);
            }
        }
        return new ToolResult(
                executor == null ? false : executor.shouldClearMemory(), 
                ToolExecutionResultMessage.from(tr.id(), tr.name(), result));
    }
    
    /**
     * Registers any object that has methods annotated with {@link Tool}.
     * Existing tools with the same name will be replaced.
     */
    public void addTool(SmartTool toolObject) {
        for (Method method : toolObject.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                var spec = ToolSpecifications.toolSpecificationFrom(method);
                var old = toolExecutors.put(spec.name(), new SmartToolExecutor(toolObject, method, spec));
                if (old != null) throw new RuntimeException("Tool with " + spec.name() + " already registered ...");
                System.out.println("added tool " + spec);
            }
        }
    }
}
