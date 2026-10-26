package org.sterl.open.aifactory.chat.dto;

/**
 * One line of the stored conversation. Roles are the two of the document — {@code user} and
 * {@code jon}; a tool or status line is live-only and therefore never appears here (R-WP-11).
 *
 * @param origin the author of the line by name — Jon, or the team member whose final answer this is —
 *               and {@code null} when no agent produced it, which today means every {@code user} line
 *               (R-WP-4 "Zeilen ohne Agenten-Beziehung tragen keinen Namen", R-WP-17). It is never a
 *               guess and never a core id the factory did not build.
 */
public record ChatMessageDto(String role, String text, String origin) {
}
