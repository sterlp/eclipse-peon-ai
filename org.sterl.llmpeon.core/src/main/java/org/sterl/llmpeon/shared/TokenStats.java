package org.sterl.llmpeon.shared;

import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.Nullable;

import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Cumulative token counter — {@code sent} (input) and {@code received} (output/generated), plus
 * cache counters for providers that report them: {@code cachedRead} (tokens served from a prompt
 * cache) and {@code cachedWrite} (tokens written into a prompt cache).
 * <p>
 * Thread-safe. Only real provider usage is counted: {@link #add(TokenUsage)} ignores {@code null}
 * usage and {@code null} counts, so a missing usage never moves the totals (no estimate).
 * <p>
 * Cache extraction is provider-specific: OpenAI reports {@code prompt_tokens_details.cached_tokens}
 * ({@link OpenAiTokenUsage}), Anthropic reports {@code cache_read_input_tokens} /
 * {@code cache_creation_input_tokens} ({@link AnthropicTokenUsage}); all other providers leave the
 * cache counters untouched (see {@code docs/caching.md} R5).
 * <p>
 * Used session-wide by the header readout; designed to be reused per-agent later (see
 * {@code docs/token-usage.md} R5).
 */
public class TokenStats {

    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong received = new AtomicLong();
    private final AtomicLong cachedRead = new AtomicLong();
    private final AtomicLong cachedWrite = new AtomicLong();

    /** Adds real usage only; {@code null} usage or {@code null} counts are ignored. */
    public void add(@Nullable TokenUsage usage) {
        if (usage == null) return;
        if (usage.inputTokenCount() != null) sent.addAndGet(usage.inputTokenCount());
        if (usage.outputTokenCount() != null) received.addAndGet(usage.outputTokenCount());
        addCache(usage);
    }

    private void addCache(TokenUsage usage) {
        if (usage instanceof OpenAiTokenUsage openAi) {
            OpenAiTokenUsage.InputTokensDetails details = openAi.inputTokensDetails();
            if (details != null && details.cachedTokens() != null) cachedRead.addAndGet(details.cachedTokens());
        } else if (usage instanceof AnthropicTokenUsage anthropic) {
            if (anthropic.cacheReadInputTokens() != null) cachedRead.addAndGet(anthropic.cacheReadInputTokens());
            if (anthropic.cacheCreationInputTokens() != null) cachedWrite.addAndGet(anthropic.cacheCreationInputTokens());
        }
    }

    public long getSent() {
        return sent.get();
    }

    public long getReceived() {
        return received.get();
    }

    /** Tokens served from a prompt cache (OpenAI {@code cachedTokens}, Anthropic {@code cacheReadInputTokens}). */
    public long getCachedRead() {
        return cachedRead.get();
    }

    /** Tokens written into a prompt cache (Anthropic {@code cacheCreationInputTokens}). */
    public long getCachedWrite() {
        return cachedWrite.get();
    }

    /** True while nothing real has been counted yet (fresh session). */
    public boolean isEmpty() {
        return sent.get() == 0 && received.get() == 0;
    }
}
