package org.sterl.llmpeon.compact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
}
