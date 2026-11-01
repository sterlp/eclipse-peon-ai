package org.sterl.open.aifactory.chat;

import org.sterl.llmpeon.queuedmessages.UserMessageQueue;

/**
 * The sender's name in front of a message to Jon — the whole format lives here, in both directions
 * (R-WP-25): {@link #payload} writes it into the text that becomes the core's memory, {@link #parse}
 * reads exactly that back for the chat. One format, one owner: no browser invents it and nothing else
 * is ever taken out of a person's text.
 *
 * <p>The name goes into the <b>text</b> because the memory is the one store this factory has
 * (R-WP-11): written there it needs no field, no file and no cache, and it outlives a restart on its
 * own (UC-WP-48 GIVEN 3).</p>
 */
final class UserName {

    /** Long enough for a name, short enough that a chat line stays readable. */
    static final int MAX_LENGTH = 32;

    private static final String PREFIX = "[User ";
    private static final String SUFFIX = "]: ";

    private UserName() {
    }

    /** A line of the prompt that explains the format (R-WP-25) — a format, never a name (D-6-8). */
    static String promptLine() {
        return "Messages from people start with `" + PREFIX + "<Name>" + SUFFIX
                + "` — the name is the sender, not part of the request.";
    }

    /**
     * What Jon is handed: the name in front of the text, or the text alone when there is no name.
     * A name is never refused, only cleaned — a browser carrying a broken name keeps its chat, since
     * there is nothing to protect (R-WP-17: no login, no user-owned area).
     */
    static String payload(String name, String text) {
        String cleaned = clean(name);
        return cleaned == null ? text : PREFIX + cleaned + SUFFIX + text;
    }

    /** The name as the format can hold it: no control character, no bracket that would close it early. */
    static String clean(String name) {
        if (name == null) {
            return null;
        }
        StringBuilder kept = new StringBuilder(Math.min(name.length(), MAX_LENGTH));
        for (int i = 0; i < name.length() && kept.length() < MAX_LENGTH; i++) {
            char c = name.charAt(i);
            if (c != ']' && !Character.isISOControl(c)) {
                kept.append(c);
            }
        }
        String cleaned = kept.toString().trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * The sender of a stored line, and the line without his name. Looked for at the start of the text
     * and — for an input the core took into its queue — directly behind the core's own marker, which
     * stays exactly as the core wrote it (Q6). Never at an arbitrary line start: a person may quote
     * this very format and then has to read it back unchanged.
     *
     * @param text the stored text, as {@code ProjectRuntime#history} read it from the memory
     */
    static Parsed parse(String text) {
        int start = 0;
        if (text.startsWith(UserMessageQueue.QUEUED_MARKER_PREFIX)) {
            int markerEnd = text.indexOf(": ", UserMessageQueue.QUEUED_MARKER_PREFIX.length());
            if (markerEnd < 0) {
                return new Parsed(null, text);
            }
            start = markerEnd + 2;
        }
        if (!text.startsWith(PREFIX, start)) {
            return new Parsed(null, text);
        }
        int nameStart = start + PREFIX.length();
        int nameEnd = text.indexOf(SUFFIX, nameStart);
        if (nameEnd < 0) {
            return new Parsed(null, text);
        }
        String name = text.substring(nameStart, nameEnd).trim();
        // a name never spans a line, so a multi-line quote of the format is no sender either
        if (name.isEmpty() || text.substring(nameStart, nameEnd).chars().anyMatch(c -> c == '\n' || c == '\r')) {
            return new Parsed(null, text);
        }
        // what stands before the prefix is the core's own line — for a queued input its marker, and it
        // stays visible exactly as the core wrote it (Inc 3, Q6)
        return new Parsed(name, text.substring(0, start) + text.substring(nameEnd + SUFFIX.length()));
    }

    /**
     * A stored line, split the way it was written.
     *
     * @param name the sender the format named, or {@code null} — the factory never guesses one (R-WP-4)
     * @param text the line without that prefix, everything else untouched
     */
    record Parsed(String name, String text) {
    }
}
