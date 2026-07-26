package org.sterl.llmpeon.queuedmessages;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class UserMessageQueueTest {

    // ========== Rule 1: Join Short Burst Messages ==========

    @Test
    void shortMessagesWithinWindow_joinIntoSingleEntry() throws InterruptedException {
        // GIVEN
        var queue = new UserMessageQueue();

        // WHEN - rapid fire short messages within 10s window
        queue.add("Hello");       // 5 chars
        Thread.sleep(500);
        queue.add("world");       // 5 chars
        Thread.sleep(500);
        queue.add("how are you?"); // 12 chars

        // THEN - all joined into single entry (total well under 300)
        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.pollNext()).isEqualTo("Hello world how are you?");
    }

    @Test
    void slidingWindowReset_allowsContinuousRapidFire() throws InterruptedException {
        // GIVEN - a user has sent a message
        var queue = new UserMessageQueue();
        queue.add("first");

        // WHEN - send after 9 seconds (resets timer), then again within the next window
        Thread.sleep(9_100);
        queue.add("second");      // resets sliding window
        Thread.sleep(500);
        queue.add("third");       // still within new window

        // THEN - all joined together (timer reset on "second" allowed "third" to merge)
        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.pollNext()).contains("first", "second", "third");
    }

    @Test
    void gapExceedsWindow_startsNewEntry() throws InterruptedException {
        // GIVEN
        var queue = new UserMessageQueue();
        queue.add("message one");

        // WHEN - gap > 10s from last activity
        Thread.sleep(10_500);
        queue.add("message two");

        // THEN - starts a new separate entry
        assertThat(queue.size()).isEqualTo(2);
        assertThat(queue.pollNext()).isEqualTo("message one");
        assertThat(queue.pollNext()).isEqualTo("message two");
    }

    @Test
    void combinedLengthCap300_forcesNewEntry() throws InterruptedException {
        // GIVEN - messages that will exceed 300 chars when joined
        var queue = new UserMessageQueue();
        String longButValid = "a".repeat(120);  // exactly 120, valid for merging

        // WHEN
        queue.add(longButValid);           // entry 1: 120 chars
        Thread.sleep(500);
        queue.add("b".repeat(120));        // would make 241 → still under 300, merges
        Thread.sleep(500);
        queue.add("c".repeat(120));        // would make 362 → exceeds 300, new entry

        // THEN - first two joined (241 chars), third is separate
        assertThat(queue.size()).isEqualTo(2);
        String first = queue.pollNext();
        assertThat(first.length()).isEqualTo(241); // 120 + 1 space + 120
        assertThat(queue.pollNext()).isEqualTo("c".repeat(120));
    }

    @Test
    void longMessageActsAsDivider() throws InterruptedException {
        // GIVEN - a message >120 chars already in the queue
        var queue = new UserMessageQueue();
        String longMsg = "x".repeat(150);  // exceeds 120 char threshold
        queue.add(longMsg);

        // WHEN - short message arrives within window
        Thread.sleep(500);
        queue.add("short");

        // THEN - short message does NOT merge into the long one
        assertThat(queue.size()).isEqualTo(2);
        assertThat(queue.pollNext()).isEqualTo(longMsg);
        assertThat(queue.pollNext()).isEqualTo("short");
    }

    @Test
    void longMessageStartsFreshEntry() throws InterruptedException {
        // GIVEN - short messages already queued and joined
        var queue = new UserMessageQueue();
        queue.add("first");
        Thread.sleep(500);
        queue.add("second");  // joins with first

        // WHEN - a long message (>120 chars) arrives within window
        Thread.sleep(500);
        String longMsg = "y".repeat(130);
        queue.add(longMsg);

        // THEN - long message starts new entry, doesn't merge into existing
        assertThat(queue.size()).isEqualTo(2);
        assertThat(queue.pollNext()).isEqualTo("first second");
        assertThat(queue.pollNext()).isEqualTo(longMsg);
    }

    @Test
    void nullAndBlankMessagesIgnored() {
        // GIVEN
        var queue = new UserMessageQueue();

        // WHEN
        queue.add(null);
        queue.add("");
        queue.add("   ");
        queue.add("\t\n");

        // THEN - nothing added
        assertThat(queue.size()).isEqualTo(0);
    }

    // ========== Rule 2: Consume Waiting Queue (FIFO) ==========

    @Test
    void pollNext_returnsInOrder() throws InterruptedException {
        // GIVEN multiple messages queued
        var queue = new UserMessageQueue();
        queue.add("first");
        Thread.sleep(10_500);  // force new entries
        queue.add("second");
        Thread.sleep(10_500);
        queue.add("third");

        // WHEN/THEN - consumed individually in FIFO order
        assertThat(queue.pollNext()).isEqualTo("first");
        assertThat(queue.pollNext()).isEqualTo("second");
        assertThat(queue.pollNext()).isEqualTo("third");
        assertThat(queue.pollNext()).isNull();
    }

    @Test
    void pollNext_doesNotBatchIntoSinglePrompt() throws InterruptedException {
        // GIVEN - 3 separate queue entries
        var queue = new UserMessageQueue();
        queue.add("msg1");
        Thread.sleep(10_500);
        queue.add("msg2");
        Thread.sleep(10_500);
        queue.add("msg3");

        // WHEN/THEN - each consumed individually, size decreases one-by-one
        assertThat(queue.size()).isEqualTo(3);
        queue.pollNext();
        assertThat(queue.size()).isEqualTo(2);
        queue.pollNext();
        assertThat(queue.size()).isEqualTo(1);
    }

    // ========== Rule 4: Drain Queue on STOP / Error / RateLimit ==========

    @Test
    void drainAll_joinsWithNewline() throws InterruptedException {
        // GIVEN - messages in queue (separate entries due to >10s gaps)
        var queue = new UserMessageQueue();
        queue.add("line one");
        Thread.sleep(10_500);
        queue.add("line two");

        // WHEN
        String result = queue.drainAll();

        // THEN - joined with newline, queue cleared
        assertThat(result).isEqualTo("line one" + System.lineSeparator() + "line two");
        assertThat(queue.size()).isEqualTo(0);
    }

    @Test
    void drainAll_returnsNullWhenEmpty() {
        var queue = new UserMessageQueue();
        assertThat(queue.drainAll()).isNull();
    }

    // ========== Rule 5: Clear Reset ==========

    @Test
    void clear_removesAllMessagesAndResetsTimer() throws InterruptedException {
        // GIVEN - queued messages exist
        var queue = new UserMessageQueue();
        queue.add("message");

        // WHEN - user clicks "Clear"
        queue.clear();

        // THEN - queue emptied, no stale follow-up can fire
        assertThat(queue.size()).isEqualTo(0);
        assertThat(queue.pollNext()).isNull();
    }

    // ========== Thread Safety ==========

    @Test
    void concurrentAdds_areThreadSafe() throws Exception {
        var queue = new UserMessageQueue();
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    queue.add("msg-" + index);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // release all threads at once
        assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // All messages should be present (may be batched or separate depending on timing)
        assertThat(queue.size()).isGreaterThan(0);
    }
}
