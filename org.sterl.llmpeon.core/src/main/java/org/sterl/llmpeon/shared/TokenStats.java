package org.sterl.llmpeon.shared;

import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.Nullable;

import dev.langchain4j.model.output.TokenUsage;

/**
 * Cumulative token counter — {@code sent} (input) and {@code received} (output/generated).
 * <p>
 * Thread-safe. Only real provider usage is counted: {@link #add(TokenUsage)} ignores {@code null}
 * usage and {@code null} counts, so a missing usage never moves the totals (no estimate).
 * <p>
 * Used session-wide by the header readout; designed to be reused per-agent later (see
 * {@code docs/token-usage.md} R5).
 */
public class TokenStats {

    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong received = new AtomicLong();

    /** Adds real usage only; {@code null} usage or {@code null} counts are ignored. */
    public void add(@Nullable TokenUsage usage) {
        if (usage == null) return;
        if (usage.inputTokenCount() != null) sent.addAndGet(usage.inputTokenCount());
        if (usage.outputTokenCount() != null) received.addAndGet(usage.outputTokenCount());
    }

    public long getSent() {
        return sent.get();
    }

    public long getReceived() {
        return received.get();
    }

    /** True while nothing real has been counted yet (fresh session). */
    public boolean isEmpty() {
        return sent.get() == 0 && received.get() == 0;
    }
}
