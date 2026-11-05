package org.sterl.open.aifactory.chat;

/**
 * The one owner of how a failed turn reads inside the conversation (R-WP-34): the marker in front of the
 * reason, written into the core's memory and read out of it again — so the line needs no field, no table
 * and no second store, and it survives a restart the way every other line does.
 *
 * <p>The marker is a <b>wire value</b>, not a sentence of the UI: the page shows the reason exactly as the
 * server hands it over and knows nothing about this prefix (R-WP-27). {@code AiMessage.from(String)} is
 * how the core itself writes such a line ({@code ThreadSafeMemory.add} stamps an {@code "ok"} the same
 * way), which is why the factory may write one without touching the core.</p>
 */
final class TurnFailure {

    /** Stands in front of the reason inside the memory; a line carrying it is never Jon's answer. */
    static final String MARKER = "[Turn failed]: ";

    static String line(String reason) {
        return MARKER + reason;
    }

    static boolean isFailure(String text) {
        return text != null && text.startsWith(MARKER);
    }

    /** The reason alone — what the conversation shows; the marker is this component's bookkeeping. */
    static String reason(String text) {
        return text.substring(MARKER.length());
    }

    private TurnFailure() {
    }
}
