package org.sterl.llmpeon.streaming;

import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

import org.sterl.llmpeon.shared.AiMonitor;

import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * "Earned patience" retry around a single AI request. Wraps only the AI call (never tool execution),
 * so it lives at the {@code bridge.call} choke-point and applies to all agents and contexts.
 * <p>
 * Two counters (see docs/agents-retry.md):
 * <ul>
 *   <li>{@code credit} — earned patience: starts at 0, +1 per successful call, capped at
 *       {@link #MAX_CREDIT}. The very first error (credit 0) bubbles immediately.</li>
 *   <li>{@code retryCount} — consecutive retries in the current error streak: +1 per retry, reset to
 *       0 on any success. Drives the linear backoff.</li>
 * </ul>
 * A {@link CancellationException} is never retried — it {@code extends RuntimeException}, so the
 * classification catch order below is load-bearing.
 * <p>
 * Not thread-safe: one instance belongs to one {@code ToolLoopRequest} (one turn), reused across the
 * tool-loop iterations of that turn.
 */
@Slf4j
public class ApiRetry {

    static final int MAX_CREDIT = 10;
    static final long DEFAULT_MAX_WAIT_MS = 5 * 60 * 1000L;
    private static final long WAIT_STEP_MS = 10_000L;
    private static final long POLL_CHUNK_MS = 1_000L;

    @Getter
    private int credit = 0;
    @Getter
    private int retryCount = 0;
    private final long maxWaitMs;

    public ApiRetry() {
        this(DEFAULT_MAX_WAIT_MS);
    }

    /** @param maxWaitMs backoff cap; set small (e.g. 100) in tests for fast, real retries. */
    public ApiRetry(long maxWaitMs) {
        this.maxWaitMs = maxWaitMs;
    }

    /**
     * Runs {@code aiCall}, retrying transient {@link RuntimeException}s while patience remains.
     *
     * @throws CancellationException if the call is canceled (propagated as-is when thrown mid-stream,
     *         or newly raised — carrying the last transient error as cause — when canceled during the
     *         backoff wait; that cause is logged here because the UI swallows CancellationException).
     * @throws RuntimeException the last transient error once {@code retryCount >= credit}
     *         (the UI logs this on the give-up path — not logged here to avoid a double log).
     */
    public ChatResponse call(AiMonitor monitor, Supplier<ChatResponse> aiCall) {
        var m = AiMonitor.nullSafety(monitor);
        while (true) {
            try {
                var response = aiCall.get();
                credit = Math.min(credit + 1, MAX_CREDIT);
                retryCount = 0;
                return response;
            } catch (CancellationException ce) {
                throw ce; // a cancel is never a transient error — never retry it
            } catch (RuntimeException e) {
                if (retryCount >= credit) throw e; // out of patience — let the UI log & show it
                retryCount++;
                long wait = Math.min((long) retryCount * WAIT_STEP_MS, maxWaitMs);
                m.onProblem(problemMessage(e, wait));
                waitOrCancel(m, wait, e);
            }
        }
    }

    /** Sleeps in {@code <=1s} chunks, checking cancel between them so a cancel lands within ~1s. */
    private void waitOrCancel(AiMonitor monitor, long waitMs, RuntimeException root) {
        long rest = waitMs;
        while (rest > 0) {
            abortIfCanceled(monitor, root);
            long chunk = Math.min(POLL_CHUNK_MS, rest);
            try {
                Thread.sleep(chunk);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                abortIfCanceled(monitor, root);
                var ce = new CancellationException("Interrupted while waiting to retry the AI call");
                ce.initCause(root);
                throw ce;
            }
            rest -= chunk;
        }
        abortIfCanceled(monitor, root);
    }

    private void abortIfCanceled(AiMonitor monitor, RuntimeException root) {
        if (!monitor.isCanceled()) return;
        // The UI swallows CancellationException silently, so log the root here or it is lost.
        log.warn("AI call canceled while waiting to retry a transient error", root);
        var ce = new CancellationException("AI call canceled while waiting to retry");
        ce.initCause(root);
        throw ce;
    }

    private String problemMessage(RuntimeException e, long waitMs) {
        var detail = e.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = e.getClass().getSimpleName();
        } else {
            detail = detail.lines().findFirst().orElse(detail).trim();
        }
        return "API error — attempt " + retryCount + ", retrying in " + (waitMs / 1000)
                + "s. " + detail + " · Use Stop to cancel.";
    }
}
