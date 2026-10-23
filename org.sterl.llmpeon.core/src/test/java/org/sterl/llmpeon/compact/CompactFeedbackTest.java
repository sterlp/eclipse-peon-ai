package org.sterl.llmpeon.compact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.CoreTestFixtures;
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
        // THEN — a successful compact of the agent the chat is showing rebuilds it from the memory
        assertThat(CompactFeedback.resetsChatView(CoreTestFixtures.compactedResult(), "Da Boss", "Da Boss"))
                .isTrue();
    }

    // UC-CT-11
    @Test
    void slaveCompactDoesNotResetChatView() {
        // THEN — a slave's compact leaves the chat alone: its memory is not the chat's content ...
        assertThat(CompactFeedback.resetsChatView(CoreTestFixtures.compactedResult(), "Da Mek", "Da Boss"))
                .isFalse();
        // AND — a view without an active agent never resets
        assertThat(CompactFeedback.resetsChatView(CoreTestFixtures.compactedResult(), "Da Boss", null))
                .isFalse();
    }

    // UC-CT-12
    @Test
    void failedCompactKeepsChatView() {
        // GIVEN — the active agent's compact ran, but the compressor produced no summary
        var failed = CompactResult.failedEmpty(
                new CompactResult.Stats(3, 900, 0, CompactResult.Stage.NONE, 0, 0, "compact-model", 10L, null, true),
                "compressor returned no summary for Da Boss");

        // THEN — the memory is untouched, so a rebuild would only throw away an unchanged transcript
        assertThat(CompactFeedback.resetsChatView(failed, "Da Boss", "Da Boss")).isFalse();
        // AND — a skip (nothing ran) resets neither ...
        assertThat(CompactFeedback.resetsChatView(CompactResult.skippedSmall(), "Da Boss", "Da Boss")).isFalse();
        // AND — so does a run that died before it produced any result
        assertThat(CompactFeedback.resetsChatView(null, "Da Boss", "Da Boss")).isFalse();
    }
}
