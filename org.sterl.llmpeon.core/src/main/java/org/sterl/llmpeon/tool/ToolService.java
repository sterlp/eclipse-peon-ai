package org.sterl.llmpeon.tool;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;

/**
 * Owns all tools, the shared {@link ToolContext}, and the tool registry.
 * Tools are stable instances — only the context changes when the user selects a different file/project.
 */
public class ToolService {

    private final List<ToolSpecification> toolSpecs = new ArrayList<>();
    private final Map<String, ToolExecutor> toolExecutors = new HashMap<>();

    public List<ToolSpecification> toolSpecifications() {
        return toolSpecs;
    }

    public ToolExecutor getExecutor(String toolName) {
        return toolExecutors.get(toolName);
    }

    /**
     * Registers any object that has methods annotated with {@link Tool}.
     * Existing tools with the same name will be replaced.
     */
    public void addTool(Object toolObject) {
        for (Method method : toolObject.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                var spec = ToolSpecifications.toolSpecificationFrom(method);
                var removed = toolSpecs.removeIf(s -> s.name().equals(spec.name()));
                if (removed) throw new RuntimeException("Tool with " + spec.name() + " already registered ...");
                toolSpecs.add(spec);
                toolExecutors.put(spec.name(), new DefaultToolExecutor(toolObject, method));
                System.out.println("added tool " + spec);
            }
        }
    }
}
