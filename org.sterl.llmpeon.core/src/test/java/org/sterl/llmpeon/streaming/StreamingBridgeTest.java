package org.sterl.llmpeon.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.OnPartialAiResponse;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;

/**
 * One {@link StreamingBridge} is created per turn and reused across all tool-loop
 * iterations, so {@code startedAt} must span the whole turn — the UI "working since Xs"
 * must not jump on every tool-loop LLM call. The per-call {@code tokenPhaseStart} and the
 * four {@link org.sterl.llmpeon.shared.Timer}s must reset per call. See docs/streaming-display.md.
 */
class StreamingBridgeTest {

    static final Instant T0 = Instant.parse("2026-09-04T10:00:00Z");

    /** Fixed clock the test can advance between the two calls of one turn. */
    static final class MutableClock extends Clock {
        private Instant instant = T0;
        void advance(Duration d) { instant = instant.plus(d); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    /** Records the START chunk's startedAt of each call. */
    static final class StartRecorder implements AiMonitor {
        final List<Instant> starts = new ArrayList<>();
        @Override public void onChatResponse(SimpleMessage m) {}
        @Override public void onStreamingChunk(OnPartialAiResponse r) {
            if (r.type() == OnPartialAiResponse.Type.START) starts.add(r.startedAt());
        }
    }

    /** Records every chunk of every call, in order. */
    static final class ChunkRecorder implements AiMonitor {
        final List<OnPartialAiResponse> chunks = new ArrayList<>();
        @Override public void onChatResponse(SimpleMessage m) {}
        @Override public void onStreamingChunk(OnPartialAiResponse r) { chunks.add(r); }
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void startedAtSpansWholeTurn() {
        // GIVEN a bridge whose clock is fixed at the turn start T0
        var clock = new MutableClock();
        var bridge = new StreamingBridge(clock);
        var model = completeModel();
        var monitor = new StartRecorder();
        var request = new ChatRequest.Builder().messages(UserMessage.from("hi")).build();

        // WHEN the first LLM call of the turn runs
        bridge.call(model, request, monitor);
        // THEN its START chunk carries the turn start
        assertThat(monitor.starts.get(0)).isEqualTo(T0);

        // WHEN the turn has been running for an hour and a tool-loop iteration triggers call 2
        clock.advance(Duration.ofHours(1));
        bridge.call(model, request, monitor);

        // THEN startedAt is still the turn start — it must NOT jump to call 2's start
        assertThat(monitor.starts.get(1)).isEqualTo(T0);
    }

    @Test
    void startChunkTokenPhaseStartIsZero() {
        var clock = new MutableClock();
        var bridge = new StreamingBridge(clock);
        var monitor = new ChunkRecorder();

        bridge.call(answerModel(clock, Duration.ZERO, Duration.ZERO), request("hi"), monitor);

        // THEN the START chunk is first and its token phase has not started yet
        assertThat(monitor.chunks.get(0).type()).isEqualTo(OnPartialAiResponse.Type.START);
        assertThat(monitor.chunks.get(0).tokenPhaseStart()).isZero();
    }

    @Test
    void partialChunkTokenPhaseStartIsCallStartNotTurnStart() {
        var clock = new MutableClock();
        var bridge = new StreamingBridge(clock); // turn start = T0
        var monitor = new ChunkRecorder();

        // WHEN the call starts one hour after the turn began
        clock.advance(Duration.ofHours(1));
        bridge.call(answerModel(clock, Duration.ZERO, Duration.ZERO), request("hi"), monitor);

        // THEN the partial's tokenPhaseStart is the call start (T0+1h), NOT the turn start (T0)
        var partial = monitor.chunks.get(1);
        assertThat(partial.type()).isEqualTo(OnPartialAiResponse.Type.ANSWER);
        assertThat(partial.tokenPhaseStart()).isEqualTo(clock.millis());
        assertThat(partial.tokenPhaseStart()).isNotEqualTo(partial.startedAt().toEpochMilli());
    }

    @Test
    void tokenPhaseStartResetsPerCall() {
        var clock = new MutableClock();
        var bridge = new StreamingBridge(clock);
        var monitor = new ChunkRecorder();

        // WHEN the same bridge runs two calls an hour apart
        bridge.call(answerModel(clock, Duration.ZERO, Duration.ZERO), request("hi"), monitor);
        long first = monitor.chunks.get(1).tokenPhaseStart();
        clock.advance(Duration.ofHours(1));
        bridge.call(answerModel(clock, Duration.ZERO, Duration.ZERO), request("hi"), monitor);
        long second = monitor.chunks.get(4).tokenPhaseStart();

        // THEN each call stamped its own call start — call 2's is not call 1's
        assertThat(second).isNotEqualTo(first);
        assertThat(second).isEqualTo(first + Duration.ofHours(1).toMillis());
    }

    @Test
    void promptAndTokenTimersStopAfterCall() {
        var clock = new MutableClock();
        var bridge = new StreamingBridge(clock);
        var monitor = new ChunkRecorder();

        // WHEN a call streams a 30s prompt phase then a 20s token phase
        bridge.call(answerModel(clock, Duration.ofSeconds(30), Duration.ofSeconds(20)), request("hi"), monitor);

        // THEN the prompt timer froze at 30s and the token timer at 20s, both stopped
        assertThat(bridge.ppTimer().millis()).isEqualTo(30_000);
        assertThat(bridge.ppTimer().running()).isFalse();
        assertThat(bridge.tokenTimer().millis()).isEqualTo(20_000);
        assertThat(bridge.tokenTimer().running()).isFalse();
        // tokenPhaseStart was stamped at the first partial (T0 + 30s)
        assertThat(monitor.chunks.get(1).tokenPhaseStart()).isEqualTo(clock.millis() - 20_000);
    }

    @Test
    void promptTimerRestartsPerCall() {
        var clock = new MutableClock();
        var bridge = new StreamingBridge(clock);
        var monitor = new ChunkRecorder();

        // WHEN call 1 has a 30s prompt phase, then call 2 (an hour later) has a 10s prompt phase
        bridge.call(answerModel(clock, Duration.ofSeconds(30), Duration.ofSeconds(20)), request("hi"), monitor);
        clock.advance(Duration.ofHours(1));
        bridge.call(answerModel(clock, Duration.ofSeconds(10), Duration.ofSeconds(5)), request("hi"), monitor);

        // THEN the prompt timer reflects only call 2's 10s — not call 1's 30s, not accumulated
        assertThat(bridge.ppTimer().millis()).isEqualTo(10_000);
    }

    @Test
    void thinkTimerStopsAtFirstThinking() {
        var clock = new MutableClock();
        var bridge = new StreamingBridge(clock);
        var monitor = new ChunkRecorder();

        // WHEN the model emits a thinking partial after a 15s prompt phase
        bridge.call(thinkModel(clock, Duration.ofSeconds(15), Duration.ofSeconds(10)), request("hi"), monitor);

        // THEN the think timer froze at 15s (call start → first thinking) and stopped
        assertThat(bridge.thinkTimer().millis()).isEqualTo(15_000);
        assertThat(bridge.thinkTimer().running()).isFalse();
    }

    @Test
    void thinkTimerKeepsRunningWithoutThinking() {
        var clock = new MutableClock();
        var bridge = new StreamingBridge(clock);
        var monitor = new ChunkRecorder();

        // WHEN a call streams an answer without any thinking
        bridge.call(answerModel(clock, Duration.ofSeconds(5), Duration.ofSeconds(5)), request("hi"), monitor);

        // THEN the think timer was never stopped
        assertThat(bridge.thinkTimer().running()).isTrue();
    }

    @Test
    void toolChunkValueIsPartialArgumentsNotName() {
        var clock = new MutableClock();
        var bridge = new StreamingBridge(clock);
        var monitor = new ChunkRecorder();

        bridge.call(toolModel(clock, Duration.ZERO, Duration.ZERO), request("hi"), monitor);

        // THEN the TOOL chunk carries the argument delta, not the repeated tool name
        var tool = monitor.chunks.get(1);
        assertThat(tool.type()).isEqualTo(OnPartialAiResponse.Type.TOOL);
        assertThat(tool.value()).isEqualTo("{\"q\":\"hi\"}");
    }

    @Test
    void totalTimerRunsThroughBridgeLifetime() {
        var clock = new MutableClock();
        var bridge = new StreamingBridge(clock);
        var monitor = new ChunkRecorder();
        clock.advance(Duration.ofSeconds(45));

        // WHEN a full call runs
        bridge.call(answerModel(clock, Duration.ZERO, Duration.ZERO), request("hi"), monitor);

        // THEN the total timer still runs — it is never stopped (no turn-end signal)
        assertThat(bridge.totalTimer().running()).isTrue();
        assertThat(bridge.totalTimer().millis()).isGreaterThanOrEqualTo(45_000);
    }

    // -------------------------------------------------------------------------
    // Model mocks — fire partial callbacks synchronously on the calling thread
    // -------------------------------------------------------------------------

    private ChatRequest request(String text) {
        return new ChatRequest.Builder().messages(UserMessage.from(text)).build();
    }

    private StreamingChatModel completeModel() {
        var cm = mock(StreamingChatModel.class);
        doAnswer(inv -> {
            inv.getArgument(1, StreamingChatResponseHandler.class)
                .onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());
            return null;
        }).when(cm).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        return cm;
    }

    private StreamingChatModel answerModel(MutableClock clock, Duration prompt, Duration token) {
        var cm = mock(StreamingChatModel.class);
        doAnswer(inv -> {
            var h = inv.getArgument(1, StreamingChatResponseHandler.class);
            var handle = mock(StreamingHandle.class);
            clock.advance(prompt);
            h.onPartialResponse(new PartialResponse("hi"), new PartialResponseContext(handle));
            clock.advance(token);
            h.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("done")).build());
            return null;
        }).when(cm).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        return cm;
    }

    private StreamingChatModel thinkModel(MutableClock clock, Duration prompt, Duration token) {
        var cm = mock(StreamingChatModel.class);
        doAnswer(inv -> {
            var h = inv.getArgument(1, StreamingChatResponseHandler.class);
            var handle = mock(StreamingHandle.class);
            clock.advance(prompt);
            h.onPartialThinking(new PartialThinking("hmm"), new PartialThinkingContext(handle));
            clock.advance(token);
            h.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("done")).build());
            return null;
        }).when(cm).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        return cm;
    }

    private StreamingChatModel toolModel(MutableClock clock, Duration prompt, Duration token) {
        var cm = mock(StreamingChatModel.class);
        doAnswer(inv -> {
            var h = inv.getArgument(1, StreamingChatResponseHandler.class);
            var handle = mock(StreamingHandle.class);
            clock.advance(prompt);
            h.onPartialToolCall(
                PartialToolCall.builder().name("search").partialArguments("{\"q\":\"hi\"}").build(),
                new PartialToolCallContext(handle));
            clock.advance(token);
            h.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("done")).build());
            return null;
        }).when(cm).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        return cm;
    }
}
