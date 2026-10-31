package org.sterl.llmpeon.queuedmessages;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

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
}
