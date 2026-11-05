package org.sterl.open.aifactory.chat.dto;

/** A tile's state — the same three live states as the plugin roster plus the factory's turn failure. */
public enum AgentTileState {
    IDLE, WORKING, COMPACTING, ERROR
}
