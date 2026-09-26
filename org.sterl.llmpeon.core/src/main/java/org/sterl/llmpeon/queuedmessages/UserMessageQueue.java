package org.sterl.llmpeon.queuedmessages;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;

public class UserMessageQueue {

    /**
     * Prefix of the Rule 9 LLM marker — the single home of this literal. The compact stage-1
     * "last real user message" check excludes messages starting with it (docs/compact.md R-CIB-4).
     */
    public static final String QUEUED_MARKER_PREFIX = "[Queued Message] ";

    /** A queued message: its text plus the instant it was queued (Rule 9 Queued-At Disclosure). */
    public record QueuedMessage(String text, long queuedAt) {}

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final Deque<QueuedMessage> queue = new ArrayDeque<>();
    private volatile long batchStartTime = 0;
    private final long batchWindowMs;
    private final Clock clock;

    public UserMessageQueue() { this(10_000); } // Production default: 10s window

    /** @param batchWindowMs configurable window for tests (e.g. 250ms) */
    public UserMessageQueue(long batchWindowMs) { this(batchWindowMs, Clock.systemDefaultZone()); }

    /**
     * @param clock injectable clock (tests) that stamps {@code queuedAt} and renders the
     *        {@code HH:mm} label — production uses the system default-zone clock.
     */
    public UserMessageQueue(long batchWindowMs, Clock clock) {
        this.batchWindowMs = batchWindowMs;
        this.clock = clock;
    }

    /**
     * Add a message to the queue, optionally merging with the last entry within the sliding window.
     * @return true if a new queue entry was created, false if silently merged into existing batch
     */
    public synchronized boolean add(String message) {
        if (message == null || message.isBlank()) return false;
        long now = clock.millis();

        boolean startNewBatch = queue.isEmpty() || (now - batchStartTime > batchWindowMs);
        String combined = message;
        long queuedAt = now;

        // Allow merging even for longer incoming messages, as long as capacity permits
        if (!startNewBatch) {
            QueuedMessage last = queue.removeLast();
            String sep = System.lineSeparator();
            int newLen = last.text().length() + sep.length() + message.length();
            if (newLen <= 300) {
                combined = last.text() + sep + message;
                queuedAt = last.queuedAt(); // burst-join keeps the FIRST entry's time (Rule 9)
                startNewBatch = false; // explicitly merged
            } else {
                queue.addLast(last); // cap exceeded, restore & add separate
                startNewBatch = true; // explicitly mark as new entry
            }
        }

        queue.addLast(new QueuedMessage(combined, queuedAt));
        batchStartTime = now; // sliding window reset
        return startNewBatch; // true if new entry created, false if silently joined
    }

    public synchronized QueuedMessage pollNext() { return queue.pollFirst(); }

    public synchronized QueuedMessage drainAll() {
        if (queue.isEmpty()) return null;
        // Burst-join shows the FIRST entry's time (Rule 9)
        long firstQueuedAt = queue.peekFirst().queuedAt();
        String combined = String.join(System.lineSeparator(),
                queue.stream().map(QueuedMessage::text).toList());
        queue.clear();
        batchStartTime = 0;
        return new QueuedMessage(combined, firstQueuedAt);
    }

    /** Renders a stored {@code queuedAt} as {@code HH:mm} in this queue's clock zone (Rule 9). */
    public String queuedLabel(long queuedAt) {
        return Instant.ofEpochMilli(queuedAt).atZone(clock.getZone()).format(HHMM);
    }

    public synchronized int size() { return queue.size(); }
    public synchronized void clear() { queue.clear(); batchStartTime = 0; }
}
