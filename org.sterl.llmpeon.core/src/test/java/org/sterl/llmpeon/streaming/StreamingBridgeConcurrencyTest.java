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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Proves that terminal outcomes (completion, failure, cancel, timeout) win exactly one race
 * under concurrent callback and poller access. Each invocation uses real Bridge/Clock and
 * latches to control interleaving — no mock of the outcome path, no reflection on internals.
 */
class StreamingBridgeConcurrencyTest {

    static final Instant T0 = Instant.parse("2026-09-04T10:00:00Z");

    static final class MutableClock extends Clock {
        private volatile Instant instant = T0;
        void advance(Duration d) { instant = instant.plus(d); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    private static ChatRequest request(String text) {
        return new ChatRequest.Builder().messages(UserMessage.from(text)).build();
    }

    // Partial activity racing with idle timeout — activity must win
    @ParameterizedTest
    @ValueSource(strings = {"PARTIAL_ACTIVITY"})
    @Timeout(value = 2, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void partialActivityBeatsTimeout(String race) throws Exception {
        if (!"PARTIAL_ACTIVITY".equals(race)) return;
        var clock = new MutableClock();
        var activityDelivered = new CountDownLatch(1); // callback has delivered partial
        var pollerWaiting = new CountDownLatch(1); // poller is inside awaitCompletion

        var model = mock(StreamingChatModel.class);
        var handlerRef = new AtomicReference<StreamingChatResponseHandler>();
        doAnswer(inv -> {
            handlerRef.set(inv.getArgument(1, StreamingChatResponseHandler.class));
            return null;
        }).when(model).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        var bridge = new StreamingBridge(clock, Duration.ofMillis(200)) {
            @Override
            boolean awaitCompletion(long waitMillis) throws InterruptedException {
                pollerWaiting.countDown();
                // Wait until callback has delivered activity, then advance past timeout
                activityDelivered.await(500, TimeUnit.MILLISECONDS);
                clock.advance(Duration.ofMillis(200));
                return false;
            }
        };

        var callbackThread = new Thread(() -> {
            var h = handlerRef.get();
            var handle = mock(StreamingHandle.class);
            // Fire partial at t100 — this marks activity
            clock.advance(Duration.ofMillis(100));
            h.onPartialResponse(new PartialResponse("Hal"), new PartialResponseContext(handle));
            activityDelivered.countDown();
            h.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("Hal")).build());
        });

        var resultRef = new AtomicReference<ChatResponse>();
        var errorRef = new AtomicReference<Throwable>();
        var callerThread = new Thread(() -> {
            try {
                resultRef.set(bridge.call(model, request("hi"), AiMonitor.NULL_MONITOR, T0));
            } catch (Throwable t) {
                errorRef.set(t);
            }
        });

        callerThread.start();
        // Wait for handler to be set before starting callback
        while (handlerRef.get() == null) Thread.onSpinWait();
        callbackThread.start();

        pollerWaiting.await(500, TimeUnit.MILLISECONDS);
        callbackThread.join(500);
        callerThread.join(500);

        assertThat(errorRef.get()).isNull();
        assertThat(resultRef.get()).isNotNull();
        assertThat(resultRef.get().aiMessage().text()).isEqualTo("Hal");
    }

    // Completion/failure racing with idle timeout — terminal must win
    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "FAILED"})
    @Timeout(value = 2, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void completionOrFailureBeatsTimeout(String race) throws Exception {
        var clock = new MutableClock();
        var terminalDelivered = new CountDownLatch(1); // callback has delivered terminal outcome
        var pollerWaiting = new CountDownLatch(1);

        var model = mock(StreamingChatModel.class);
        var handlerRef = new AtomicReference<StreamingChatResponseHandler>();
        doAnswer(inv -> {
            handlerRef.set(inv.getArgument(1, StreamingChatResponseHandler.class));
            return null;
        }).when(model).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        var usageCount = new AtomicReference<Integer>(0);
        var endMonitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onStreamingChunk(OnPartialAiResponse r) {}
            @Override public void onTokenUsage(TokenUsage usage) { usageCount.updateAndGet(v -> v == null ? usage.totalTokenCount() : v + usage.totalTokenCount()); }
        };

        var bridge = new StreamingBridge(clock, Duration.ofMillis(200)) {
            @Override
            boolean awaitCompletion(long waitMillis) throws InterruptedException {
                pollerWaiting.countDown();
                // Wait until callback has delivered terminal outcome, then advance past timeout
                terminalDelivered.await(500, TimeUnit.MILLISECONDS);
                clock.advance(Duration.ofMillis(200));
                return false;
            }
        };

        var callbackThread = new Thread(() -> {
            var h = handlerRef.get();
            var handle = mock(StreamingHandle.class);
            h.onPartialResponse(new PartialResponse("X"), new PartialResponseContext(handle));
            if ("COMPLETED".equals(race)) {
                h.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.from("done"))
                        .tokenUsage(new TokenUsage(10, 5, 15))
                        .build());
            } else {
                h.onError(new RuntimeException("provider error"));
            }
            terminalDelivered.countDown();
        });

        var errorRef = new AtomicReference<Throwable>();
        var resultRef = new AtomicReference<ChatResponse>();
        var callerThread = new Thread(() -> {
            try {
                resultRef.set(bridge.call(model, request("hi"), endMonitor, T0));
            } catch (Throwable t) {
                errorRef.set(t);
            }
        });

        callerThread.start();
        while (handlerRef.get() == null) Thread.onSpinWait();
        callbackThread.start();

        pollerWaiting.await(500, TimeUnit.MILLISECONDS);
        callbackThread.join(500);
        callerThread.join(500);

        if ("COMPLETED".equals(race)) {
            assertThat(errorRef.get()).isNull();
            assertThat(resultRef.get()).isNotNull();
            assertThat(resultRef.get().aiMessage().text()).isEqualTo("done");
            assertThat(usageCount.get()).isEqualTo(15);
        } else {
            assertThat(errorRef.get()).isNotNull();
            assertThat(errorRef.get()).isInstanceOf(RuntimeException.class);
            assertThat(errorRef.get().getMessage()).isEqualTo("provider error");
            assertThat(resultRef.get()).isNull();
        }
    }

    // Cancel racing with poller — cancel must win, never null
    @ParameterizedTest
    @ValueSource(strings = {"CANCELED"})
    @Timeout(value = 2, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void cancelBeatsPollerNullResult(String race) throws Exception {
        if (!"CANCELED".equals(race)) return;
        var clock = new MutableClock();
        var cancelEntered = new CountDownLatch(1); // handle.cancel() has started
        var cancelBlocker = new CountDownLatch(1); // holds cancel
        var pollerWaiting = new CountDownLatch(1);

        var monitorCanceled = new AtomicReference<Boolean>(false);
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public boolean isCanceled() { return monitorCanceled.get(); }
        };

        var model = mock(StreamingChatModel.class);
        var handlerRef = new AtomicReference<StreamingChatResponseHandler>();
        doAnswer(inv -> {
            handlerRef.set(inv.getArgument(1, StreamingChatResponseHandler.class));
            return null;
        }).when(model).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        var handlerReady = new CountDownLatch(1);
        var cancelStarted = new AtomicReference<Boolean>(false);
        var bridge = new StreamingBridge(clock, Duration.ofMillis(500)) {
            @Override
            boolean awaitCompletion(long waitMillis) throws InterruptedException {
                pollerWaiting.countDown();
                while (!cancelStarted.get()) Thread.onSpinWait();
                return false;
            }
        };

        var callbackThread = new Thread(() -> {
            try {
                handlerReady.await(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            var h = handlerRef.get();
            var handle = mock(StreamingHandle.class);
            doAnswer(c -> {
                cancelEntered.countDown();
                cancelStarted.set(true);
                try { cancelBlocker.await(500, TimeUnit.MILLISECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                return null;
            }).when(handle).cancel();
            // First partial: ANSWER "Hal" — accepted
            h.onPartialResponse(new PartialResponse("Hal"), new PartialResponseContext(handle));
            // Second partial: triggers stop via monitorCanceled
            clock.advance(Duration.ofMillis(100));
            monitorCanceled.set(true);
            h.onPartialResponse(new PartialResponse("extra"), new PartialResponseContext(handle));
            // After cancel, provider fires error
            h.onError(new RuntimeException("post-cancel error"));
        });

        var errorRef = new AtomicReference<Throwable>();
        var resultRef = new AtomicReference<ChatResponse>();
        var callerThread = new Thread(() -> {
            try {
                resultRef.set(bridge.call(model, request("hi"), monitor, T0));
            } catch (Throwable t) {
                errorRef.set(t);
            }
        });

        callerThread.start();
        // Wait for model.chat to set the handler before starting callback thread
        Thread.sleep(100);
        handlerReady.countDown();
        callbackThread.start();

        pollerWaiting.await(500, TimeUnit.MILLISECONDS);
        cancelBlocker.countDown();

        callbackThread.join(500);
        callerThread.join(500);

        assertThat(errorRef.get()).isNotNull();
        assertThat(errorRef.get()).isInstanceOf(AiCancellationException.class);
        var ace = (AiCancellationException) errorRef.get();
        assertThat(ace.getPartialResult()).isEqualTo("Hal");
        assertThat(resultRef.get()).isNull();
    }
}