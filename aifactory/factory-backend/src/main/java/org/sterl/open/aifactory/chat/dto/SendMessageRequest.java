package org.sterl.open.aifactory.chat.dto;

/**
 * The whole inbound contract of a turn: the text and nothing else. There is no field that could name
 * an addressee, so "die Eingabe geht immer an Jon" (UC-WP-6) is a property of this type, not a rule
 * some code has to remember.
 */
public record SendMessageRequest(String text) {
}
