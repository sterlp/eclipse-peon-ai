package org.sterl.llmpeon.compact;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.CoreTestFixtures;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.shared.CallStats;

class CompactFeedbackTest {

    // UC-CT-9
    @Test
    void startLineNamesAgentAndNumbers() {
        // WHEN — the start line of Da Mek's compact over 3 messages, estimated 124 tokens
        var line = CompactFeedback.startLine("Da Mek", 3, 124, null);

        // THEN — it names the agent next to the emoji, plus count and estimate (R-CT-6)
        assertThat(line).isEqualTo("🗜 Compressing Da Mek: 3 messages 124 tokens (estimate)");
        // AND — the shared prefix is the key the UI and the absence-assertions read
        assertThat(CompactFeedback.START_PREFIX).isEqualTo("🗜 Compressing ");
        assertThat(line).startsWith(CompactFeedback.START_PREFIX);
    }

    // UC-CT-9
    @Test
    void startLineNamesTheCompactModelOnlyWhenSet() {
        // THEN — the COMPACT slot's own model is named when it has one ...
        assertThat(CompactFeedback.startLine("Da Boss", 12, 78_000, "compact-model"))
                .isEqualTo("🗜 Compressing Da Boss: 12 messages 78000 tokens (estimate) using compact-model");
        // AND — empty means unset: never a dangling "using " without a model
        assertThat(CompactFeedback.startLine("Da Boss", 12, 1, "  ")).doesNotContain(" using ");
    }

    // UC-CT-13
    @Test
    void endLineNamesAgentNumbersTimeAndChars() {
        // GIVEN — a COMPACTED result over 6 messages estimated at 969 tokens, summary 1200 chars,
        // and a compact that started at 09:40 UTC and took 4 s (own clock, so the suffix is pinned)
        var clock = new MutableClock();
        var callStats = new CallStats(clock);
        clock.advance(Duration.ofMillis(4200));
        var result = CompactResult.compacted(
                new CompactResult.Stats(6, 969, 240, CompactResult.Stage.THINK_AND_USER, 120, 1200,
                        "compact-model", 4000L, null, true),
                "x".repeat(1200));

        // WHEN — the end line for the memory as it stands after the re-seed: 2 messages, ~210 tokens
        var line = CompactFeedback.endLine("Da Mek", result, 2, 210, callStats);

        // THEN — agent, before → after numbers, the shared CallStats suffix and the result size
        assertThat(line).isEqualTo(
                "🗜 Da Mek compacted: 6 → 2 messages, ~969 → ~210 tokens (4s, 09:40) (1200 chars)");
        // AND — the shared marker is what production and the absence-assertions key on
        assertThat(CompactFeedback.END_MARKER).isEqualTo(" compacted: ");
        assertThat(line).contains(CompactFeedback.END_MARKER);
    }

    // UC-CT-13
    @Test
    void endLineRendersSubThousandTokensRaw() {
        // GIVEN — a small compact whose result is under a thousand tokens
        var result = CompactResult.compacted(
                new CompactResult.Stats(4, 900, 300, CompactResult.Stage.NONE, 0, 40, "compact-model",
                        10L, null, true),
                "short");

        // THEN — both figures render raw, never rounded away by the k-abbreviation
        assertThat(CompactFeedback.endLine("Da Boss", result, 2, 900, new CallStats(new MutableClock())))
                .contains("~900 → ~900 tokens").doesNotContain("0k");
    }


    // UC-CT-10
    @Test
    void resetsChatForSuccessfulCompactOfTheShownAgent() {
        assertThat(CompactFeedback.resetsChat(CoreTestFixtures.compactedResult(), "Da Boss", "Da Boss")).isTrue();
    }

    // UC-CT-11
    @Test
    void resetsChatNeverForAnotherAgent() {
        assertThat(CompactFeedback.resetsChat(CoreTestFixtures.compactedResult(), "Da Mek", "Da Boss")).isFalse();
        assertThat(CompactFeedback.resetsChat(CoreTestFixtures.compactedResult(), "Da Boss", null)).isFalse();
    }

    // UC-CT-12
    @Test
    void resetsChatNeverWithoutSuccess() {
        var failed = CompactResult.failedEmpty(
                new CompactResult.Stats(3, 900, 0, CompactResult.Stage.NONE, 0, 0, "compact-model", 10L, null, true),
                "compressor returned no summary for Da Boss");
        assertThat(CompactFeedback.resetsChat(failed, "Da Boss", "Da Boss")).isFalse();
        assertThat(CompactFeedback.resetsChat(CompactResult.skippedSmall(), "Da Boss", "Da Boss")).isFalse();
        assertThat(CompactFeedback.resetsChat(null, "Da Boss", "Da Boss")).isFalse();
    }

    /** Fixed clock the tests advance manually — pins {@link CallStats#suffix()} (Muster CallStatsTest). */
    static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-10-24T09:40:00Z");
        void advance(Duration d) { instant = instant.plus(d); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
