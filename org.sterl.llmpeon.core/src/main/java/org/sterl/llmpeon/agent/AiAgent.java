package org.sterl.llmpeon.agent;

import java.util.Collection;
import java.util.List;

import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.WriteValidator;
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
    public void setStaticContext(Collection<ChatMessage> staticContext);

    /**
     * The static context previously set via {@link #setStaticContext(Collection)} (date/OS/file rules).
     * Mirrors {@link #getUserContextInformations()}. Default empty for agents that hold none.
     */
    default List<ChatMessage> getStaticContext() {
        return List.of();
    }

    /**
     * Addition prompt information which should stay until changed -- added to the user message
     * 
     * @param userContextInformations addition prompt
     */
    void setUserContextInformations(Collection<String> userContextInformations);
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
     * The write-path validator this agent applies to every write tool call. Default: no restriction.
     * Peon-PO (Jon) overrides this to scope writes to docs. Provided per request, like the tool filter.
     */
    default WriteValidator getWriteValidator() {
        return WriteValidator.ALLOW_ALL;
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

    /** @return whether this agent's model supports thinking (drives the chat brain toggle state). */
    default boolean isThinkSupported() {
        return false;
    }

    /** @deprecated use {@link #isThinkSupported()} */
    @Deprecated
    default boolean isThinkEnabled() {
        return isThinkSupported();
    }

    /** @return true if the agent is currently processing a call (including internal queue chaining). */
    default boolean isWorking() {
        return false;
    }

    /**
     * Queue a message for follow-up while the agent is working.
     * @param msg the message to queue
     * @return true if a new queue entry was created, false if silently merged into existing batch
     */
    default boolean queueMessage(String msg) {
        return false;
    }

    /** @return the number of queued messages waiting to be processed. Default 0 for agents without queues. */
    default int getQueuedMessageCount() {
        return 0;
    }

    /** Drain remaining queue on abort, returning null if empty. Default no-op. */
    default String drainQueue() {
        return null;
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
