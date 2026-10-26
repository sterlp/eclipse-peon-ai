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

    /**
     * Called once per turn, <b>after</b> the turn's user message entered the memory and before the tool
     * loop starts — the earliest point at which a rebuild from the memory cannot lose that message (the
     * pre-turn compact R-CC-20.2 runs before it). userMessage may be null (autonomous re-trigger).
     */
    default void onCallStart(String userMessage) {}

    /** Called before each LLM request within the tool loop. iteration starts at 1. The builder can be mutated. */
    default void onChatMessage(int iteration, ChatRequest.Builder request) {}

    /** A message to display in the chat (AI text, tool call result, error, etc.). */
    void onChatResponse(SimpleMessage message);

    /** Called once after the tool loop ends — includes the final response and total duration. */
    default void onCallCompleted(ChatResponse response, Duration duration) {}

    /**
     * {@link #onCallCompleted(ChatResponse, Duration)} of one <b>named</b> agent — fired by the agent
     * that finished, with its own {@code org.sterl.llmpeon.agent.AiAgent#getName()}, so a listener never
     * has to guess whose turn ended. A delegated member announces itself; a viewer that shows one
     * conversation decides whether a nested member's completion means anything to it.
     * <p>
     * Fired at the same point as {@link #onCallCompleted} and never instead of it: a listener that only
     * wants today's behaviour keeps listening to {@code onCallCompleted} and is untouched.
     */
    default void onAgentCallCompleted(String agentName, ChatResponse response, Duration duration) {}

    /**
     * The final answer a delegation handed back to the delegating agent — the pure text the member
     * produced, not the status line the model additionally receives. A fact, not "the member's last
     * answer": only a delegation that actually returned an answer fires it, and a member that was asked
     * twice fires it twice.
     * <p>
     * {@code agentName} is the member's core id ({@code org.sterl.llmpeon.agent.AiAgent#getName()});
     * display names are not core knowledge.
     */
    default void onDelegatedAnswer(String agentName, String answer) {}

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
     * <p>
     * {@code withinTurn} says the agent's working flag was already held when the compact ran — a turn
     * owned it, so the compact ran inside that turn (pre-turn, in-loop, from a tool). A listener that
     * rebuilds from the memory must then wait for that turn's {@link #onCallStart}: before it, the
     * turn's fresh message is not in the memory yet and a rebuild would drop it (R-CT-7). The flag
     * could also mean a parallel compact of the same agent; the UI excludes that case.
     */
    default void onCompactCompleted(String agentName, CompactResult result, boolean withinTurn) {}

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
