package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;

class TokenStatsTest {

    @Test
    void starts_empty() {
        var stats = new TokenStats();
        assertThat(stats.isEmpty()).isTrue();
        assertThat(stats.getSent()).isZero();
        assertThat(stats.getReceived()).isZero();
    }

    @Test
    void accumulates_sent_and_received() {
        // GIVEN
        var stats = new TokenStats();
        // WHEN
        stats.add(new TokenUsage(10, 3, 13));
        stats.add(new TokenUsage(20, 7, 27));
        // THEN
        assertThat(stats.getSent()).isEqualTo(30);
        assertThat(stats.getReceived()).isEqualTo(10);
        assertThat(stats.isEmpty()).isFalse();
    }

    @Test
    void ignores_null_usage() {
        var stats = new TokenStats();
        stats.add(null);
        assertThat(stats.isEmpty()).isTrue();
    }

    @Test
    void ignores_missing_counts_no_estimate() {
        // GIVEN a usage without input/output counts (provider returned nothing usable)
        var stats = new TokenStats();
        // WHEN
        stats.add(new TokenUsage()); // all counts null
        // THEN totals stay untouched — never estimated
        assertThat(stats.isEmpty()).isTrue();
    }

    @Test
    void addsOpenAiCachedTokens() {
        // GIVEN an OpenAI usage with prompt_tokens_details.cached_tokens
        var usage = OpenAiTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder().cachedTokens(40).build())
                .build();
        var stats = new TokenStats();
        // WHEN
        stats.add(usage);
        // THEN
        assertThat(stats.getCachedRead()).isEqualTo(40);
        assertThat(stats.getCachedWrite()).isZero();
        assertThat(stats.getSent()).isEqualTo(100);
        assertThat(stats.getReceived()).isEqualTo(50);
    }

    @Test
    void addsAnthropicCacheReadAndCreation() {
        // GIVEN an Anthropic usage with cache_read_input_tokens / cache_creation_input_tokens
        var usage = AnthropicTokenUsage.builder()
                .inputTokenCount(60)
                .outputTokenCount(10)
                .cacheReadInputTokens(30)
                .cacheCreationInputTokens(10)
                .build();
        var stats = new TokenStats();
        // WHEN
        stats.add(usage);
        // THEN
        assertThat(stats.getCachedRead()).isEqualTo(30);
        assertThat(stats.getCachedWrite()).isEqualTo(10);
    }

    @Test
    void plainUsage_leavesCacheCountersZero() {
        // GIVEN a plain usage from a provider without cache reporting
        var stats = new TokenStats();
        // WHEN
        stats.add(new TokenUsage(10, 3, 13));
        // THEN
        assertThat(stats.getCachedRead()).isZero();
        assertThat(stats.getCachedWrite()).isZero();
    }
}
