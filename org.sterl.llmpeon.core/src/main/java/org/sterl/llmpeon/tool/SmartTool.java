package org.sterl.llmpeon.tool;

import org.sterl.llmpeon.agent.AiMonitor;

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
