package org.sterl.open.aifactory.chat.dto;

/**
 * The whole inbound contract of a turn: the text and the label of the person who typed it. There is no
 * field that could name an addressee, so "die Eingabe geht immer an Jon" (UC-WP-6) is a property of this
 * type, not a rule some code has to remember.
 *
 * @param user the sender's name as a <b>label</b> (R-WP-17, R-WP-25) — it selects neither a conversation
 *            nor an agent, and {@code null} or blank simply means no name, which is no error and no
 *            prefix. The format it turns into is the server's, not the caller's (see
 *            {@code chat/UserName}).
 */
public record SendMessageRequest(String text, String user) {

    /** A send without a name — the shape every caller had before a name existed (Inc 6). */
    public SendMessageRequest(String text) {
        this(text, null);
    }
}
