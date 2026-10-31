package org.sterl.llmpeon.agent.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaggedResultTest {

    /**
     * R-TR-6: the system-prompt INFO is the one place a model learns the block format AND that a
     * {@code queued_user_message} is a real user message — a producer without that sentence would
     * deliver a block the model is told nothing about.
     */
    @Test
    void infoNamesTheQueuedUserMessageBlock() {
        // THEN — the example block teaches both lines of a block, symmetrically
        assertThat(TaggedResult.INFO)
                .contains(TaggedResult.PREFIX + "<head>", TaggedResult.SUFFIX + "<head>");
        // AND — the head a real producer uses is named, with what it means
        assertThat(TaggedResult.INFO)
                .contains(TaggedResult.TAG_QUEUED_USER_MESSAGE)
                .contains("real user message");
    }

    /** R-TR-2: only a text that STARTS with the head and ENDS with it is a pure block. */
    @Test
    void aToolOutputCarryingAnEmbeddedBlockIsNotAPureBlock() {
        var block = new TaggedResult("queued_user_message", "(queued 14:32): u1").toString();

        assertThat(TaggedResult.isTagged(block)).isTrue();
        // AND — what the tool loop really sends: tool output first, block behind it (R11/R-TR-2)
        assertThat(TaggedResult.isTagged("tool-output" + TaggedResult.LN + TaggedResult.LN + block)).isFalse();
    }
}
