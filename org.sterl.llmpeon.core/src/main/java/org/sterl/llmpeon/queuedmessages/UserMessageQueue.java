package org.sterl.llmpeon.queuedmessages;

import java.util.ArrayDeque;
import java.util.Deque;

public class UserMessageQueue {
    private final Deque<String> queue = new ArrayDeque<>();
    private volatile long batchStartTime = 0;

    public synchronized void add(String message) {
        if (message == null || message.isBlank()) return;
        long now = System.currentTimeMillis();

        boolean startNewBatch = queue.isEmpty() || (now - batchStartTime > 10_000);
        String combined = message;

        // Only merge short messages into other short messages
        if (!startNewBatch && message.length() <= 120) {
            String last = queue.removeLast();
            if (last.length() <= 120) {
                int newLen = last.length() + 1 + message.length();
                if (newLen <= 300) {
                    combined = last + " " + message;
                } else {
                    queue.addLast(last); // cap exceeded, restore & add separate
                }
            } else {
                queue.addLast(last); // long msg acts as divider, don't merge into it
            }
        }

        queue.addLast(combined);
        batchStartTime = now; // sliding window: reset timer on every merge to allow continuous rapid-fire sequencing
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
