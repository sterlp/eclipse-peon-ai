package org.sterl.llmpeon.tool;

/**
 * Throw {@link IllegalArgumentException} to return to the LLM an error.
 */
public interface SmartTool {

    /**
     * If true the tool can modify state (write files, run shell commands, etc.).
     * Plan agents should only receive tools where this returns false.
     */
    default boolean isEditTool() { return false; }
    
    /**
     * Adds a Monitor for the observation of the tool
     */
    void withToolRequest(ToolLoopRequest request);
}
