package org.sterl.llmpeon.streaming;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.OnPartialAiResponse;
import org.sterl.llmpeon.shared.OnPartialAiResponse.Type;

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
 * One instance is created per user request and reused across all tool-loop
 * iterations so that {@code startedAt} spans the entire turn. Each call to
 * {@link #call} resets per-call state (latch, refs) but keeps {@code startedAt}.
 * <p>
 * Cancel: every partial callback checks {@link AiMonitor#isCanceled()} and calls
 * {@link StreamingHandle#cancel()} immediately when true.
 */
public class StreamingBridge implements StreamingChatResponseHandler {

    private Instant startedAt;

    // Per-call state — reset at the top of each call()
    private volatile CountDownLatch latch;
    private volatile AtomicReference<ChatResponse> responseRef;
    private volatile AtomicReference<Throwable> errorRef;
    private volatile AtomicReference<StreamingHandle> handleRef;
    private volatile AiMonitor monitor;

    /**
     * Executes one streaming LLM call and blocks until complete or error.
     * Sets {@code startedAt} on the first invocation only.
     */
    public ChatResponse call(StreamingChatModel model, ChatRequest request, AiMonitor monitor) {
        if (startedAt == null) startedAt = Instant.now();

        this.latch = new CountDownLatch(1);
        this.responseRef = new AtomicReference<>();
        this.errorRef = new AtomicReference<>();
        this.handleRef = new AtomicReference<>();
        this.monitor = AiMonitor.nullSafety(monitor);
        this.monitor.onStreamingChunk(new OnPartialAiResponse(Type.WAITING, null, startedAt));

        model.chat(request, this);

        try {
            while (!latch.await(1500, TimeUnit.MILLISECONDS)) {
                cancelAndRelease(handleRef.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            StreamingHandle h = handleRef.get();
            if (h != null) h.cancel();
            errorRef.compareAndSet(null, new RuntimeException("Interrupted"));
            latch.countDown();
        }

        Throwable error = errorRef.get();
        if (error != null) throw new RuntimeException(error);
        return responseRef.get();
    }

    // -------------------------------------------------------------------------
    // StreamingChatResponseHandler — partial callbacks with cancel guard
    // -------------------------------------------------------------------------

    @Override
    public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
        handleRef.compareAndSet(null, context.streamingHandle());
        if (cancelAndRelease(context.streamingHandle())) return;
        monitor.onStreamingChunk(new OnPartialAiResponse(Type.ANSWER, partialResponse.text(), startedAt));
    }

    @Override
    public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
        handleRef.compareAndSet(null, context.streamingHandle());
        if (cancelAndRelease(context.streamingHandle())) return;
        monitor.onStreamingChunk(new OnPartialAiResponse(Type.THINK, partialThinking.text(), startedAt));
    }

    @Override
    public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
        handleRef.compareAndSet(null, context.streamingHandle());
        if (cancelAndRelease(context.streamingHandle())) return;
        monitor.onStreamingChunk(new OnPartialAiResponse(Type.TOOL, partialToolCall.name(), startedAt));
    }

    private boolean cancelAndRelease(StreamingHandle handle) {
        if (!monitor.isCanceled()) return false;
        if (handle != null) handle.cancel();
        errorRef.compareAndSet(null, new RuntimeException("Cancelled"));
        latch.countDown();
        return true;
    }

    // -------------------------------------------------------------------------
    // Terminal callbacks — release the latch
    // -------------------------------------------------------------------------

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        responseRef.set(completeResponse);
        latch.countDown();
    }

    @Override
    public void onError(Throwable error) {
        errorRef.set(error);
        latch.countDown();
    }
}
