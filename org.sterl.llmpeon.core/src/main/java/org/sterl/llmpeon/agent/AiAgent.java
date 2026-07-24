package org.sterl.llmpeon.agent;

import java.util.List;

import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

public interface AiAgent {
    String getName();
    String getSystemPrompt();

    ChatResponse call(String message, AiMonitor monitor);
    ChatResponse compressContext(AiMonitor monitor);

    ThreadSafeMemory getMemory();
    
    /**
     * Clear the current state
     */
    void clear();
    
    /**
     * Only context information which doesn't change - change only after clear!
     * Otherwise it will kill the KV-cache!
     * 
     * @param staticContext attached as system message.
     */
    public void setStaticContext(List<ChatMessage> staticContext);

    /**
     * Addition prompt information which should stay until changed -- added to the user message
     * 
     * @param userContextInformations addition prompt
     */
    void setUserContextInformations(List<String> userContextInformations);
    /**
     * addition prompt information which should stay until changed
     */
    List<String> getUserContextInformations();
    
    /**
     * If a handover is available show the button
     * 
     * @return <code>null</code> no handover, name of the handover agent
     */
    default String handoverTo() {
        return null;
    }

    default Double getTemperature() {
        return null;
    }

    /**
     * get a custom model for this agent
     */
    default String getAgentModelName() {
        return null;
    }
    /**
     * set a custom model, save it is supported
     */
    default boolean setAgentModelName(String modelName) {
        return false;
    }
    
    default boolean isReadOnly() {
        return false;
    }

    /** @return whether thinking is enabled for this agent (drives the chat brain toggle state). */
    default boolean isThinkEnabled() {
        return false;
    }
    
    /**
     * If it is an agent just to be used as tool
     */
    default boolean isTool() {
        return false;
    }
    /**
     * @return list of enabled tool names `*` for all tools
     */
    default List<String> getTools() {
        return List.of("*");
    }

    /**
     * Whether the given built-in tool is offered to the LLM for this agent. Reflects the agent's
     * full tool filter (allowlist <em>and</em> the read-only "no edit tools" rule), so it matches
     * exactly what the agent sends at runtime. For UI introspection (e.g. the tool activity popup).
     */
    boolean isToolActive(SmartToolExecutor exec);

    /**
     * Whether the given MCP tool name is offered to the LLM for this agent (name allowlist).
     * For UI introspection.
     */
    boolean isMcpToolActive(String toolName);

    /**
     * Returns the tool service used by this agent.
     * @return the tool service, or null if not set
     */
    default ToolService getToolService() {
        return null;
    }
}
