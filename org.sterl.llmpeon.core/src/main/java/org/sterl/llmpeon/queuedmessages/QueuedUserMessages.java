package org.sterl.llmpeon.queuedmessages;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.agent.model.Attachment;
import org.sterl.llmpeon.agent.model.HarnessInput;
import org.sterl.llmpeon.agent.model.PictureAttachment;
import org.sterl.llmpeon.agent.model.QueuedInput;
import org.sterl.llmpeon.agent.model.TaggedInput;
import org.sterl.llmpeon.agent.model.TextAttachment;
import org.sterl.llmpeon.agent.model.UserInput;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * The one renderer of queued entries, so the LLM payload marker, the disclosure line and the
 * tool-result block of one entry can never drift apart ("one behaviour, one implementation").
 * Formats are pinned by org.sterl.llmpeon.core/docs/queued-user-messages.md R9; the time is always
 * the queue time rendered in the queue's own clock zone, never the consumption time.
 */
public final class QueuedUserMessages {

    /** Shown where an entry has no text of its own (an attachment-only input, Rule 13). */
    static final String ATTACHMENT_ONLY = "(attachment only)";

    /** What a consumer of {@code askUser} gets: the one outer block plus the pictures that cannot ride in it. */
    public record Delivery(@Nullable HarnessInput block, List<PictureAttachment> pictures) {}

    private QueuedUserMessages() {}

    /** LLM payload marker: {@code [Queued Message] (14:32): <text>} — text unchanged behind the prefix. */
    public static String marker(UserMessageQueue queue, QueuedInput entry) {
        return marker(queue, entry.queuedAt(), entry.input().text());
    }

    private static String marker(UserMessageQueue queue, Instant queuedAt, String text) {
        var head = UserMessageQueue.QUEUED_MARKER_PREFIX + "(" + queue.queuedLabel(queuedAt) + "):";
        return StringUtil.hasValue(text) ? head + " " + text : head;
    }

    /** Disclosure line for every consumed entry: {@code Reading queued User message: <text> (queued 14:32)}. */
    public static String readingLine(UserMessageQueue queue, QueuedInput entry) {
        return "Reading queued User message: " + shown(entry)
                + " (queued " + queue.queuedLabel(entry.queuedAt()) + ")";
    }

    /**
     * One line of the {@code queued_user_message} block body: {@code (queued 14:32): <text>}. No
     * {@code [Queued Message]} prefix here — the block head already says what the lines are (R11).
     */
    public static String bodyLine(UserMessageQueue queue, QueuedInput entry) {
        return "(queued " + queue.queuedLabel(entry.queuedAt()) + "): " + shown(entry);
    }

    /** The entry as a consumer outside the queue sees it: disclosure-formatted text, attachments untouched (R12/13). */
    public static UserInput disclosed(UserMessageQueue queue, QueuedInput entry) {
        return new UserInput(bodyLine(queue, entry), entry.input().attachments());
    }

    /** The follow-up turn of one entry (Rule 2/13): marker before the text only, then its attachments. */
    public static UserInput followUp(UserMessageQueue queue, QueuedInput entry) {
        return new UserInput(marker(queue, entry), entry.input().attachments());
    }

    /**
     * One input from several: texts joined by a line break, attachments flattened in order. Used where
     * everything waiting becomes ONE message (turn start, abort drain).
     */
    public static UserInput combine(List<UserInput> inputs) {
        var texts = inputs.stream().map(UserInput::text).filter(StringUtil::hasValue).toList();
        var attachments = new ArrayList<Attachment>();
        inputs.forEach(in -> attachments.addAll(in.attachments()));
        return new UserInput(texts.isEmpty() ? null : String.join(TaggedInput.LN, texts), attachments);
    }

    /** The inputs an abort keeps: the polled-but-unanswered entry first, then everything still queued. */
    public static List<UserInput> preserved(@Nullable QueuedInput unanswered, List<QueuedInput> entries) {
        var all = new ArrayList<UserInput>();
        if (unanswered != null) all.add(unanswered.input());
        entries.forEach(e -> all.add(e.input()));
        return all;
    }

    /**
     * What a turn starts with while entries wait: queued texts + initial text (line break, no time),
     * all queued attachments in queue order, then the initial ones. Without initial text the queue IS
     * the payload and gets the marker with the FIRST entry's time (Rule 9).
     */
    public static UserInput startInput(UserMessageQueue queue, List<QueuedInput> entries, UserInput initial) {
        if (entries.isEmpty()) return initial;
        var queued = combine(entries.stream().map(QueuedInput::input).toList());
        var attachments = new ArrayList<>(queued.attachments());
        attachments.addAll(initial.attachments());
        if (StringUtil.hasNoValue(initial.text())) {
            return new UserInput(marker(queue, entries.getFirst().queuedAt(), queued.text()), attachments);
        }
        var text = StringUtil.hasValue(queued.text()) ? queued.text() + TaggedInput.LN + initial.text() : initial.text();
        return new UserInput(text, attachments);
    }

    /**
     * The askUser delivery of already disclosed inputs (R12/13): per input its text line followed by
     * its file blocks inside ONE outer block; pictures cannot ride in a text block and are returned
     * apart, in input order.
     */
    public static Delivery delivery(List<UserInput> disclosedInputs) {
        var lines = new ArrayList<String>();
        var pictures = new ArrayList<PictureAttachment>();
        for (var input : disclosedInputs) {
            var line = new StringBuilder(input.text());
            for (var a : input.attachments()) {
                switch (a) {
                    case PictureAttachment p -> pictures.add(p);
                    case TextAttachment t -> line.append(TaggedInput.LN).append(t);
                }
            }
            lines.add(line.toString());
        }
        return new Delivery(block(lines), List.copyOf(pictures));
    }

    private static String shown(QueuedInput entry) {
        return StringUtil.hasValue(entry.input().text()) ? entry.input().text() : ATTACHMENT_ONLY;
    }

    /**
     * All body lines as the ONE block a round attaches to a tool result (R11) or a question rides
     * with (R12).
     *
     * @return the block, or {@code null} when there is nothing to deliver — an empty lane must leave
     *         the result text untouched instead of carrying an empty block.
     */
    public static @Nullable HarnessInput block(List<String> bodyLines) {
        if (bodyLines == null || bodyLines.isEmpty()) return null;
        return new HarnessInput(TaggedInput.TAG_QUEUED_USER_MESSAGE, String.join(TaggedInput.LN, bodyLines));
    }

    /**
     * Whether a tool result already carries one of our blocks — such a result is no delivery target,
     * because an {@code askUser} result ends with the user's answer and a second block would push it
     * into the middle (R12). Recognised by the head line alone, so no tag literal leaks into the
     * tool layer.
     */
    public static boolean carriesBlock(@Nullable String toolText) {
        return toolText != null && toolText.contains(TaggedInput.PREFIX + TaggedInput.TAG_QUEUED_USER_MESSAGE);
    }
}
