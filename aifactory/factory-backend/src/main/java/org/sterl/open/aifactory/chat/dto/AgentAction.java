package org.sterl.open.aifactory.chat.dto;

/**
 * A tile's action. Parsed from the wire value here rather than by Jackson, so an unknown action fails
 * as an {@link IllegalArgumentException} — the same 400 as an unknown agent name — instead of turning
 * into a body-conversion error with a different shape.
 */
public enum AgentAction {
    CLEAR, COMPACT;

    public static AgentAction from(String wire) {
        for (AgentAction action : values()) {
            if (action.name().equalsIgnoreCase(wire)) {
                return action;
            }
        }
        throw new IllegalArgumentException("unknown agent action: " + wire);
    }

    /** The capability that offers this action — so a tile without it cannot be asked to run it. */
    public TileCapability capability() {
        return switch (this) {
            case CLEAR -> TileCapability.CLEAR;
            case COMPACT -> TileCapability.COMPACT;
        };
    }
}
