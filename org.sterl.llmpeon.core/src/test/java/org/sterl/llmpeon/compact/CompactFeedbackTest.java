package org.sterl.llmpeon.compact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.CoreTestFixtures;
import org.sterl.llmpeon.compact.CompactFeedback.Timing;
import org.sterl.llmpeon.model.CompactResult;

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

    // UC-CT-10
    @Test
    void mainAgentCompactResetsChatView() {
        // THEN — a successful compact of the agent the chat is showing rebuilds it from the memory,
        // right away: no turn is in flight, so the memory already holds everything the chat shows
        assertThat(CompactFeedback.resetTiming(CoreTestFixtures.compactedResult(), "Da Boss", "Da Boss", false))
                .isEqualTo(Timing.NOW);
    }

    // UC-CT-10
    @Test
    void preTurnCompactDefersChatReset() {
        // GIVEN — the same compact of the same agent, but a turn was running when it ran: that turn's
        // fresh message is not in the memory yet (R-CC-20.2 compacts before the add)
        // THEN — the rebuild waits for the turn's onCallStart instead of swallowing the question
        assertThat(CompactFeedback.resetTiming(CoreTestFixtures.compactedResult(), "Da Boss", "Da Boss", true))
                .isEqualTo(Timing.DEFERRED);
    }

    // UC-CT-11
    @Test
    void slaveCompactDoesNotResetChatView() {
        // THEN — a slave's compact leaves the chat alone, turn or no turn: its memory is not the
        // chat's content ...
        assertThat(CompactFeedback.resetTiming(CoreTestFixtures.compactedResult(), "Da Mek", "Da Boss", false))
                .isEqualTo(Timing.NONE);
        assertThat(CompactFeedback.resetTiming(CoreTestFixtures.compactedResult(), "Da Mek", "Da Boss", true))
                .isEqualTo(Timing.NONE);
        // AND — a view without an active agent never resets, not even deferred
        assertThat(CompactFeedback.resetTiming(CoreTestFixtures.compactedResult(), "Da Boss", null, false))
                .isEqualTo(Timing.NONE);
        assertThat(CompactFeedback.resetTiming(CoreTestFixtures.compactedResult(), "Da Boss", null, true))
                .isEqualTo(Timing.NONE);
    }

    // UC-CT-12
    @Test
    void failedCompactKeepsChatView() {
        // GIVEN — the active agent's compact ran, but the compressor produced no summary
        var failed = CompactResult.failedEmpty(
                new CompactResult.Stats(3, 900, 0, CompactResult.Stage.NONE, 0, 0, "compact-model", 10L, null, true),
                "compressor returned no summary for Da Boss");

        // THEN — for every non-success and either timing: the memory is untouched, so a rebuild would
        // only throw away an unchanged transcript
        for (var withinTurn : new boolean[] { false, true }) {
            assertThat(CompactFeedback.resetTiming(failed, "Da Boss", "Da Boss", withinTurn))
                    .as("FAILED_EMPTY, withinTurn=" + withinTurn).isEqualTo(Timing.NONE);
            // AND — a skip (nothing ran) resets neither ...
            assertThat(CompactFeedback.resetTiming(CompactResult.skippedSmall(), "Da Boss", "Da Boss", withinTurn))
                    .as("SKIPPED_SMALL, withinTurn=" + withinTurn).isEqualTo(Timing.NONE);
            // AND — so does a run that died before it produced any result
            assertThat(CompactFeedback.resetTiming(null, "Da Boss", "Da Boss", withinTurn))
                    .as("no result at all, withinTurn=" + withinTurn).isEqualTo(Timing.NONE);
        }
    }
}
