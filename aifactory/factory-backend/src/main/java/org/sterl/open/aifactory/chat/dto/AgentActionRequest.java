package org.sterl.open.aifactory.chat.dto;

/**
 * A tile action: which tile, what to do. It addresses a tile of this project's team — never a
 * conversation, and it starts no turn (UC-WP-12, UC-WP-13).
 *
 * @param name   one of this project's agent names, resolved against the roster
 * @param action the wire value of {@link AgentAction} — {@code clear} or {@code compact}
 */
public record AgentActionRequest(String name, String action) {
}
