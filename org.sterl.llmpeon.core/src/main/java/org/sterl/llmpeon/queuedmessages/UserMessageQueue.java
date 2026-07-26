package org.sterl.llmpeon.queuedmessages;

import java.util.ArrayDeque;
import java.util.Deque;

public class UserMessageQueue {
    private final Deque<String> queue = new ArrayDeque<>();
    private volatile long batchStartTime = 0;
    private final long batchWindowMs;

    public UserMessageQueue() { this(10_000); } // Production default: 10s window

    /** @param batchWindowMs configurable window for tests (e.g. 250ms) */
    public UserMessageQueue(long batchWindowMs) { this.batchWindowMs = batchWindowMs; }

    /**
     * Add a message to the queue, optionally merging with the last entry within the sliding window.
     * @return true if a new queue entry was created, false if silently merged into existing batch
     */
    public synchronized boolean add(String message) {
        if (message == null || message.isBlank()) return false;
        long now = System.currentTimeMillis();

        boolean startNewBatch = queue.isEmpty() || (now - batchStartTime > batchWindowMs);
        String combined = message;

        // Allow merging even for longer incoming messages, as long as capacity permits
        if (!startNewBatch) {
            String last = queue.removeLast();
            String sep = System.lineSeparator();
            int newLen = last.length() + sep.length() + message.length();
            if (newLen <= 300) {
                combined = last + sep + message;
                startNewBatch = false; // explicitly merged
            } else {
                queue.addLast(last); // cap exceeded, restore & add separate
                startNewBatch = true; // explicitly mark as new entry
            }
        }

        queue.addLast(combined);
        batchStartTime = now; // sliding window reset
        return startNewBatch; // true if new entry created, false if silently joined
    }

    public synchronized String pollNext() { return queue.pollFirst(); }

    public synchronized String drainAll() {
        if (queue.isEmpty()) return null;
        String combined = String.join(System.lineSeparator(), queue);
        queue.clear();
        batchStartTime = 0;
        return combined;
    }

    public synchronized int size() { return queue.size(); }
    public synchronized void clear() { queue.clear(); batchStartTime = 0; }
}
