package org.sterl.llmpeon.queuedmessages;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.agent.model.TaggedResult;

/**
 * Rule 9 Queued-At Disclosure: the payload marker and the disclosure line of one entry carry the
 * same queue time, and the message text stays unchanged behind it.
 */
class QueuedUserMessagesTest {

    /** 14:32 UTC — the queue time every rendered line has to show. */
    private static final long AT_1432 = Instant.parse("2026-09-23T14:32:00Z").toEpochMilli();

    @Test
    void markerAndReadingLineCarryTheQueueTimeInHHmm() {
        // GIVEN an entry queued at 14:32 in the queue's clock zone
        var queue = new UserMessageQueue(10_000, Clock.fixed(Instant.ofEpochMilli(AT_1432), ZoneOffset.UTC));
        queue.add("bitte abbrechen");
        var entry = queue.pollNext();

        // WHEN the LLM payload marker and the disclosure line are rendered
        var marker = QueuedUserMessages.marker(queue, entry);
        var readingLine = QueuedUserMessages.readingLine(queue, entry);

        // THEN - marker: prefix + (HH:mm) + unchanged text
        assertThat(marker).isEqualTo("[Queued Message] (14:32): bitte abbrechen");
        // AND - disclosure line: same time, own label
        assertThat(readingLine).isEqualTo("Reading queued User message: bitte abbrechen (queued 14:32)");
        // AND - the marker prefix is still the queue's constant, not a second literal
        assertThat(marker).startsWith(UserMessageQueue.QUEUED_MARKER_PREFIX);
    }

    @Test
    void aMultiLineBatchIsRenderedVerbatimInBothLines() {
        // GIVEN a burst joined into one entry (Rule 1) — its text contains a line separator
        var queue = new UserMessageQueue(10_000, Clock.fixed(Instant.ofEpochMilli(AT_1432), ZoneOffset.UTC));
        queue.add("msg2");
        queue.add("msg3");
        var entry = queue.pollNext();

        // WHEN/THEN - both renderings carry the joined text unchanged, only the label differs
        assertThat(QueuedUserMessages.marker(queue, entry))
                .isEqualTo("[Queued Message] (14:32): msg2" + System.lineSeparator() + "msg3");
        assertThat(QueuedUserMessages.readingLine(queue, entry))
                .isEqualTo("Reading queued User message: msg2" + System.lineSeparator() + "msg3 (queued 14:32)");
    }

    /** Rule 11 body line: the block head already names the sender, so no [Queued Message] prefix. */
    @Test
    void bodyLineCarriesTheTimeAndTheTextWithoutTheMarkerPrefix() {
        // GIVEN
        var queue = new UserMessageQueue(10_000, Clock.fixed(Instant.ofEpochMilli(AT_1432), ZoneOffset.UTC));
        queue.addUrgent("bitte abbrechen");

        // WHEN
        var line = QueuedUserMessages.bodyLine(queue, queue.drainUrgent().getFirst());

        // THEN
        assertThat(line).isEqualTo("(queued 14:32): bitte abbrechen");
        assertThat(line).doesNotContain(UserMessageQueue.QUEUED_MARKER_PREFIX);
    }

    @Test
    void blockWrapsAllBodyLinesInOneHeadAndFoot() {
        // GIVEN the lines of two urgent entries
        var block = QueuedUserMessages.block(List.of("(queued 14:32): u1", "(queued 14:32): u2"));

        // THEN - one block, both lines inside, start and end line carry the same head (R-TR-1)
        assertThat(block.toString()).isEqualTo(
                TaggedResult.PREFIX + "queued_user_message" + TaggedResult.LN
                        + "(queued 14:32): u1" + TaggedResult.LN
                        + "(queued 14:32): u2" + TaggedResult.LN
                        + TaggedResult.SUFFIX + "queued_user_message");
    }

    /** An empty lane must leave a tool result untouched — never an empty block. */
    @Test
    void noLinesMeansNoBlock() {
        assertThat(QueuedUserMessages.block(List.of())).isNull();
        assertThat(QueuedUserMessages.block(null)).isNull();
    }
}
