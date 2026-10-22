package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.Test;
import org.sterl.llmpeon.parts.shared.EclipseUtil;

/**
 * R5 (docs/write-path-validator.md): {@code await} must surface the real cause of a failed
 * UI-thread call — a denial arrives verbatim, "Timeout" is claimed only for a genuine timeout,
 * cancellation passes through unwrapped. No workbench needed: plain CompletableFutures.
 */
public class EclipseUtilAwaitTest {

    @Test
    public void test_await_unwraps_failed_future_to_the_denial() {
        // GIVEN a future failed with the validator's denial
        var f = CompletableFuture.<String>failedFuture(new IllegalArgumentException(
                "Write denied: '/ui-automate/config.toml' is outside this agent's allowed paths"));

        // WHEN
        var e = assertThrows(IllegalArgumentException.class,
                () -> EclipseUtil.await(f, Duration.ofSeconds(1), "editing the open editor"));

        // THEN the denial reaches the caller verbatim — no timeout claim, no envelope in the message
        assertTrue(e.getMessage(), e.getMessage().contains("Write denied"));
        assertFalse(e.getMessage(), e.getMessage().contains("Timeout"));
        assertFalse(e.getMessage(), e.getMessage().contains("ExecutionException"));
    }

    @Test
    public void test_await_reports_a_real_timeout() {
        // GIVEN a future that never completes
        var f = new CompletableFuture<String>();

        // WHEN
        var e = assertThrows(IllegalStateException.class,
                () -> EclipseUtil.await(f, Duration.ofMillis(100), "editing the open editor"));

        // THEN "Timeout" names the real cause and the waited action
        assertTrue(e.getMessage(), e.getMessage().contains("Timeout"));
        assertTrue(e.getMessage(), e.getMessage().contains("editing the open editor"));
        assertTrue("cause must be the TimeoutException, was: " + e.getCause(),
                e.getCause() instanceof TimeoutException);
    }

    @Test
    public void test_await_preserves_the_interrupt_flag() {
        // GIVEN the calling thread is already interrupted, the future never completes
        Thread.currentThread().interrupt();
        try {
            var f = new CompletableFuture<String>();

            // WHEN
            var e = assertThrows(IllegalStateException.class,
                    () -> EclipseUtil.await(f, Duration.ofSeconds(5), "editing the open editor"));

            // THEN the interrupt is reported and the flag survives for the caller
            assertTrue(e.getMessage(), e.getMessage().contains("Interrupted"));
            assertTrue("interrupt flag must be restored", Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted(); // clear the flag — otherwise the rest of the suite is poisoned
        }
    }

    @Test
    public void test_await_propagates_cancellation_unwrapped() {
        // GIVEN a future that was canceled
        var f = new CompletableFuture<String>();
        f.cancel(true);

        // WHEN/THEN the CancellationException passes through unwrapped — no swallowing, no re-labeling
        assertThrows(CancellationException.class,
                () -> EclipseUtil.await(f, Duration.ofSeconds(1), "editing the open editor"));
    }

    @Test
    public void test_await_passes_a_normal_result_through() {
        // GIVEN a completed future
        var f = CompletableFuture.completedFuture("Saved!");

        // WHEN/THEN the result arrives untouched
        assertEquals("Saved!", EclipseUtil.await(f, Duration.ofSeconds(1), "editing the open editor"));
    }

    @Test
    public void test_await_never_leaks_an_envelope_class_name() {
        // GIVEN a future whose whole failure is a future envelope — no real cause at the bottom
        // anonymous subclass: ExecutionException's constructors are protected (same as ExceptionUtilTest's cycle case)
        var f = CompletableFuture.<String>failedFuture(new ExecutionException("envelope only") {});

        // WHEN
        var e = assertThrows(IllegalStateException.class,
                () -> EclipseUtil.await(f, Duration.ofSeconds(1), "editing the open editor"));

        // THEN the message names the waited action but no envelope class — the chain stays in the cause
        assertTrue(e.getMessage(), e.getMessage().contains("editing the open editor"));
        assertFalse(e.getMessage(), e.getMessage().contains("ExecutionException"));
        assertFalse(e.getMessage(), e.getMessage().contains("java.util.concurrent"));
        assertNotNull("the original exception must stay the cause", e.getCause());
    }
}
