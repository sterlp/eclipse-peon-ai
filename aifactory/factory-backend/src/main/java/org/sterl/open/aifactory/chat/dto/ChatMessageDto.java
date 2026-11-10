package org.sterl.open.aifactory.chat.dto;

/**
 * One line of the stored conversation. Roles are the four of the document — {@code user}, {@code jon},
 * the {@code error} line of a failed turn (R-WP-34) and the {@code tool} step of Jon's own trace
 * (R-WP-47); a status line and a diff stay live-only and therefore never appear here (R-WP-11).
 *
 * @param origin the author of the line by name — Jon, the team member whose final answer this is, or the
 *               person who typed it when his message carried his name (R-WP-25) — for a step, the member
 *               it delegated to or the tool he used — and {@code null} when
 *               nobody is named, which is a line without an author and never a guess (R-WP-4 "Zeilen ohne
 *               Herkunft tragen keinen Namen"). It is never an id the factory did not build.
 */
public record ChatMessageDto(String role, String text, String origin) {
}
