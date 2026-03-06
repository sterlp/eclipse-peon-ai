package org.sterl.llmpeon.tool;

import java.util.List;

import org.sterl.llmpeon.agent.AgentService;
import org.sterl.llmpeon.agent.AiMonitor;

import dev.langchain4j.data.message.ChatMessage;

/**
 * Smart tools can tell if why are active furthermore throw an
 * {@link IllegalArgumentException} to return to the LLM an error.
 */
public interface SmartTool {

    default boolean clearMemory() {
        return false;
    }
    /**
     * If true the tool can modify state (write files, run shell commands, etc.).
     * Plan agents should only receive tools where this returns false.
     */
    default boolean isEditTool() { return false; }
    
    /**
     * Adds a Monitor for the observation of the tool
     */
    void withMonitor(AiMonitor monitor);

    void withAgentService(AgentService agentService);
    
    void withMemory(List<ChatMessage> memory);
}
