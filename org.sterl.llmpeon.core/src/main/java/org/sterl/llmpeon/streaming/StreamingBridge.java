package org.sterl.llmpeon.streaming;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.sterl.llmpeon.exception.ExceptionUtil;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.shared.OnPartialAiResponse;
import org.sterl.llmpeon.shared.OnPartialAiResponse.Type;
import org.sterl.llmpeon.shared.Timer;

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
 * Bridges langchain4j streaming to the blocking {@link AiMonitor} contract.
 * <p>
 * A fresh instance is created per call (and per retry). The bridge knows no turn:
 * the caller passes the agent-owned turn start (R20) to every {@link #call}, and each call resets
 * the per-call state (latch, refs, timing).
 * <p>
 * Timing: three {@link Timer}s track the prompt, think and token phases.
 * Chunks carry the stamped {@code tokenPhaseStart}, never the live timers.
 * <p>
 * Cancel takes precedence over idle timeout.
 */
public class StreamingBridge implements StreamingChatResponseHandler {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(3);

    private final Clock clock;
    private final long timeoutMillis;
    private volatile Instant startedAt;

    private final Timer ppTimer;
    private final Timer tokenTimer;
    private final Timer thinkTimer;
    private volatile long tokenPhaseStart;

    // Terminal outcome — null = pending, set exactly once per call
    private sealed interface Outcome {}
    private record Completed(ChatResponse response) implements Outcome {}
    private record Failed(Throwable error) implements Outcome {}
    private record Canceled(AiCancellationException error) implements Outcome {}
    private record TimedOut(RuntimeException error) implements Outcome {}

    // State guarded by stateLock
    private final Object stateLock = new Object();
    private long lastChunkAtMillis;
    private AtomicReference<Outcome> outcomeRef;

    private volatile CountDownLatch latch;
    private volatile AtomicReference<StreamingHandle> handleRef;
    private volatile AiMonitor monitor;

    private final StringBuilder partialResponse = new StringBuilder();
    private final StringBuilder partialThink = new StringBuilder();

    public StreamingBridge() {
        this(Clock.systemUTC(), DEFAULT_TIMEOUT);
    }

    public StreamingBridge(Duration timeout) {
        this(Clock.systemUTC(), timeout);
    }

    StreamingBridge(Clock clock) {
        this(clock, DEFAULT_TIMEOUT);
    }

    StreamingBridge(Clock clock, Duration timeout) {
        if (timeout == null) throw new NullPointerException("timeout must not be null");
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive: " + timeout);
        this.clock = clock;
        long ms = timeout.toMillis();
        long remainderNanos = timeout.toNanos() - ms * 1_000_000L;
        this.timeoutMillis = Math.addExact(ms, remainderNanos == 0 ? 0 : 1);
        this.ppTimer = new Timer(clock);
        this.tokenTimer = new Timer(clock);
        this.thinkTimer = new Timer(clock);
    }

    // Per-call state — reset at the top of each call()

    /**
     * Executes one streaming LLM call and blocks until complete, error, cancel, or idle timeout.
     * {@code startedAt} is the caller's (agent-owned) turn start.
     */
    public ChatResponse call(StreamingChatModel model, ChatRequest request, AiMonitor monitor, Instant startedAt) {
        this.startedAt = startedAt;
        this.latch = new CountDownLatch(1);
        this.outcomeRef = new AtomicReference<>();
        this.handleRef = new AtomicReference<>();
        this.monitor = AiMonitor.nullSafety(monitor);
        this.ppTimer.reset();
        this.ppTimer.start();
        this.thinkTimer.reset();
        this.thinkTimer.start();
        this.tokenTimer.reset();
        this.tokenPhaseStart = 0;
        this.partialResponse.setLength(0);
        this.partialThink.setLength(0);
        synchronized (stateLock) {
            this.lastChunkAtMillis = clock.millis();
        }

        this.monitor.onStreamingChunk(new OnPartialAiResponse(Type.START, null, startedAt, tokenPhaseStart));

        try {
            model.chat(request, this);

            while (!awaitCompletion(computeWaitMillis())) {
                if (checkStopOrTimeout()) break;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            synchronized (stateLock) {
                if (outcomeRef.get() == null) {
                    var msg = "AI thread interrupted (user or harness)";
                    outcomeRef.compareAndSet(null,
                            new Canceled(new AiCancellationException(msg,
                                    partialThink.toString(), partialResponse.toString())));
                }
            }
            StreamingHandle h = handleRef.get();
            if (h != null) {
                try { h.cancel(); } catch (Exception ignored) { }
            }
        } catch (Exception e) {
            var unwrapped = ExceptionUtil.unwrap(e);
            synchronized (stateLock) {
                if (outcomeRef.get() == null) {
                    outcomeRef.compareAndSet(null,
                            unwrapped instanceof RuntimeException re
                                    ? new Failed(re) : new Failed(new RuntimeException(unwrapped)));
                }
            }
        } finally {
            latch.countDown();
        }

        Outcome outcome = outcomeRef.get();
        if (outcome instanceof Completed c) return c.response();
        if (outcome instanceof Canceled c) throw c.error();
        if (outcome instanceof TimedOut t) throw t.error();
        if (outcome instanceof Failed f) {
            if (f.error() instanceof RuntimeException re) throw re;
            throw new RuntimeException(f.error());
        }
        // pending with no outcome — should not happen, but guard
        throw new IllegalStateException("StreamingBridge call completed without outcome");
    }

    private long computeWaitMillis() {
        long elapsed;
        synchronized (stateLock) {
            elapsed = clock.millis() - lastChunkAtMillis;
        }
        long remaining = timeoutMillis - elapsed;
        if (remaining <= 0) return 1;
        return Math.min(1000, remaining);
    }

    /**
     * Checks stop (cancel) first, then idle timeout. Both claim the outcome atomically.
     * @return true if the loop should exit
     */
    private boolean checkStopOrTimeout() {
        // Stop check — cancel wins over timeout
        if (monitor.isCanceled()) {
            synchronized (stateLock) {
                if (outcomeRef.get() != null) return true;
                var msg = "AI call canceled by user ...";
                outcomeRef.compareAndSet(null,
                        new Canceled(new AiCancellationException(msg,
                                partialThink.toString(), partialResponse.toString())));
            }
            // Cancel transport after outcome is published
            StreamingHandle h = handleRef.get();
            if (h != null) {
                try { h.cancel(); } catch (Exception ignored) { }
            }
            latch.countDown();
            return true;
        }

        // Idle timeout check
        synchronized (stateLock) {
            if (outcomeRef.get() != null) return true;
            long idle = clock.millis() - lastChunkAtMillis;
            if (idle < timeoutMillis) return false;
            var message = formatTimeout(timeoutMillis);
            outcomeRef.compareAndSet(null,
                    new TimedOut(new RuntimeException(message, new TimeoutException(message))));
        }
        StreamingHandle h = handleRef.get();
        if (h != null) {
            try { h.cancel(); } catch (Exception ignored) { }
        }
        latch.countDown();
        return true;
    }

    static String formatTimeout(long timeoutMillis) {
        if (timeoutMillis < 1000) {
            return "AI stream idle for " + timeoutMillis + " ms (timeout)";
        }
        double seconds = timeoutMillis / 1000.0;
        if (seconds == Math.floor(seconds)) {
            return "AI stream idle for " + ((long) seconds) + " s (timeout)";
        }
        return "AI stream idle for " + seconds + " s (timeout)";
    }

    /** Records chunk activity. Called under stateLock from partial entry. */
    private void markActivityLocked() {
        lastChunkAtMillis = clock.millis();
    }

    boolean awaitCompletion(long waitMillis) throws InterruptedException {
        return latch.await(waitMillis, TimeUnit.MILLISECONDS);
    }

    // -------------------------------------------------------------------------
    // StreamingChatResponseHandler — partial callbacks
    // -------------------------------------------------------------------------

    private void onFirstPartial() {
        if (tokenPhaseStart == 0) {
            tokenPhaseStart = clock.millis();
            tokenTimer.start();
            ppTimer.stop();
        }
    }

    @Override
    public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
        // Capture handle and activity under lock, then check pending
        StreamingHandle handle;
        synchronized (stateLock) {
            if (outcomeRef.get() != null) return;
            handleRef.compareAndSet(null, context.streamingHandle());
            handle = context.streamingHandle();
            markActivityLocked();
        }
        // External call: check cancel (never under lock)
        if (monitor.isCanceled()) {
            claimCancel(handle);
            return;
        }
        // Re-check pending after external call
        synchronized (stateLock) {
            if (outcomeRef.get() != null) return;
            onFirstPartial();
            this.partialResponse.append(partialResponse.text());
        }
        monitor.onStreamingChunk(new OnPartialAiResponse(Type.ANSWER, partialResponse.text(), startedAt, tokenPhaseStart));
    }

    @Override
    public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
        StreamingHandle handle;
        synchronized (stateLock) {
            if (outcomeRef.get() != null) return;
            handleRef.compareAndSet(null, context.streamingHandle());
            handle = context.streamingHandle();
            markActivityLocked();
        }
        if (monitor.isCanceled()) {
            claimCancel(handle);
            return;
        }
        synchronized (stateLock) {
            if (outcomeRef.get() != null) return;
            onFirstPartial();
            thinkTimer.stop();
            this.partialThink.append(partialThinking.text());
        }
        monitor.onStreamingChunk(new OnPartialAiResponse(Type.THINK, partialThinking.text(), startedAt, tokenPhaseStart));
    }

    @Override
    public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
        StreamingHandle handle;
        synchronized (stateLock) {
            if (outcomeRef.get() != null) return;
            handleRef.compareAndSet(null, context.streamingHandle());
            handle = context.streamingHandle();
            markActivityLocked();
        }
        if (monitor.isCanceled()) {
            claimCancel(handle);
            return;
        }
        synchronized (stateLock) {
            if (outcomeRef.get() != null) return;
            onFirstPartial();
        }
        monitor.onStreamingChunk(new OnPartialAiResponse(Type.TOOL, partialToolCall.partialArguments(), startedAt, tokenPhaseStart));
    }

    /** Claims a Canceled outcome and cancels transport. Called outside stateLock. */
    private void claimCancel(StreamingHandle handle) {
        synchronized (stateLock) {
            if (outcomeRef.get() != null) return;
            var msg = "AI call canceled by user ...";
            outcomeRef.compareAndSet(null,
                    new Canceled(new AiCancellationException(msg,
                            partialThink.toString(), partialResponse.toString())));
        }
        if (handle != null) {
            try { handle.cancel(); } catch (Exception ignored) { }
        }
        latch.countDown();
    }

    // -------------------------------------------------------------------------
    // Terminal callbacks — atomically claim outcome
    // -------------------------------------------------------------------------

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        synchronized (stateLock) {
            if (outcomeRef.get() != null) return;
            outcomeRef.compareAndSet(null, new Completed(completeResponse));
        }
        // Only the winner reports END and usage
        this.tokenTimer.stop();
        this.monitor.onStreamingChunk(new OnPartialAiResponse(Type.END, null, startedAt, tokenPhaseStart));
        var usage = ChatMessageUtil.tokenUsage(completeResponse);
        if (usage != null) this.monitor.onTokenUsage(usage);
        latch.countDown();
    }

    @Override
    public void onError(Throwable error) {
        synchronized (stateLock) {
            if (outcomeRef.get() != null) return;
            outcomeRef.compareAndSet(null, new Failed(error));
        }
        this.monitor.onStreamingChunk(new OnPartialAiResponse(Type.END, null, startedAt, tokenPhaseStart));
        latch.countDown();
    }

    // Package-private accessors for tests.
    Timer ppTimer() { return ppTimer; }
    Timer tokenTimer() { return tokenTimer; }
    Timer thinkTimer() { return thinkTimer; }
}