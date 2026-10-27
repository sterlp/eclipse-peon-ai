package org.sterl.llmpeon.streaming;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.shared.OnPartialAiResponse;
import org.sterl.llmpeon.shared.OnPartialAiResponse.Type;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.shared.Timer;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Create one for your conversation. 
 * WIP
 */
public class AsyncStreamingBridge {

    private final Clock clock;
    @Getter
    private final Timer totalTimer;
    
    
    public AsyncStreamingBridge(Clock clock) {
        super();
        this.clock = clock;
        this.totalTimer = new Timer(clock).start();
    }

    public AsyncStreamingBridge() {
        this(Clock.systemUTC());
    }

    public CompletableFuture<SteamHandler> call(StreamingChatModel model, ChatRequest request, AiMonitor monitor) {
        return call(model, request, monitor, Instant.now(clock));
    }

    public CompletableFuture<SteamHandler> call(StreamingChatModel model, ChatRequest request, AiMonitor monitor, Instant startedAt) {
        Objects.requireNonNull(monitor, "Monitor is needed otherwise cancel can't work.");

        var result = new SteamHandler(clock, monitor, ChatMessageUtil.estimateTokens(request.messages()), startedAt);
        // and go
        monitor.onStreamingChunk(new OnPartialAiResponse(Type.START, null, startedAt, 0));
        model.chat(request, result);
        
        return result.getFuture();
    }

    @RequiredArgsConstructor
    public static class SteamHandler implements StreamingChatResponseHandler {
        @Getter
        private final CompletableFuture<SteamHandler> future = new CompletableFuture<>();
        private final AiMonitor monitor;
        private final int inputTok;

        private final Clock clock;
        /** R20: the turn start shown in the chunks — owned by the agent, not by this call. */
        private final Instant startedAt;
        /** This call's own start — the only basis of the call duration. */
        private final Instant callStart;

        private final Timer ppTimer;
        private final Timer tokenTimer;
        private final Timer thinkTimer;
        /** time until we see the first token */
        private volatile long tokenPhaseStart = 0;
        
        // Per-call state — reset at the top of each call()
        @Getter
        private volatile ChatResponse response;
        @Getter
        private volatile AtomicReference<Throwable> errorRef = new AtomicReference<>();
        
        private StringBuilder partialResponse = new StringBuilder();
        private StringBuilder partialThink = new StringBuilder();
        
        public boolean isCanceled() {
            return errorRef.get() != null && errorRef.get() instanceof CancellationException;
        }
        
        public boolean isFailed() {
            return errorRef.get() != null;
        }

        SteamHandler(Clock clock, AiMonitor monitor, int inputTok, Instant startedAt) {
            this.clock = clock;
            this.inputTok = inputTok;
            this.monitor = AiMonitor.nullSafety(monitor);
            this.startedAt = startedAt;
            this.callStart = Instant.now(clock);

            this.ppTimer = new Timer(clock).start();

            this.tokenTimer = new Timer(clock);
            this.thinkTimer = new Timer(clock);
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
            if (cancelAndRelease(context.streamingHandle())) return;
            onFirstPartial();
            monitor.onStreamingChunk(new OnPartialAiResponse(Type.ANSWER, partialResponse.text(), startedAt, tokenPhaseStart));
            this.partialResponse.append(partialResponse.text());
        }

        @Override
        public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
            if (cancelAndRelease(context.streamingHandle())) return;
            onFirstPartial();
            thinkTimer.stop();
            monitor.onStreamingChunk(new OnPartialAiResponse(Type.THINK, partialThinking.text(), startedAt, tokenPhaseStart));
            this.partialThink.append(partialThinking.text());
        }

        @Override
        public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
            if (cancelAndRelease(context.streamingHandle())) return;
            onFirstPartial();
            // The tool name repeats in every callback; only the argument delta carries new tokens (D5).
            monitor.onStreamingChunk(new OnPartialAiResponse(Type.TOOL, partialToolCall.partialArguments(), startedAt, tokenPhaseStart));
        }

        private boolean cancelAndRelease(StreamingHandle handle) {
            if (!monitor.isCanceled()) return false;
            if (handle != null) handle.cancel();
            errorRef.compareAndSet(null, new CancellationException("AI call canceled ..."));
            buildPartialResult();
            future.complete(this);
            return true;
        }
        
        private final void buildPartialResult() {
            if (this.response == null) {
                var msg = partialResponse.isEmpty() ? partialThink.toString() : partialResponse.toString();
                if (StringUtil.hasValue(msg)) {
                    var usgae = new TokenUsage(inputTok, ChatMessageUtil.estimateTokens(msg));

                    this.response = ChatResponse.builder()
                            .aiMessage(AiMessage.from(msg))
                            .finishReason(FinishReason.OTHER)
                            .metadata(ChatResponseMetadata.builder()
                                    .tokenUsage(usgae)
                                    .finishReason(FinishReason.OTHER)
                                    .build())
                            .build();
                    
                    this.monitor.onTokenUsage(usgae);
                }
            }
        }

        // -------------------------------------------------------------------------
        // Terminal callbacks — release the latch
        // -------------------------------------------------------------------------

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            this.tokenTimer.stop();
            this.monitor.onStreamingChunk(new OnPartialAiResponse(Type.END, null, startedAt, tokenPhaseStart));
            this.response = completeResponse;

            // Single accumulation trigger for the whole app (main loop, search sub-agent, compaction).
            // Only real provider usage — no estimate. See docs/adr/0004-session-token-accounting.md.
            var usage = ChatMessageUtil.tokenUsage(completeResponse);
            if (usage != null) this.monitor.onTokenUsage(usage);

            monitor.onCallCompleted(response, Duration.between(callStart, Instant.now(clock)));
            future.complete(this);
        }

        @Override
        public void onError(Throwable error) {
            this.monitor.onStreamingChunk(new OnPartialAiResponse(Type.END, null, startedAt, tokenPhaseStart));
            // first setter wins: a cancel stored a CancellationException before the provider's
            // post-cancel onError — overwriting it would make callers retry a stopped call
            errorRef.compareAndSet(null, error);
            buildPartialResult();
            future.complete(this);
        }

        // Package-private accessors for tests.
        Timer ppTimer() { return ppTimer; }
        Timer tokenTimer() { return tokenTimer; }
        Timer thinkTimer() { return thinkTimer; }
    }
}
