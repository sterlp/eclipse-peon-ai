package org.sterl.llmpeon.queuedmessages;

import org.sterl.llmpeon.queuedmessages.UserMessageQueue.QueuedMessage;

/**
 * The one renderer of queued entries, so the LLM payload marker, the disclosure line and the
 * tool-result block of one entry can never drift apart ("one behaviour, one implementation").
 * Formats are pinned by org.sterl.llmpeon.core/docs/queued-user-messages.md R9; the time is always
 * the queue time rendered in the queue's own clock zone, never the consumption time.
 */
public final class QueuedUserMessages {

    private QueuedUserMessages() {}

    /** LLM payload marker: {@code [Queued Message] (14:32): <text>} — text unchanged behind the prefix. */
    public static String marker(UserMessageQueue queue, QueuedMessage entry) {
        return UserMessageQueue.QUEUED_MARKER_PREFIX + "(" + queue.queuedLabel(entry.queuedAt()) + "): "
                + entry.text();
    }

    /** Disclosure line for every consumed entry: {@code Reading queued User message: <text> (queued 14:32)}. */
    public static String readingLine(UserMessageQueue queue, QueuedMessage entry) {
        return "Reading queued User message: " + entry.text()
                + " (queued " + queue.queuedLabel(entry.queuedAt()) + ")";
    }
}
