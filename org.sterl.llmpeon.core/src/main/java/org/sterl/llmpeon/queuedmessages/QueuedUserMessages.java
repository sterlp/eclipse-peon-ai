package org.sterl.llmpeon.queuedmessages;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.agent.model.TaggedResult;
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

    /**
     * One line of the {@code queued_user_message} block body: {@code (queued 14:32): <text>}. No
     * {@code [Queued Message]} prefix here — the block head already says what the lines are (R11).
     */
    public static String bodyLine(UserMessageQueue queue, QueuedMessage entry) {
        return "(queued " + queue.queuedLabel(entry.queuedAt()) + "): " + entry.text();
    }

    /**
     * All body lines as the ONE block a round attaches to a tool result (R11) or a question rides
     * with (R12).
     *
     * @return the block, or {@code null} when there is nothing to deliver — an empty lane must leave
     *         the result text untouched instead of carrying an empty block.
     */
    public static @Nullable TaggedResult block(List<String> bodyLines) {
        if (bodyLines == null || bodyLines.isEmpty()) return null;
        return new TaggedResult(TaggedResult.TAG_QUEUED_USER_MESSAGE, String.join(TaggedResult.LN, bodyLines));
    }

    /**
     * Whether a tool result already carries one of our blocks — such a result is no delivery target,
     * because an {@code askUser} result ends with the user's answer and a second block would push it
     * into the middle (R12). Recognised by the head line alone, so no tag literal leaks into the
     * tool layer.
     */
    public static boolean carriesBlock(@Nullable String toolText) {
        return toolText != null && toolText.contains(TaggedResult.PREFIX + TaggedResult.TAG_QUEUED_USER_MESSAGE);
    }
}
