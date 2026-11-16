package org.sterl.llmpeon.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.exception.ExceptionUtil;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.OnPartialAiResponse;
import org.sterl.llmpeon.streaming.ApiRetry;
import org.sterl.llmpeon.streaming.StreamingBridge;
import org.sterl.llmpeon.tool.ToolLoopRequest;
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
import dev.langchain4j.model.output.TokenUsage;

class StreamingBridgeTest {

    static final Instant T0 = Instant.parse("2026-09-04T10:00:00Z");

    static final class MutableClock extends Clock {
        private Instant instant = T0;
        void advance(Duration d) { instant = instant.plus(d); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    static final class StartRecorder implements AiMonitor {
        final List<Instant> starts = new ArrayList<>();
        @Override public void onChatResponse(SimpleMessage m) {}
        @Override public void onStreamingChunk(OnPartialAiResponse r) {
            if (r.type() == OnPartialAiResponse.Type.START) starts.add(r.startedAt());
        }
    }

    static final class ChunkRecorder implements AiMonitor {
        final List<OnPartialAiResponse> chunks = new ArrayList<>();
        @Override public void onChatResponse(SimpleMessage m) {}
        @Override public void onStreamingChunk(OnPartialAiResponse r) { chunks.add(r); }
    }

    // -------------------------------------------------------------------------
    // Existing tests — unchanged
    // -------------------------------------------------------------------------

    @Test
    void startedAtIsWhatTheCallerPasses() {
        var clock = new MutableClock();
        var bridge = new StreamingBridge(clock);
        var model = completeModel();
        var monitor = new StartRecorder();
        var request = new ChatRequest.Builder().messages(UserMessage.from("hi")).build();
        var turnStart = T0.minus(Duration.ofDays(1));

        bridge.call(model, request, monitor, turnStart);
        clock.advance(Duration.ofHours(1));
        bridge.call(model, request, monitor, turnStart);
        var otherTurn = T0.minus(Duration.ofDays(2));
        bridge.call(model, request, monitor, otherTurn);

        assertThat(monitor.starts).containsExactly(turnStart, turnStart, otherTurn);
    }

    @Test
    void startChunkTokenPhaseStartIsZero() {
        var clock = new MutableClock();
        var bridge = new StreamingBridge(clock);
        var monitor = new ChunkRecorder();

        bridge.call(answerModel(clock, Duration.ZERO, Duration.ZERO), request("hi"), monitor, T0);

        assertThat(monitor.chunks.get(0).type()).isEqualTo(OnPartialAiResponse.Type.START);
        assertThat(monitor.chunks.get(0).tokenPhaseStart()).isZero();
    }

    @Test
    void partialChunkTokenPhaseStartIsCallStartNotTurnStart() {
        var clock = new MutableClock();
        var bridge = new StreamingBridge(clock);
        var monitor = new ChunkRecorder();

        clock.advance(Duration.ofHours(1));
        bridge.call(answerModel(clock, Duration.ZERO, Duration.ZERO), request("hi"), monitor, T0);

        var partial = monitor.chunks.get(1);
        assertThat(partial.type()).isEqualTo(OnPartialAiResponse.Type.ANSWER);
        assertThat(partial.startedAt()).isEqualTo(T0);
        assertThat(partial.tokenPhaseStart()).isEqualTo(clock.millis());
        assertThat(partial.tokenPhaseStart()).isNotEqualTo(partial.startedAt().toEpochMilli());
    }

    @Test
    void tokenPhaseStartResetsPerCall() {
        var clock = new MutableClock();
        var bridge = new StreamingBridge(clock);
        var monitor = new ChunkRecorder();

        bridge.call(answerModel(clock, Duration.ZERO, Duration.ZERO), request("hi"), monitor, T0);
        long first = monitor.chunks.get(1).tokenPhaseStart();
        clock.advance(Duration.ofHours(1));
        bridge.call(answerModel(clock, Duration.ZERO, Duration.ZERO), request("hi"), monitor, T0);
        long second = monitor.chunks.get(4).tokenPhaseStart();

        assertThat(second).isNotEqualTo(first);
        assertThat(second).isEqualTo(first + Duration.ofHours(1).toMillis());
    }

    @Test
    void promptAndTokenTimersStopAfterCall() {
        var clock = new MutableClock();
        var bridge = new StreamingBridge(clock);
        var monitor = new ChunkRecorder();

        bridge.call(answerModel(clock, Duration.ofSeconds(30), Duration.ofSeconds(20)), request("hi"), monitor, T0);

        assertThat(bridge.ppTimer().millis()).isEqualTo(30_000);
        assertThat(bridge.ppTimer().running()).isFalse();
        assertThat(bridge.tokenTimer().millis()).isEqualTo(20_000);
        assertThat(bridge.tokenTimer().running()).isFalse();
        assertThat(monitor.chunks.get(1).tokenPhaseStart()).isEqualTo(clock.millis() - 20_000);
    }

    @Test
    void promptTimerRestartsPerCall() {
        var clock = new MutableClock();
        var bridge = new StreamingBridge(clock);
        var monitor = new ChunkRecorder();

        bridge.call(answerModel(clock, Duration.ofSeconds(30), Duration.ofSeconds(20)), request("hi"), monitor, T0);
        clock.advance(Duration.ofHours(1));
        bridge.call(answerModel(clock, Duration.ofSeconds(10), Duration.ofSeconds(5)), request("hi"), monitor, T0);

        assertThat(bridge.ppTimer().millis()).isEqualTo(10_000);
    }

    @Test
    void thinkTimerStopsAtFirstThinking() {
        var clock = new MutableClock();
        var bridge = new StreamingBridge(clock);
        var monitor = new ChunkRecorder();

        bridge.call(thinkModel(clock, Duration.ofSeconds(15), Duration.ofSeconds(10)), request("hi"), monitor, T0);

        assertThat(bridge.thinkTimer().millis()).isEqualTo(15_000);
        assertThat(bridge.thinkTimer().running()).isFalse();
    }

    @Test
    void thinkTimerKeepsRunningWithoutThinking() {
        var clock = new MutableClock();
        var bridge = new StreamingBridge(clock);
        var monitor = new ChunkRecorder();

        bridge.call(answerModel(clock, Duration.ofSeconds(5), Duration.ofSeconds(5)), request("hi"), monitor, T0);

        assertThat(bridge.thinkTimer().running()).isTrue();
    }

    @Test
    void toolChunkValueIsPartialArgumentsNotName() {
        var clock = new MutableClock();
        var bridge = new StreamingBridge(clock);
        var monitor = new ChunkRecorder();

        bridge.call(toolModel(clock, Duration.ZERO, Duration.ZERO), request("hi"), monitor, T0);

        var tool = monitor.chunks.get(1);
        assertThat(tool.type()).isEqualTo(OnPartialAiResponse.Type.TOOL);
        assertThat(tool.value()).isEqualTo("{\"q\":\"hi\"}");
    }

    @Test
    void bug_onErrorAfterCancelMasksCancellation() {
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public boolean isCanceled() { return true; }
        };
        var cm = mock(StreamingChatModel.class);
        doAnswer(inv -> {
            var h = inv.getArgument(1, StreamingChatResponseHandler.class);
            var handle = mock(StreamingHandle.class);
            h.onPartialResponse(new PartialResponse("hi"), new PartialResponseContext(handle));
            h.onError(new RuntimeException("provider error after cancel"));
            return null;
        }).when(cm).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        var bridge = new StreamingBridge(new MutableClock());

        assertThatThrownBy(() -> bridge.call(cm, request("hi"), monitor, T0))
                .isInstanceOf(AiCancellationException.class);
    }

    // -------------------------------------------------------------------------
    // UC-AEM-8 — idle timeout
    // -------------------------------------------------------------------------

    enum Uc8Input { TOOL_LOOP, CALL_BLOCKING }

    // UC-AEM-8
    @ParameterizedTest
    @EnumSource(Uc8Input.class)
    @Timeout(value = 2, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void silentStreamTimesOut(Uc8Input input) {
        var model = mock(StreamingChatModel.class);
        var cancelCount = new AtomicInteger();
        doAnswer(inv -> {
            var h = inv.getArgument(1, StreamingChatResponseHandler.class);
            var handle = mock(StreamingHandle.class);
            doAnswer(c -> { cancelCount.incrementAndGet(); return null; }).when(handle).cancel();
            h.onPartialResponse(new PartialResponse("first"), new PartialResponseContext(handle));
            return null;
        }).when(model).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        var config = LlmConfig.builder().providerType(org.sterl.llmpeon.ai.AiProvider.OPEN_AI).model("test").timeout(Duration.ofMillis(200)).build();

        Throwable thrown = null;
        if (input == Uc8Input.CALL_BLOCKING) {
            var chatModel = new ConfiguredChatModel(config, model);
            try {
                chatModel.callBlocking(new ChatRequest.Builder().messages(UserMessage.from("hi")).build(), null, AiMonitor.NULL_MONITOR, Instant.now());
            } catch (Throwable t) { thrown = t; }
        } else {
            var chatModel = new ConfiguredChatModel(config, model);
            var memory = new ThreadSafeMemory();
            memory.add(UserMessage.from("hi"));
            var request = ToolLoopRequest.builder().memory(memory).chatModel(chatModel).build();
            try {
                request.call(new ChatRequest.Builder().messages(UserMessage.from("hi")).build());
            } catch (Throwable t) { thrown = t; }
        }

        assertThat(thrown).isInstanceOf(RuntimeException.class);
        assertThat(thrown).hasMessage("AI stream idle for 200 ms (timeout)");
        assertThat(thrown.getCause()).isInstanceOf(TimeoutException.class);
        assertThat(ExceptionUtil.isCanceled(thrown)).isFalse();
        assertThat(cancelCount.get()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // UC-AEM-9 — active stream does not time out
    // -------------------------------------------------------------------------

    // UC-AEM-9
    @ParameterizedTest
    @EnumSource(value = OnPartialAiResponse.Type.class, names = {"ANSWER", "THINK", "TOOL"})
    @Timeout(value = 2, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void activeStreamDoesNotTimeOut(OnPartialAiResponse.Type chunkType) {

        var clock = new MutableClock();
        var monitor = new ChunkRecorder();
        var handlerRef = new AtomicReference<StreamingChatResponseHandler>();
        var handle = mock(StreamingHandle.class);
        var cancelCount = new AtomicInteger();
        doAnswer(c -> { cancelCount.incrementAndGet(); return null; }).when(handle).cancel();

        var model = mock(StreamingChatModel.class);
        doAnswer(inv -> {
            handlerRef.set(inv.getArgument(1, StreamingChatResponseHandler.class));
            return null;
        }).when(model).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        var bridge = new StreamingBridge(clock, Duration.ofMillis(200)) {
            private int poll = 0;
            @Override
            boolean awaitCompletion(long waitMillis) {
                poll++;
                var h = handlerRef.get();
                if (poll <= 3) {
                    // Each poll: advance 100 ms, fire two chunks of the given type
                    clock.advance(Duration.ofMillis(100));
                    fireChunk(h, chunkType, handle, "c" + poll + "a");
                    clock.advance(Duration.ofMillis(100));
                    fireChunk(h, chunkType, handle, "c" + poll + "b");
                    assertThat(waitMillis).isEqualTo(200);
                    return false;
                }
                // poll 4: complete
                clock.advance(Duration.ofMillis(50));
                h.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("done")).build());
                return true;
            }
        };

        var result = bridge.call(model, request("hi"), monitor, T0);

        assertThat(result.aiMessage().text()).isEqualTo("done");
        assertThat(monitor.chunks).hasSize(8); // START + 6 partials + END
        for (int i = 1; i <= 6; i++) {
            assertThat(monitor.chunks.get(i).type()).isEqualTo(chunkType);
        }
        assertThat(monitor.chunks.get(7).type()).isEqualTo(OnPartialAiResponse.Type.END);
        assertThat(cancelCount.get()).isZero();
    }

    private void fireChunk(StreamingChatResponseHandler h, OnPartialAiResponse.Type type, StreamingHandle handle, String text) {
        switch (type) {
            case ANSWER -> h.onPartialResponse(new PartialResponse(text), new PartialResponseContext(handle));
            case THINK -> h.onPartialThinking(new PartialThinking(text), new PartialThinkingContext(handle));
            case TOOL -> h.onPartialToolCall(
                    PartialToolCall.builder().name("s").partialArguments("{\"t\":\"" + text + "\"}").build(),
                    new PartialToolCallContext(handle));
        }
    }

    // -------------------------------------------------------------------------
    // B4 — fractional timeout rounds up
    // -------------------------------------------------------------------------

    @Test
    @Timeout(value = 2, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void fractionalTimeoutRoundsUp() {
        // Verify rounding directly: 1.5 ms → 2 ms
        var bridge = new StreamingBridge(Duration.ofNanos(1_500_000));
        // timeoutMillis is private — tested via behaviour

        var clock = new MutableClock();
        var model = mock(StreamingChatModel.class);
        doAnswer(inv -> { return null; }).when(model).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        var waitVals = new ArrayList<Long>();
        var bridge2 = new StreamingBridge(clock, Duration.ofNanos(1_500_000)) {
            @Override
            boolean awaitCompletion(long waitMillis) {
                waitVals.add(waitMillis);
                clock.advance(Duration.ofMillis(1));
                return false;
            }
        };

        Throwable thrown = null;
        try {
            bridge2.call(model, request("hi"), AiMonitor.NULL_MONITOR, T0);
        } catch (Throwable t) { thrown = t; }

        assertThat(thrown).isInstanceOf(RuntimeException.class);
        assertThat(thrown).hasMessage("AI stream idle for 2 ms (timeout)");
        assertThat(thrown.getCause()).isInstanceOf(TimeoutException.class);
        assertThat(waitVals.get(0)).isEqualTo(2); // initial budget must be 2 ms (rounded up from 1.5)
    }

    // -------------------------------------------------------------------------
    // Stop before the idle deadline stays a cancellation
    // -------------------------------------------------------------------------

    @Test
    @Timeout(value = 2, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void stopBeforeIdleDeadlineRemainsCancellation() {
        var clock = new MutableClock();
        var monitorCanceled = new AtomicReference<Boolean>(false);
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public boolean isCanceled() { return monitorCanceled.get(); }
        };
        var model = mock(StreamingChatModel.class);
        doAnswer(inv -> { return null; }).when(model).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        var bridge = new StreamingBridge(clock, Duration.ofMillis(500)) {
            @Override
            boolean awaitCompletion(long waitMillis) {
                clock.advance(Duration.ofMillis(100));
                monitorCanceled.set(true);
                return false;
            }
        };

        Throwable thrown = null;
        try {
            bridge.call(model, request("hi"), monitor, T0);
        } catch (Throwable t) { thrown = t; }

        assertThat(thrown).isInstanceOf(AiCancellationException.class);
    }

    // -------------------------------------------------------------------------
    // Old callbacks of a timed-out attempt never reach the retry
    // -------------------------------------------------------------------------

    @Test
    @Timeout(value = 2, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void idleTimeoutRetryIgnoresOldCallbacks() {
        var callCount = new AtomicInteger();
        var oldHandler = new AtomicReference<StreamingChatResponseHandler>();
        var secondCallStarted = new AtomicReference<Boolean>(false);
        var model = mock(StreamingChatModel.class);
        var firstHandle = mock(StreamingHandle.class);
        doAnswer(inv -> {
            int call = callCount.incrementAndGet();
            var h = inv.getArgument(1, StreamingChatResponseHandler.class);
            if (call == 1) {
                oldHandler.set(h);
                h.onPartialResponse(new PartialResponse("ghost"), new PartialResponseContext(firstHandle));
                // Fire onError(CancellationException) synchronously from cancel
                doAnswer(c -> {
                    h.onError(new java.util.concurrent.CancellationException());
                    return null;
                }).when(firstHandle).cancel();
                return null; // no terminal callback — hangs
            }
            // Second call: mark started, then fire old callbacks on the OLD handler, then own completion
            secondCallStarted.set(true);
            var oldH = oldHandler.get();
            oldH.onPartialResponse(new PartialResponse("ghost2"), new PartialResponseContext(mock(StreamingHandle.class)));
            oldH.onError(new RuntimeException("ghost error"));
            oldH.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("ghost-done"))
                    .tokenUsage(new TokenUsage(999, 999, 999))
                    .build());
            // Now own completion
            var handle = mock(StreamingHandle.class);
            h.onPartialResponse(new PartialResponse("real"), new PartialResponseContext(handle));
            h.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("done"))
                    .tokenUsage(new TokenUsage(10, 5, 15))
                    .build());
            return null;
        }).when(model).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        var config = LlmConfig.builder().providerType(org.sterl.llmpeon.ai.AiProvider.OPEN_AI).model("test").timeout(Duration.ofMillis(200)).build();
        var chatModel = new ConfiguredChatModel(config, model);
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("hi"));

        var usageMonitor = new AtomicInteger();
        var ghostChunks = new ArrayList<String>();
        var chunkRecorder = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onStreamingChunk(OnPartialAiResponse r) {
                if (r.value() != null && secondCallStarted.get()) ghostChunks.add(r.value());
            }
            @Override public void onTokenUsage(TokenUsage usage) { usageMonitor.addAndGet(usage.totalTokenCount()); }
        };

        var retry = new ApiRetry(100);
        retry.call(chunkRecorder, () -> ChatResponse.builder().aiMessage(AiMessage.from("pre")).build());

        var request = ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(chatModel)
                .monitor(chunkRecorder)
                .retry(retry)
                .build();

        // The model's second call sets secondCallStarted before firing old callbacks
        var result = request.call(new ChatRequest.Builder().messages(UserMessage.from("hi")).build());

        assertThat(result.aiMessage().text()).isEqualTo("done");
        assertThat(callCount.get()).isEqualTo(2);
        assertThat(usageMonitor.get()).isEqualTo(15);
        // No ghost chunks from old handler leaked into the second call
        var ghostCount = ghostChunks.stream().filter(v -> v.contains("ghost")).count();
        assertThat(ghostCount).as("ghost chunks from old handler leaked").isZero();
    }

    // -------------------------------------------------------------------------
    // Model mocks
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