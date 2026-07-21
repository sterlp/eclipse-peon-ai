package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
}
