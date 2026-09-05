package org.sterl.llmpeon.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import org.sterl.llmpeon.shared.OnPartialAiResponse.Type;

class LiveStatusTest {

    private Instant at(int hour, int minute) {
        return LocalTime.of(hour, minute).atDate(LocalDate.of(2026, 9, 5)).atZone(ZoneId.systemDefault()).toInstant();
    }

    private OnPartialAiResponse chunk(Type type, Instant startedAt, long tokenPhaseStart) {
        return new OnPartialAiResponse(type, "x", startedAt, tokenPhaseStart);
    }

    @Test
    void start_shows_started_time_waiting_and_zero_rate() {
        // GIVEN a START chunk whose turn started at 09:07 local
        Instant startedAt = at(9, 7);
        OnPartialAiResponse start = new OnPartialAiResponse(Type.START, null, startedAt, 0);
        // WHEN
        LiveStatus status = LiveStatus.of(start, 0, startedAt.toEpochMilli(), 0);
        // THEN the full line, with a leading zero and no rate yet
        assertThat(status.state()).isEqualTo("Started 09:07 · waiting for AI...");
        assertThat(status.tokPerSec()).isZero();
    }

    @Test
    void working_line_full_think_after_3m15s() {
        // GIVEN a THINK chunk whose turn started at 14:32 local, rendered 3m 15s later (R20 BDD)
        Instant startedAt = at(14, 32);
        long now = startedAt.toEpochMilli() + 195_000;
        OnPartialAiResponse think = chunk(Type.THINK, startedAt, 0);
        // WHEN
        LiveStatus status = LiveStatus.of(think, 0, now, 0);
        // THEN the full line — wall-clock, middle-dot separator, elapsed, phase suffix
        assertThat(status.state()).isEqualTo("Started 14:32 · working since 3m 15s | thinking...");
    }

    @Test
    void under_a_minute_shows_seconds_only() {
        // GIVEN a chunk 45 s after the turn started
        OnPartialAiResponse answer = chunk(Type.ANSWER, Instant.EPOCH, 0);
        // WHEN
        LiveStatus status = LiveStatus.of(answer, 0, 45_000, 0);
        // THEN
        assertThat(status.state()).contains("working since 45s");
    }

    @Test
    void under_an_hour_shows_minutes_and_seconds() {
        // GIVEN a chunk 3m 15s after the turn started (R20 BDD)
        OnPartialAiResponse answer = chunk(Type.ANSWER, Instant.EPOCH, 0);
        // WHEN
        LiveStatus status = LiveStatus.of(answer, 0, 195_000, 0);
        // THEN
        assertThat(status.state()).contains("working since 3m 15s");
    }

    @Test
    void over_an_hour_shows_hours_minutes_seconds() {
        // GIVEN a chunk 1h 4m 5s after the turn started
        OnPartialAiResponse answer = chunk(Type.ANSWER, Instant.EPOCH, 0);
        // WHEN
        LiveStatus status = LiveStatus.of(answer, 0, 3_845_000, 0);
        // THEN
        assertThat(status.state()).contains("working since 1h 4m 5s");
    }

    @Test
    void phase_suffix_matches_chunk_type() {
        // GIVEN one chunk of each working phase, all 195 s in
        Instant startedAt = Instant.EPOCH;
        long now = 195_000;
        // WHEN / THEN
        assertThat(LiveStatus.of(chunk(Type.THINK, startedAt, 0), 0, now, 0).state()).contains("thinking...");
        assertThat(LiveStatus.of(chunk(Type.ANSWER, startedAt, 0), 0, now, 0).state()).contains("responding...");
        assertThat(LiveStatus.of(chunk(Type.TOOL, startedAt, 0), 0, now, 0).state()).contains("using tools...");
    }

    @Test
    void end_shows_done_and_no_rate() {
        // GIVEN an END chunk
        OnPartialAiResponse end = new OnPartialAiResponse(Type.END, null, Instant.EPOCH, 0);
        // WHEN
        LiveStatus status = LiveStatus.of(end, 0, 100_000, 0);
        // THEN (the widget hides on END, so this is only ever a fallback value)
        assertThat(status.state()).isEqualTo("AI done.");
        assertThat(status.tokPerSec()).isZero();
    }

    @Test
    void rate_is_tokens_over_token_phase() {
        // GIVEN 1000 tokens 2 s into the token phase (R19 BDD, doc-corrected)
        long now = 2_000_000;
        long tokenPhaseStart = now - 2_000;
        OnPartialAiResponse answer = chunk(Type.ANSWER, Instant.EPOCH, tokenPhaseStart);
        // WHEN — same call (lastTokenPhaseStart == tokenPhaseStart)
        LiveStatus status = LiveStatus.of(answer, 1000, now, tokenPhaseStart);
        // THEN 1000 / 2 s = 500
        assertThat(status.tokPerSec()).isEqualTo(500.0);
    }

    @Test
    void rate_burst_is_tokens_over_token_phase() {
        // GIVEN 500 tokens in a 200 ms burst (R19 BDD)
        long now = 2_000_000;
        long tokenPhaseStart = now - 200;
        OnPartialAiResponse answer = chunk(Type.ANSWER, Instant.EPOCH, tokenPhaseStart);
        // WHEN — same call
        LiveStatus status = LiveStatus.of(answer, 500, now, tokenPhaseStart);
        // THEN 500 / 0.2 s = 2500 — no n/a threshold, bursts show too
        assertThat(status.tokPerSec()).isEqualTo(2500.0);
    }

    @Test
    void rate_zero_while_token_phase_not_started() {
        // GIVEN a working chunk but the token phase has not started yet
        OnPartialAiResponse answer = chunk(Type.ANSWER, Instant.EPOCH, 0);
        // WHEN
        LiveStatus status = LiveStatus.of(answer, 100, 5_000, 0);
        // THEN
        assertThat(status.tokPerSec()).isZero();
    }

    @Test
    void elapsed_from_turn_rate_from_token_phase() {
        // GIVEN a chunk 1 h after the turn started but only 2 s into the token phase
        long now = 100_000_000;
        Instant startedAt = Instant.ofEpochMilli(now - 3_600_000);
        long tokenPhaseStart = now - 2_000;
        OnPartialAiResponse answer = chunk(Type.ANSWER, startedAt, tokenPhaseStart);
        // WHEN — same call
        LiveStatus status = LiveStatus.of(answer, 1000, now, tokenPhaseStart);
        // THEN elapsed reads the turn (1 h), the rate reads the token phase (2 s → 500)
        assertThat(status.state()).contains("working since 1h 0m 0s");
        assertThat(status.tokPerSec()).isEqualTo(500.0);
    }

    // ---- R22: first token of a call shows zero rate ----

    @Test
    void first_token_of_a_call_shows_zero_rate() {
        // GIVEN the first token of a call: tokenPhaseStart is set but differs from the
        //        widget's lastTokenPhaseStart (0 = no previous call in this turn)
        long tokenPhaseStart = 1_000_000;
        long now = tokenPhaseStart + 500;
        OnPartialAiResponse answer = chunk(Type.ANSWER, Instant.EPOCH, tokenPhaseStart);
        // WHEN
        LiveStatus status = LiveStatus.of(answer, 1, now, 0);
        // THEN rate is 0 — the JS hides the tok/s segment
        assertThat(status.tokPerSec()).isZero();
    }

    @Test
    void second_token_of_a_call_shows_rate() {
        // GIVEN the second token of the same call: tokenPhaseStart == lastTokenPhaseStart
        long tokenPhaseStart = 1_000_000;
        long now = tokenPhaseStart + 2_000;
        OnPartialAiResponse answer = chunk(Type.ANSWER, Instant.EPOCH, tokenPhaseStart);
        // WHEN — same call
        LiveStatus status = LiveStatus.of(answer, 100, now, tokenPhaseStart);
        // THEN rate = 100 / 2 s = 50 (R19)
        assertThat(status.tokPerSec()).isEqualTo(50.0);
    }

    @Test
    void new_call_resets_rate_to_zero() {
        // GIVEN a call switch: tokenPhaseStart changed from T1 to T2 (both non-zero)
        long previousPhaseStart = 1_000_000;
        long newPhaseStart = 2_000_000;
        long now = newPhaseStart + 300;
        OnPartialAiResponse answer = chunk(Type.ANSWER, Instant.EPOCH, newPhaseStart);
        // WHEN — different call (lastTokenPhaseStart = previous call's phase start)
        LiveStatus status = LiveStatus.of(answer, 1, now, previousPhaseStart);
        // THEN rate is 0 — each new call starts fresh (R22)
        assertThat(status.tokPerSec()).isZero();
    }
}
