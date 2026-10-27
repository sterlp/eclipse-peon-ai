package org.sterl.llmpeon.streaming;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
 * One instance is reused across all tool-loop iterations of a request. The bridge knows no turn:
 * the caller passes the agent-owned turn start (R20) to every {@link #call}, and each call resets
 * the per-call state (latch, refs, timing).
 * <p>
 * Timing: three {@link Timer}s track the prompt phase (reset+start per call, stopped at the
 * first partial), the think phase (reset+start per call, stopped at the first thinking) and the
 * token phase (started at the first partial, stopped at completion). Chunks carry the
 * stamped {@code tokenPhaseStart}, never the live timers.
 * <p>
 * Cancel: every partial callback checks {@link AiMonitor#isCanceled()} and calls
 * {@link StreamingHandle#cancel()} immediately when true.
 * 
 * Maybe replaced by {@link AsyncStreamingBridge}.
 */
public class StreamingBridge implements StreamingChatResponseHandler {

    private final Clock clock;
    private volatile Instant startedAt;

    private final Timer ppTimer;
    private final Timer tokenTimer;
    private final Timer thinkTimer;
    private volatile long tokenPhaseStart;

    public StreamingBridge() {
        this(Clock.systemUTC());
    }

    StreamingBridge(Clock clock) {
        this.clock = clock;
        this.ppTimer = new Timer(clock);
        this.tokenTimer = new Timer(clock);
        this.thinkTimer = new Timer(clock);
    }

    // Per-call state — reset at the top of each call()
    private volatile CountDownLatch latch;
    private volatile AtomicReference<ChatResponse> responseRef;
    private volatile AtomicReference<Throwable> errorRef;
    private volatile AtomicReference<StreamingHandle> handleRef;
    private volatile AiMonitor monitor;

    /**
     * Executes one streaming LLM call and blocks until complete or error.
     * {@code startedAt} is the caller's (agent-owned) turn start, shown in every chunk of this call.
     * 
     * @throws CancellationException if canceled
     */
    public ChatResponse call(StreamingChatModel model, ChatRequest request, AiMonitor monitor, Instant startedAt) {
        this.startedAt = startedAt;
        this.latch = new CountDownLatch(1);
        this.responseRef = new AtomicReference<>();
        this.errorRef = new AtomicReference<>();
        this.handleRef = new AtomicReference<>();
        this.monitor = AiMonitor.nullSafety(monitor);
        // Per-call timing: prompt and think phases start now; the token phase starts at the first partial.
        this.ppTimer.reset();
        this.ppTimer.start();
        this.thinkTimer.reset();
        this.thinkTimer.start();
        this.tokenTimer.reset();
        this.tokenPhaseStart = 0;
        this.monitor.onStreamingChunk(new OnPartialAiResponse(Type.START, null, startedAt, tokenPhaseStart));


        Throwable error = null;
        try {
            model.chat(request, this);

            while (!latch.await(1500, TimeUnit.MILLISECONDS)) {
                cancelAndRelease(handleRef.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            StreamingHandle h = handleRef.get();
            if (h != null) h.cancel();
            errorRef.compareAndSet(null, new CancellationException("Thread interrupted"));
        } catch (Exception e) {
            error = e;
        } finally {
            latch.countDown();
        }

        error = errorRef.get() == null ? error : errorRef.get();
        if (error != null) {
            // if we are canceled - and have a response use it
            if (error instanceof CancellationException && responseRef.get() != null) return responseRef.get();
            if (error instanceof RuntimeException ex) throw ex;
            throw new RuntimeException(error);
        }
        return responseRef.get();
    }

    // -------------------------------------------------------------------------
    // StreamingChatResponseHandler — partial callbacks with cancel guard
    // -------------------------------------------------------------------------

    /**
     * Stamps the token-phase start on the first partial of any type, starts the token timer and
     * stops the prompt-phase timer. Subsequent partials are no-ops.
     */
    private void onFirstPartial() {
        if (tokenPhaseStart == 0) {
            tokenPhaseStart = clock.millis();
            tokenTimer.start();
            ppTimer.stop();
        }
    }

    @Override
    public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
        handleRef.compareAndSet(null, context.streamingHandle());
        if (cancelAndRelease(context.streamingHandle())) return;
        onFirstPartial();
        monitor.onStreamingChunk(new OnPartialAiResponse(Type.ANSWER, partialResponse.text(), startedAt, tokenPhaseStart));
    }

    @Override
    public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
        handleRef.compareAndSet(null, context.streamingHandle());
        if (cancelAndRelease(context.streamingHandle())) return;
        onFirstPartial();
        thinkTimer.stop();
        monitor.onStreamingChunk(new OnPartialAiResponse(Type.THINK, partialThinking.text(), startedAt, tokenPhaseStart));
    }

    @Override
    public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
        handleRef.compareAndSet(null, context.streamingHandle());
        if (cancelAndRelease(context.streamingHandle())) return;
        onFirstPartial();
        // The tool name repeats in every callback; only the argument delta carries new tokens (D5).
        monitor.onStreamingChunk(new OnPartialAiResponse(Type.TOOL, partialToolCall.partialArguments(), startedAt, tokenPhaseStart));
    }

    private boolean cancelAndRelease(StreamingHandle handle) {
        if (!monitor.isCanceled()) return false;
        if (handle != null) handle.cancel();
        errorRef.compareAndSet(null, new CancellationException("AI call canceled ..."));
        latch.countDown();
        return true;
    }

    // -------------------------------------------------------------------------
    // Terminal callbacks — release the latch
    // -------------------------------------------------------------------------

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        this.tokenTimer.stop();
        this.monitor.onStreamingChunk(new OnPartialAiResponse(Type.END, null, startedAt, tokenPhaseStart));
        responseRef.set(completeResponse);
        // Single accumulation trigger for the whole app (main loop, search sub-agent, compaction).
        // Only real provider usage — no estimate. See docs/adr/0004-session-token-accounting.md.
        var usage = ChatMessageUtil.tokenUsage(completeResponse);
        if (usage != null) this.monitor.onTokenUsage(usage);
        latch.countDown();
    }

    @Override
    public void onError(Throwable error) {
        this.monitor.onStreamingChunk(new OnPartialAiResponse(Type.END, null, startedAt, tokenPhaseStart));
        // first setter wins: a cancel stored a CancellationException before the provider's
        // post-cancel onError — overwriting it would make callers retry a stopped call
        errorRef.compareAndSet(null, error);
        latch.countDown();
    }

    // Package-private accessors for tests.
    Timer ppTimer() { return ppTimer; }
    Timer tokenTimer() { return tokenTimer; }
    Timer thinkTimer() { return thinkTimer; }
}
