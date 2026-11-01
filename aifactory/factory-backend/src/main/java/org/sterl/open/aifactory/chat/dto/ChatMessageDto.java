package org.sterl.open.aifactory.chat.dto;

/**
 * One line of the stored conversation. Roles are the two of the document — {@code user} and
 * {@code jon}; a tool or status line is live-only and therefore never appears here (R-WP-11).
 *
 * @param origin the author of the line by name — Jon, the team member whose final answer this is, or the
 *               person who typed it when his message carried his name (R-WP-25) — and {@code null} when
 *               nobody is named, which is a line without an author and never a guess (R-WP-4 "Zeilen ohne
 *               Herkunft tragen keinen Namen"). It is never an id the factory did not build.
 */
public record ChatMessageDto(String role, String text, String origin) {
}
