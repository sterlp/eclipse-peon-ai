package org.sterl.llmpeon.tool;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;

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
    }

    public List<ToolSpecification> toolSpecifications() {
        return toolExecutors.values().stream()
                .filter(SmartToolExecutor::isActive)
                .map(SmartToolExecutor::getSpec)
                .toList();
    }

    public SmartToolExecutor getExecutor(String toolName) {
        return toolExecutors.get(toolName);
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
