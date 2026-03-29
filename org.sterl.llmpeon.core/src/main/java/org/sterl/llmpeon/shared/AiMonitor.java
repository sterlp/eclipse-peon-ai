package org.sterl.llmpeon.shared;

import java.time.Duration;

import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.model.SimpleMessage.Type;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

public interface AiMonitor {
    record AiFileUpdate(String file, String oldContent, String newContent) {}

    public static final AiMonitor NULL_MONITOR = new AiMonitor() {
        @Override
        public void onChatResponse(SimpleMessage m) {}
    };

    public static AiMonitor nullSafety(AiMonitor in) {
        return in == null ? NULL_MONITOR : in;
    }

    /** Called once before the tool loop starts. userMessage may be null (autonomous re-trigger). */
    default void onCallStart(String userMessage) {}

    /** Called before each LLM request within the tool loop. iteration starts at 1. The builder can be mutated. */
    default void onChatMessage(int iteration, ChatRequest.Builder request) {}

    /** A message to display in the chat (AI text, tool call result, error, etc.). */
    void onChatResponse(SimpleMessage message);

    /** Called once after the tool loop ends — includes the final response and total duration. */
    default void onCallCompleted(ChatResponse response, Duration duration) {}

    default void onTool(String message) {
        onChatResponse(new SimpleMessage(Type.TOOL, message));
    }

    default void onFileUpdate(AiFileUpdate update) {
        onChatResponse(new SimpleMessage(Type.TOOL, "Updated: " + update.file()));
    }

    default void onProblem(String message) {
        onChatResponse(new SimpleMessage(Type.PROBLEM, message));
    }

    default boolean isCanceled() {
        return false;
    }
}
