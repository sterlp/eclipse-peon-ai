package org.sterl.llmpeon.tool;

import java.util.List;

import org.sterl.llmpeon.shared.AiMonitor;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;

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

    void withChatModel(ChatModel chatModel);
    
    void withMemory(List<ChatMessage> memory);
}
