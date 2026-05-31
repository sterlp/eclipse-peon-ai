package org.sterl.llmpeon.tool;

import dev.langchain4j.agent.tool.ToolSpecification;

/**
 * A dynamically registered tool that does not rely on reflection.
 * Implementations provide name, description, and execution logic directly.
 */
public interface DynamicTool extends SmartTool {

    String getName();

    String getDescription();

    /**
     * Executes the tool and returns the result as a string.
     * Throw {@link IllegalArgumentException} to return an error to the LLM.
     */
    String execute();

    default ToolSpecification getToolSpecification() {
        return ToolSpecification.builder()
                .name(getName())
                .description(getDescription())
                .build();
    }
}
