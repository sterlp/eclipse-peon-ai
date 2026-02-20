package org.sterl.llmpeon.tool;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sterl.llmpeon.agent.AiMonitor;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;

/**
 * Owns all tools, the shared {@link ToolContext}, and the tool registry.
 * Tools are stable instances — only the context changes when the user selects a different file/project.
 */
public class ToolService {

    private final Map<String, SmartToolExecutor> toolExecutors = new HashMap<>();

    public List<ToolSpecification> toolSpecifications() {
        return toolExecutors.values().stream()
                .filter(SmartToolExecutor::isActive)
                .map(SmartToolExecutor::getSpec)
                .toList();
    }

    public SmartToolExecutor getExecutor(String toolName) {
        return toolExecutors.get(toolName);
    }
    
    public static class SmartToolExecutor extends DefaultToolExecutor {
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
