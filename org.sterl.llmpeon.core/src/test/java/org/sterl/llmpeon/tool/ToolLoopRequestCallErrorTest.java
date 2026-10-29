package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.channels.ClosedChannelException;
import java.time.Instant;
import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.streaming.StreamingBridge;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * R-AEM-3 (org.sterl.llmpeon.core/docs/agent-error-memory.md) at the AI-call choke-point: the agent's
 * own endpoint must show up in a message-less failure, a cancel and an already-messaged error must
 * pass through untouched.
 */
class ToolLoopRequestCallErrorTest {

    private static final String BASE_URL = "http://base-host:11434";
    private static final String AGENT_URL = "http://agent-host:9999";

    private static final ChatRequest REQUEST = ChatRequest.builder().messages(UserMessage.from("hi")).build();

    /** The real choke-point wiring: base config plus an agent carrying its own url. */
    private static ToolLoopRequest requestThrowing(RuntimeException error) {
        var chatModel = LlmConfig.newConfig(AiProvider.OLLAMA, "test-model", BASE_URL).build();
        return ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(chatModel)
                .agentConfig(AgentConfig.builder().provider(AiProvider.OLLAMA).url(AGENT_URL).model("test-model").build())
                .bridge(new StreamingBridge() {
                    @Override
                    public ChatResponse call(StreamingChatModel model, ChatRequest request, AiMonitor monitor, Instant startedAt) {
                        throw error;
                    }
                })
                .build();
    }

    // UC-AEM-4
    @Test
    void messageLessExceptionNamesUrlAndClass() {
        // initCause, not the (cause) constructor: that one would give the exception a message already.
        var original = new RuntimeException();
        original.initCause(new ClosedChannelException());
        var request = requestThrowing(original);

        var thrown = assertThrows(RuntimeException.class, () -> request.call(REQUEST));

        assertThat(thrown.getMessage())
                .isEqualTo("AI call to " + AGENT_URL + " failed: RuntimeException — ClosedChannelException")
                .doesNotContain(BASE_URL);
        assertThat(thrown.getCause()).isSameAs(original);
    }

    // UC-AEM-5
    @Test
    void cancellationIsNeverWrapped() {
        var canceled = new CancellationException();
        var request = requestThrowing(canceled);

        assertThat(assertThrows(RuntimeException.class, () -> request.call(REQUEST))).isSameAs(canceled);
        assertThat(canceled.getMessage()).isNull();
    }

    // UC-AEM-6
    @Test
    void exceptionWithMessageUntouched() {
        var withMessage = new RuntimeException("connection refused");
        var request = requestThrowing(withMessage);

        var thrown = assertThrows(RuntimeException.class, () -> request.call(REQUEST));

        assertThat(thrown).isSameAs(withMessage);
        assertThat(thrown.getMessage()).isEqualTo("connection refused").doesNotStartWith("AI call to");
    }
}
