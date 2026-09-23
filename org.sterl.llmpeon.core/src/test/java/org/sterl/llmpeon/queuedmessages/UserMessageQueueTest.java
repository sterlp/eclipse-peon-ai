package org.sterl.llmpeon.queuedmessages;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class UserMessageQueueTest {

    // ========== Rule 1: Join Short Burst Messages ==========

    @Test
    void shortMessagesWithinWindow_joinIntoSingleEntry() throws InterruptedException {
        // GIVEN a queue with 200ms window
        var queue = new UserMessageQueue(200);

        // WHEN - rapid fire messages within window
        queue.add("Hello");
        Thread.sleep(50);
        queue.add("world");
        Thread.sleep(50);
        queue.add("how are you?");

        // THEN - all joined into single entry with newlines, total well under 300
        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.pollNext().text())
                .isEqualTo("Hello" + System.lineSeparator() + "world" + System.lineSeparator() + "how are you?");
    }

    @Test
    void slidingWindowReset_allowsContinuousRapidFire() throws InterruptedException {
        // GIVEN a user has sent a message
        var queue = new UserMessageQueue(200);
        queue.add("first");

        // WHEN - send within window (resets timer), then again within the next window
        Thread.sleep(100);
        queue.add("second");  // resets sliding window
        Thread.sleep(50);
        queue.add("third");   // still within new window

        // THEN - all joined together (timer reset on "second" allowed "third" to merge)
        assertThat(queue.size()).isEqualTo(1);
        String joined = queue.pollNext().text();
        assertThat(joined).contains("first", "second", "third");
        assertThat(joined).doesNotContain(" first ", " second ", " third "); // newline joiner, not space
    }

    @Test
    void gapExceedsWindow_startsNewEntry() throws InterruptedException {
        // GIVEN
        var queue = new UserMessageQueue(200);
        queue.add("message one");

        // WHEN - gap > window from last activity
        Thread.sleep(210);
        queue.add("message two");

        // THEN - starts a new separate entry
        assertThat(queue.size()).isEqualTo(2);
        assertThat(queue.pollNext().text()).isEqualTo("message one");
        assertThat(queue.pollNext().text()).isEqualTo("message two");
    }

    @Test
    void combinedLengthCap300_forcesNewEntry() throws InterruptedException {
        // GIVEN - messages that will exceed 300 chars when joined
        var queue = new UserMessageQueue(200);
        String longButValid = "a".repeat(120);

        // WHEN
        queue.add(longButValid);           // entry 1: 120 chars
        Thread.sleep(50);
        queue.add("b".repeat(120));        // would make 241 → still under 300, merges
        Thread.sleep(50);
        queue.add("c".repeat(120));        // would make 362 → exceeds 300, new entry

        // THEN - first two joined, third is separate (length includes newline chars)
        assertThat(queue.size()).isEqualTo(2);
        String first = queue.pollNext().text();
        int nlLen = System.lineSeparator().length();
        assertThat(first.length()).isEqualTo(120 + nlLen + 120);
        assertThat(queue.pollNext().text()).isEqualTo("c".repeat(120));
    }

    @Test
    void longMessageMergesIfCapacityPermits() throws InterruptedException {
        // GIVEN - a message already in the queue (incoming length restrictions relaxed)
        var queue = new UserMessageQueue(200);
        String existingMsg = "x".repeat(100);
        queue.add(existingMsg);

        // WHEN - short message arrives well within window, fits under cap
        Thread.sleep(50);
        boolean result = queue.add("short");

        // THEN - merges into the long one (capacity permits), returns false (merged)
        assertThat(queue.size()).isEqualTo(1);
        assertThat(result).isFalse();
    }

    @Test
    void nullAndBlankMessagesIgnored() {
        var queue = new UserMessageQueue(200);

        // WHEN/THEN - nothing added, returns false
        assertThat(queue.add(null)).isFalse();
        assertThat(queue.add("")).isFalse();
        assertThat(queue.add("   ")).isFalse();
        assertThat(queue.size()).isEqualTo(0);
    }

    // ========== Rule 2: Consume Waiting Queue (FIFO) ==========

    @Test
    void pollNext_returnsInOrder() throws InterruptedException {
        // GIVEN multiple messages queued as separate entries
        var queue = new UserMessageQueue(100);
        queue.add("first");
        Thread.sleep(110);
        queue.add("second");
        Thread.sleep(110);
        queue.add("third");

        // WHEN/THEN - consumed individually in FIFO order
        assertThat(queue.pollNext().text()).isEqualTo("first");
        assertThat(queue.pollNext().text()).isEqualTo("second");
        assertThat(queue.pollNext().text()).isEqualTo("third");
        assertThat(queue.pollNext()).isNull();
    }

    @Test
    void pollNext_doesNotBatchIntoSinglePrompt() throws InterruptedException {
        // GIVEN - 3 separate queue entries
        var queue = new UserMessageQueue(100);
        queue.add("msg1");
        Thread.sleep(110);
        queue.add("msg2");
        Thread.sleep(110);
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
    void drainAll_joinsRemainingWithNewline() throws InterruptedException {
        // GIVEN - messages in queue (separate entries)
        var queue = new UserMessageQueue(100);
        queue.add("line one");
        Thread.sleep(110);
        queue.add("line two");

        // WHEN
        String result = queue.drainAll().text();

        // THEN - joined with newline, queue cleared
        assertThat(result).isEqualTo("line one" + System.lineSeparator() + "line two");
        assertThat(queue.size()).isEqualTo(0);
    }

    @Test
    void drainAll_returnsNullWhenEmpty() {
        var queue = new UserMessageQueue(200);
        assertThat(queue.drainAll()).isNull();
    }

    // ========== Rule 5: Compaction Survival & Clear Reset ==========

    @Test
    void clear_removesAllMessagesAndResetsTimer() throws InterruptedException {
        // GIVEN - queued messages exist
        var queue = new UserMessageQueue(200);
        queue.add("message");

        // WHEN - user clicks "Clear"
        queue.clear();

        // THEN - queue emptied, no stale follow-up can fire
        assertThat(queue.size()).isEqualTo(0);
        assertThat(queue.pollNext()).isNull();
    }

    @Test
    void add_returnsTrue_forNewEntry() {
        var queue = new UserMessageQueue(200);
        boolean result = queue.add("hello");
        assertThat(result).isTrue(); // new entry created → UI shows "Noted..."
    }

    @Test
    void add_returnsFalse_whenSilentlyMerged() throws InterruptedException {
        // GIVEN a queue with short window
        var queue = new UserMessageQueue(200);
        queue.add("first");

        // WHEN message arrives within window and merges silently
        Thread.sleep(50);
        boolean result = queue.add("second");

        // THEN returns false (merged, not new entry) → UI suppresses "Noted..."
        assertThat(result).isFalse();
    }

    @Test
    void add_returnsTrue_whenCapExceeded() throws InterruptedException {
        // GIVEN a queue with short window
        var queue = new UserMessageQueue(200);
        queue.add("a".repeat(200));

        // WHEN another long message arrives (cap exceeded)
        Thread.sleep(50);
        boolean result = queue.add("b".repeat(200));

        // THEN returns true (new entry created, cap exceeded) → UI shows "Noted..."
        assertThat(result).isTrue();
    }

    @Test
    void add_returnsTrue_whenWindowExpired() throws InterruptedException {
        // GIVEN a queue with short window
        var queue = new UserMessageQueue(100);
        queue.add("first");

        // WHEN gap exceeds window
        Thread.sleep(110);
        boolean result = queue.add("second");

        // THEN returns true (new entry, window expired) → UI shows "Noted..."
        assertThat(result).isTrue();
    }

    // ========== Rule 9: Queued-At Disclosure ==========

    @Test
    void entriesCarryQueuedAtFromFirstAdd() {
        // GIVEN a fixed clock so the queue time is deterministic
        var clock = fixedClock();
        var queue = new UserMessageQueue(200, clock);

        // WHEN - two messages arrive within the window and merge
        queue.add("first");
        queue.add("second");

        // THEN - the merged entry keeps the FIRST entry's queuedAt
        var entry = queue.pollNext();
        assertThat(entry.text()).isEqualTo("first" + System.lineSeparator() + "second");
        assertThat(entry.queuedAt()).isEqualTo(clock.millis());
    }

    @Test
    void newEntryAfterWindowGetsNewTimestamp() {
        // GIVEN a clock I can advance past the batch window
        var clock = new MutableClock();
        var queue = new UserMessageQueue(100, clock);

        // WHEN - first message, then one after the window expires
        queue.add("first");
        clock.advance(150); // > 100ms window
        queue.add("second");

        // THEN - two entries; the second is stamped with the LATER time
        assertThat(queue.size()).isEqualTo(2);
        assertThat(queue.pollNext().queuedAt()).isEqualTo(BASE_EPOCH_MS);
        assertThat(queue.pollNext().queuedAt()).isEqualTo(BASE_EPOCH_MS + 150);
    }

    @Test
    void drainAllReturnsFirstEntryQueuedAt() {
        // GIVEN a clock I can advance so the two entries have different times
        var clock = new MutableClock();
        var queue = new UserMessageQueue(100, clock);
        queue.add("line one");
        clock.advance(150);
        queue.add("line two");

        // WHEN
        var drained = queue.drainAll();

        // THEN - joined text, but the FIRST entry's timestamp
        assertThat(drained.text()).isEqualTo("line one" + System.lineSeparator() + "line two");
        assertThat(drained.queuedAt()).isEqualTo(BASE_EPOCH_MS);
    }

    @Test
    void queuedLabelFormatsHHmmInClockZone() {
        // GIVEN a fixed clock at 14:32 UTC
        var clock = fixedClock();
        var queue = new UserMessageQueue(100, clock);

        // WHEN
        String label = queue.queuedLabel(clock.millis());

        // THEN - rendered as HH:mm in the clock's zone
        assertThat(label).isEqualTo("14:32");
    }

    // ========== Thread Safety ==========

    @Test
    void concurrentAdds_areThreadSafe() throws Exception {
        var queue = new UserMessageQueue(200);
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

    // ========== Rule 9 test helpers ==========

    private static final long BASE_EPOCH_MS = Instant.parse("2026-09-23T14:32:00Z").toEpochMilli();

    /** A fixed clock at 14:32 UTC (deterministic queuedAt + HH:mm label). */
    private static Clock fixedClock() {
        return Clock.fixed(Instant.ofEpochMilli(BASE_EPOCH_MS), ZoneOffset.UTC);
    }

    /** A controllable clock: starts at 14:32 UTC, advanced explicitly by the test. */
    private static final class MutableClock extends Clock {
        private final AtomicLong millis = new AtomicLong(BASE_EPOCH_MS);

        long advance(long ms) { return millis.addAndGet(ms); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis.get()); }
        @Override public long millis() { return millis.get(); }
    }
}
