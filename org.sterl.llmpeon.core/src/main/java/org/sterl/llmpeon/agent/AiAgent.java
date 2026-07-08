package org.sterl.llmpeon.agent;

import java.util.List;

import org.sterl.llmpeon.shared.AiMonitor;

import dev.langchain4j.model.chat.response.ChatResponse;

public interface AiAgent {
    String getName();
    String getSystemPrompt();
    
    ChatResponse call(String message, AiMonitor monitor);
    
    /**
     * addition prompt information which should stay until changed
     * @param userContextInformations addition prompt
     */
    void setUserContextInformations(List<String> userContextInformations);
    /**
     * addition prompt information which should stay until changed
     */
    List<String> getUserContextInformations();

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
}
