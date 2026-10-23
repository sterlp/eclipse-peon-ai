package org.sterl.llmpeon.shared;

import java.time.Duration;

import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.model.SimpleMessage.Type;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

public interface AiMonitor {
    record AiFileUpdate(String file, String oldContent, String newContent) {}

    public static final AiMonitor NULL_MONITOR = new AiMonitor() {
        @Override
        public void onChatResponse(SimpleMessage m) {}
    };

    public static AiMonitor nullSafety(AiMonitor in) {
        return in == null ? NULL_MONITOR : in;
    }

    /** Fired on every streaming token (thinking, answer, or tool). Default is a no-op. */
    default void onStreamingChunk(OnPartialAiResponse r) {}

    /** Called once before the tool loop starts. userMessage may be null (autonomous re-trigger). */
    default void onCallStart(String userMessage) {}

    /** Called before each LLM request within the tool loop. iteration starts at 1. The builder can be mutated. */
    default void onChatMessage(int iteration, ChatRequest.Builder request) {}

    /** A message to display in the chat (AI text, tool call result, error, etc.). */
    void onChatResponse(SimpleMessage message);

    /** Called once after the tool loop ends — includes the final response and total duration. */
    default void onCallCompleted(ChatResponse response, Duration duration) {}

    /**
     * Fired for every completed LLM response that carried real token usage — main loop, search
     * sub-agent and compaction all funnel through here. Never fired for estimated/missing usage.
     * Default is a no-op.
     */
    default void onTokenUsage(TokenUsage usage) {}

    /**
     * Fired exactly once after a compact that ran — {@code COMPACTED} or {@code FAILED_EMPTY}. Never
     * for {@code SKIPPED_SMALL} (nothing ran) and never when the compact throws, and always after the
     * agent released its working state, so a refresh in the reaction sees an idle agent (R-CT-7).
     * <p>
     * The agent <b>name</b> (not the instance) is all a listener needs to decide whether it is
     * affected — e.g. the chat view rebuilds only for its active agent. Arrives on the caller's
     * thread: UI updates need the UI-thread hop.
     */
    default void onCompactCompleted(String agentName, CompactResult result) {}

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
