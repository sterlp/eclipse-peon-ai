package org.sterl.open.aifactory.chat.dto;

/**
 * One line of the stored conversation. Roles are the two of the document — {@code user} and
 * {@code jon}; a tool or status line is live-only and therefore never appears here (R-WP-11).
 */
public record ChatMessageDto(String role, String text) {
}
