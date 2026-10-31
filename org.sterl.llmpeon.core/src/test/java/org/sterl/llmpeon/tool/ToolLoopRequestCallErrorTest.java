package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.time.Instant;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
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
 * own endpoint must show up in a failure whose message says nothing (blank, or just its cause's
 * class name), a cancel and an informative error must pass through untouched.
 */
class ToolLoopRequestCallErrorTest {

    private static final String BASE_URL = "http://base-host:11434";
    private static final String AGENT_URL = "http://agent-host:9999";

    private static final ChatRequest REQUEST = ChatRequest.builder().messages(UserMessage.from("hi")).build();

    /** The real choke-point wiring: base config plus an agent carrying its own url. */
    private static ToolLoopRequest requestThrowing(RuntimeException error) {
        return requestThrowing(BASE_URL, AgentConfig.builder().provider(AiProvider.OLLAMA).url(AGENT_URL).model("test-model").build(), error);
    }

    private static ToolLoopRequest requestThrowing(String baseUrl, AgentConfig agentConfig, RuntimeException error) {
        var chatModel = LlmConfig.newConfig(AiProvider.OLLAMA, "test-model", baseUrl).build();
        return ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(chatModel)
                .owner(LoopOwner.named(null, base -> agentConfig))
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

    // UC-AEM-4
    @Test
    void messageLessExceptionWithoutCauseOmitsCauseSuffix() {
        var request = requestThrowing(new RuntimeException());

        var thrown = assertThrows(RuntimeException.class, () -> request.call(REQUEST));

        assertThat(thrown.getMessage())
                .isEqualTo("AI call to " + AGENT_URL + " failed: RuntimeException")
                .doesNotContain("—");
    }

    // UC-AEM-4
    @Test
    void causeMessageIsRenderedAsFirstLineOnly() {
        var original = new RuntimeException();
        original.initCause(new IOException("boom\nand a second line"));
        var request = requestThrowing(original);

        var thrown = assertThrows(RuntimeException.class, () -> request.call(REQUEST));

        assertThat(thrown.getMessage())
                .isEqualTo("AI call to " + AGENT_URL + " failed: RuntimeException — IOException: boom");
    }

    // UC-AEM-4
    @Test
    void unresolvableUrlIsNamedAsNoUrlConfigured() {
        var original = new RuntimeException();
        original.initCause(new ClosedChannelException());
        var request = requestThrowing("", AgentConfig.builder().provider(AiProvider.OLLAMA).model("test-model").build(), original);

        var thrown = assertThrows(RuntimeException.class, () -> request.call(REQUEST));

        assertThat(thrown.getMessage())
                .startsWith("AI call to (no url configured) failed: RuntimeException")
                .doesNotContain(AGENT_URL);
    }

    // UC-AEM-7
    @Test
    void envelopeWithCauseClassNameIsEnriched() {
        // the production shape — nothing hand-made: JDK CompletableFuture envelope whose message is
        // its cause's class name, over a cause that itself has no message.
        var closed = new ClosedChannelException();
        var connect = new ConnectException();
        connect.initCause(closed);
        var envelope = new CompletionException(connect);
        var request = requestThrowing(envelope);

        var thrown = assertThrows(RuntimeException.class, () -> request.call(REQUEST));

        assertThat(thrown.getMessage())
                .isEqualTo("AI call to " + AGENT_URL + " failed: CompletionException — ConnectException")
                .doesNotContain(BASE_URL);
        assertThat(thrown.getCause()).isSameAs(envelope);
        assertThat(envelope.getCause()).isSameAs(connect);
        assertThat(connect.getCause()).isSameAs(closed);
    }

    // UC-AEM-7
    @Test
    void productionEnvelopeSurvivesTheRealBridgeAndRetryLayers() {
        // The shape the real stack reports: JdkHttpClient relays the failure through
        // StreamingChatResponseHandler.onError as a JDK CompletionException whose message is nothing
        // but its cause's class name. Real StreamingBridge + real ApiRetry here, so their pass-through
        // is part of the proof, not assumed. The endpoint comes from the base config on purpose: an
        // agent url override is not the base connection and ConfiguredChatModel would then build a
        // real Ollama model instead of handing the fake to the bridge.
        var jdkEnvelope = new CompletionException(new ConnectException());
        var request = ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(new ConfiguredChatModel(
                        LlmConfig.newConfig(AiProvider.OLLAMA, "test-model", AGENT_URL),
                        new StreamMock().buildMock(chatRequest -> { throw jdkEnvelope; })))
                .owner(LoopOwner.named(null, base -> AgentConfig.builder().provider(AiProvider.OLLAMA).model("test-model").build()))
                .build();

        var thrown = assertThrows(RuntimeException.class, () -> request.call(REQUEST));

        assertThat(thrown.getMessage())
                .isEqualTo("AI call to " + AGENT_URL + " failed: CompletionException — ConnectException")
                .doesNotContain(BASE_URL);
        assertThat(thrown.getCause()).isSameAs(jdkEnvelope);
    }

    // UC-AEM-6
    @Test
    void messageThatOnlyContainsTheCauseClassNameStaysUntouched() {
        // containing the class name is not being it — this message carries information, so no wrap.
        // without the cause this test could not tell equals from contains.
        var withText = new RuntimeException("connect to java.net.ConnectException failed");
        withText.initCause(new ConnectException());
        var request = requestThrowing(withText);

        var thrown = assertThrows(RuntimeException.class, () -> request.call(REQUEST));

        assertThat(thrown).isSameAs(withText);
        assertThat(thrown.getMessage()).isEqualTo("connect to java.net.ConnectException failed");
    }
}
