package org.sterl.llmpeon.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

class ApiRetryTest {

    /** Collects onProblem messages and lets a test drive isCanceled(). */
    static class TestMonitor implements AiMonitor {
        final List<String> problems = new ArrayList<>();
        final AtomicBoolean canceled = new AtomicBoolean(false);

        @Override public void onChatResponse(SimpleMessage message) {}
        @Override public void onProblem(String message) { problems.add(message); }
        @Override public boolean isCanceled() { return canceled.get(); }
    }

    private static ChatResponse ok() {
        return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
    }

    /**
     * Earns {@code n} credit by making {@code n} separate successful calls — mirrors how the tool loop
     * calls {@code retry.call} once per iteration. Credit accumulates ACROSS calls, not within one.
     */
    private static void precharge(ApiRetry retry, AiMonitor monitor, int n) {
        for (int i = 0; i < n; i++) retry.call(monitor, ApiRetryTest::ok);
    }

    @Test
    @Timeout(5)
    void precharge_then_retry_succeeds() {
        // GIVEN — one prior success earns credit 1; the next call fails once then recovers
        var retry = new ApiRetry(10); // maxWaitMs tiny → fast, real backoff
        var monitor = new TestMonitor();
        precharge(retry, monitor, 1);
        var ok = ok();
        var calls = new AtomicInteger();
        Supplier<ChatResponse> ai = () -> {
            if (calls.incrementAndGet() == 1) throw new RuntimeException("transient");
            return ok;
        };

        // WHEN
        var result = retry.call(monitor, ai);

        // THEN — recovered inside the retry, one retry announced, counters correct
        assertThat(result).isSameAs(ok);
        assertThat(calls.get()).isEqualTo(2);            // fail then recover
        assertThat(monitor.problems).hasSize(1);
        assertThat(monitor.problems.get(0)).contains("attempt 1", "Use Stop to cancel");
        assertThat(retry.getRetryCount()).isZero();      // reset on success
        assertThat(retry.getCredit()).isEqualTo(2);      // +1 per successful call
    }

    @Test
    @Timeout(5)
    void first_error_bubbles_without_retry() {
        // GIVEN — fresh, credit 0
        var retry = new ApiRetry(10);
        var monitor = new TestMonitor();
        var calls = new AtomicInteger();
        Supplier<ChatResponse> ai = () -> { calls.incrementAndGet(); throw new RuntimeException("boom"); };

        // WHEN / THEN — first error bubbles immediately, no retry, no problem message
        var ex = assertThrows(RuntimeException.class, () -> retry.call(monitor, ai));
        assertThat(ex).hasMessage("boom").isNotInstanceOf(CancellationException.class);
        assertThat(calls.get()).isOne();
        assertThat(monitor.problems).isEmpty();
    }

    @Test
    @Timeout(5)
    void patience_exhausted_rethrows_last_error() {
        // GIVEN — credit 1 from one prior success, then a permanently failing call
        var retry = new ApiRetry(10);
        var monitor = new TestMonitor();
        precharge(retry, monitor, 1);
        var calls = new AtomicInteger();
        Supplier<ChatResponse> ai = () -> { calls.incrementAndGet(); throw new RuntimeException("still failing"); };

        // WHEN / THEN — one retry (credit 1), then retryCount >= credit → give up
        var ex = assertThrows(RuntimeException.class, () -> retry.call(monitor, ai));
        assertThat(ex).hasMessage("still failing");
        assertThat(calls.get()).isEqualTo(2);            // initial attempt + one retried attempt
        assertThat(monitor.problems).hasSize(1);
    }

    @Test
    @Timeout(5)
    void cancellation_is_never_retried() {
        // GIVEN — credit 1 available, but the failure is a CancellationException (a RuntimeException)
        var retry = new ApiRetry(10);
        var monitor = new TestMonitor();
        precharge(retry, monitor, 1);
        var calls = new AtomicInteger();
        Supplier<ChatResponse> ai = () -> { calls.incrementAndGet(); throw new CancellationException("stopped mid-stream"); };

        // WHEN / THEN — bubbles straight up despite available credit, no retry
        assertThrows(CancellationException.class, () -> retry.call(monitor, ai));
        assertThat(calls.get()).isOne();
        assertThat(monitor.problems).isEmpty();
    }

    @Test
    @Timeout(5)
    void cancel_before_wait_wraps_root_and_stops() {
        // GIVEN — credit 1; the failing call also sets cancel, so cancel is true when the wait starts
        var retry = new ApiRetry(60_000); // large cap — we must NOT sleep it out
        var monitor = new TestMonitor();
        precharge(retry, monitor, 1);
        var calls = new AtomicInteger();
        Supplier<ChatResponse> ai = () -> {
            calls.incrementAndGet();
            monitor.canceled.set(true);
            throw new RuntimeException("boom");
        };

        // WHEN / THEN — CancellationException carrying the transient root as cause, no sleep
        var ex = assertThrows(CancellationException.class, () -> retry.call(monitor, ai));
        assertThat(ex.getCause()).isInstanceOf(RuntimeException.class).hasMessage("boom");
        assertThat(calls.get()).isOne();
        assertThat(monitor.problems).hasSize(1);         // retry was announced before the wait
    }

    @Test
    @Timeout(5)
    void cancel_during_wait_lands_within_a_poll_chunk() throws InterruptedException {
        // GIVEN — credit 1 and a long backoff; cancel flips from another thread mid-wait
        var retry = new ApiRetry(60_000);
        var monitor = new TestMonitor();
        precharge(retry, monitor, 1);
        var calls = new AtomicInteger();
        Supplier<ChatResponse> ai = () -> { calls.incrementAndGet(); throw new RuntimeException("boom"); };
        var flip = new Thread(() -> {
            try { Thread.sleep(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            monitor.canceled.set(true);
        });

        // WHEN
        flip.start();
        var ex = assertThrows(CancellationException.class, () -> retry.call(monitor, ai));
        flip.join();

        // THEN — the chunked poll picked up the cancel well within @Timeout, root preserved
        assertThat(ex.getCause()).isInstanceOf(RuntimeException.class).hasMessage("boom");
        assertThat(calls.get()).isOne();
    }

    @Test
    @Timeout(5)
    void credit_is_capped_at_ten() {
        // GIVEN — many successes
        var retry = new ApiRetry(10);
        var monitor = new TestMonitor();

        // WHEN — 20 successful calls
        for (int i = 0; i < 20; i++) retry.call(monitor, ApiRetryTest::ok);

        // THEN — credit does not grow past the cap
        assertThat(retry.getCredit()).isEqualTo(ApiRetry.MAX_CREDIT);
    }
}
