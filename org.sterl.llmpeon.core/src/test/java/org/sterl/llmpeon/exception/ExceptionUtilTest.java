package org.sterl.llmpeon.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import dev.langchain4j.exception.RateLimitException;

class ExceptionUtilTest {

    @Test
    void isCanceled_returns_true_for_direct_cancellation() {
        assertThat(ExceptionUtil.isCanceled(new CancellationException())).isTrue();
    }

    @Test
    void isCanceled_returns_true_for_nested_cancellation() {
        var cause = new CancellationException("aborted");
        assertThat(ExceptionUtil.isCanceled(new RuntimeException("wrap", cause))).isTrue();
    }

    @Test
    void isCanceled_returns_true_for_deeply_nested_cancellation() {
        var root = new CancellationException("aborted");
        var mid  = new IllegalStateException("mid", root);
        var top  = new RuntimeException("top", mid);
        assertThat(ExceptionUtil.isCanceled(top)).isTrue();
    }

    @Test
    void isCanceled_returns_false_for_plain_exception() {
        assertThat(ExceptionUtil.isCanceled(new RuntimeException("boom"))).isFalse();
    }

    @Test
    void isCanceled_returns_false_for_null() {
        assertThat(ExceptionUtil.isCanceled(null)).isFalse();
    }

    @Test
    void isRateLimit_returns_true_for_direct_rate_limit() {
        assertThat(ExceptionUtil.isRateLimit(new RateLimitException("limit"))).isTrue();
    }

    @Test
    void isRateLimit_returns_true_for_nested_rate_limit() {
        var cause = new RateLimitException("limit");
        assertThat(ExceptionUtil.isRateLimit(new RuntimeException("wrap", cause))).isTrue();
    }

    @Test
    void isRateLimit_returns_false_for_plain_exception() {
        assertThat(ExceptionUtil.isRateLimit(new RuntimeException("boom"))).isFalse();
    }

    @Test
    void isRateLimit_returns_false_for_null() {
        assertThat(ExceptionUtil.isRateLimit(null)).isFalse();
    }

    // ------------------------------------------------------------------ unwrap (R5, docs/write-path-validator.md)

    @Test
    void unwrap_returns_the_cause_of_an_execution_exception() {
        // GIVEN an ExecutionException wrapping the validator's denial
        var denial = new IllegalArgumentException("Write denied: x");
        var envelope = new ExecutionException(denial);

        // WHEN
        var unwrapped = ExceptionUtil.unwrap(envelope);

        // THEN the very same denial instance comes out
        assertThat(unwrapped).isSameAs(denial);
    }

    @Test
    void unwrap_strips_stacked_future_envelopes() {
        // GIVEN two stacked future envelopes around the real cause
        var deep = new IllegalStateException("deep");
        var top = new ExecutionException(new CompletionException(deep));

        // WHEN
        var unwrapped = ExceptionUtil.unwrap(top);

        // THEN all envelopes are stripped
        assertThat(unwrapped).isSameAs(deep);
    }

    @Test
    void unwrap_returns_the_throwable_itself_when_not_enveloped() {
        // GIVEN a plain throwable
        var boom = new RuntimeException("boom");

        // WHEN/THEN it is returned as-is
        assertThat(ExceptionUtil.unwrap(boom)).isSameAs(boom);
    }

    @Test
    void unwrap_is_null_safe() {
        assertThat(ExceptionUtil.unwrap(null)).isNull();
    }

    @Test
    void unwrap_terminates_on_a_cyclic_cause_chain() {
        // GIVEN two envelopes whose causes point at each other — anonymous subclasses, because
        // the no-arg constructors of both envelope types are protected
        var a = new ExecutionException() {};
        var b = new CompletionException() {};
        a.initCause(b);
        b.initCause(a);

        // WHEN — on a daemon probe with a bounded join: a non-terminating regression must fail
        // the test, not hang the suite (@Timeout cannot kill a non-interruptible loop)
        var result = new AtomicReference<Throwable>();
        var probe = new Thread(() -> result.set(ExceptionUtil.unwrap(a)), "unwrap-cycle-probe");
        probe.setDaemon(true);
        probe.start();
        try {
            probe.join(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // THEN it terminated within the bound and returned a non-null throwable
        assertFalse(probe.isAlive(), "unwrap must terminate on a cyclic cause chain");
        assertThat(result.get()).isNotNull();
    }

    // ------------------------------------------------------------------ findCause (R5, docs/write-path-validator.md)

    @Test
    void findCause_returns_a_deeply_nested_instance() {
        // GIVEN a denial nested two levels below the outer exception
        var denial = new IllegalArgumentException("Write denied: x");
        var top = new IllegalStateException("outer", new ExecutionException(denial));

        // WHEN
        var found = ExceptionUtil.findCause(top, IllegalArgumentException.class);

        // THEN the very same instance comes out
        assertThat(found).isSameAs(denial);
    }

    @Test
    void findCause_returns_null_when_absent() {
        assertThat(ExceptionUtil.findCause(new RuntimeException("boom"), CancellationException.class)).isNull();
    }

    @Test
    void findCause_is_null_safe() {
        assertThat(ExceptionUtil.findCause(null, CancellationException.class)).isNull();
    }

    @Test
    void findCause_terminates_on_a_cyclic_cause_chain_without_a_match() {
        // GIVEN a cycle of two exceptions, neither of them the searched type
        var a = new RuntimeException("a");
        var b = new RuntimeException("b");
        a.initCause(b);
        b.initCause(a);

        // WHEN — on a daemon probe with a bounded join: a non-terminating regression must fail
        // the test, not hang the suite (@Timeout cannot kill a non-interruptible loop)
        var result = new AtomicReference<Throwable>();
        var probe = new Thread(() -> result.set(ExceptionUtil.findCause(a, IllegalArgumentException.class)), "findCause-cycle-probe");
        probe.setDaemon(true);
        probe.start();
        try {
            probe.join(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // THEN it terminated within the bound and reported the absence
        assertFalse(probe.isAlive(), "findCause must terminate on a cyclic cause chain");
        assertThat(result.get()).isNull();
    }

    @Test
    void findCause_finds_the_match_above_a_cyclic_tail() {
        // GIVEN the denial above a cycle that never contains it
        var c = new RuntimeException("c");
        var d = new RuntimeException("d");
        var denial = new IllegalArgumentException("Write denied: x");
        var top = new RuntimeException("top");
        denial.initCause(c);
        c.initCause(d);
        d.initCause(c);
        top.initCause(denial);

        // WHEN/THEN the match wins over the cycle below
        assertThat(ExceptionUtil.findCause(top, IllegalArgumentException.class)).isSameAs(denial);
    }
}
