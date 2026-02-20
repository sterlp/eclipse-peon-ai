package org.sterl.llmpeon.tool.model;

import org.sterl.llmpeon.agent.AiMonitor;

/**
 * Smart tools can tell if why are active furthermore throw an
 * {@link IllegalArgumentException} to return to the LLM an error.
 */
public interface SmartTool {

    /**
     * If the tool can be used currently
     */
    default boolean isActive() { return true; }
    
    /**
     * Adds a Monitor for the observation of the tool
     */
    void withMonitor(AiMonitor monitor);
}
