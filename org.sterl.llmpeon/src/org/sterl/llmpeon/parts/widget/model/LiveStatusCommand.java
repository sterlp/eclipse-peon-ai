package org.sterl.llmpeon.parts.widget.model;

/** Streaming status update shown in the live-preview bar at the bottom of the chat. */
public record LiveStatusCommand(String state, double tokPerSec, String chunk) implements UiCommand {

    @Override
    public String type() { return "updateLiveResponse"; }
}
