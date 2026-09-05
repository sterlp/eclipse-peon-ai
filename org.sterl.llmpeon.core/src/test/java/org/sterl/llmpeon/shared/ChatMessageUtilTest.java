package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatMessageUtilTest {

    // R21 — the estimator the live status uses for per-chunk token counting.

    @Test
    void estimates_long_snippet_by_chars_over_3() {
        // GIVEN a 32-char snippet (R21 BDD)
        // WHEN
        int tokens = ChatMessageUtil.estimateTokens("a".repeat(32));
        // THEN 32 / 3 = 10
        assertThat(tokens).isEqualTo(10);
    }

    @Test
    void estimates_26_chars_to_8() {
        // GIVEN a 26-char snippet (R21 BDD)
        // WHEN
        int tokens = ChatMessageUtil.estimateTokens("b".repeat(26));
        // THEN 26 / 3 = 8
        assertThat(tokens).isEqualTo(8);
    }

    @Test
    void estimates_short_snippet_to_one() {
        // GIVEN a snippet of 5 chars or less (R21 BDD: "Hi" → 1)
        // WHEN
        int tokens = ChatMessageUtil.estimateTokens("Hi");
        // THEN any non-empty snippet up to 5 chars counts as one token
        assertThat(tokens).isEqualTo(1);
    }

    @Test
    void boundary_five_chars_is_one() {
        // GIVEN a snippet of exactly 5 chars
        // WHEN
        int tokens = ChatMessageUtil.estimateTokens("12345");
        // THEN still the short-snippet floor
        assertThat(tokens).isEqualTo(1);
    }

    @Test
    void boundary_six_chars_is_two() {
        // GIVEN a snippet of exactly 6 chars — first length past the short floor
        // WHEN
        int tokens = ChatMessageUtil.estimateTokens("123456");
        // THEN 6 / 3 = 2
        assertThat(tokens).isEqualTo(2);
    }

    @Test
    void empty_string_counts_zero() {
        // GIVEN an empty snippet (no content to count)
        // WHEN
        int tokens = ChatMessageUtil.estimateTokens("");
        // THEN
        assertThat(tokens).isZero();
    }

    @Test
    void null_counts_zero() {
        // GIVEN no snippet at all
        // WHEN
        int tokens = ChatMessageUtil.estimateTokens((String) null);
        // THEN a missing snippet never contributes tokens
        assertThat(tokens).isZero();
    }
}
