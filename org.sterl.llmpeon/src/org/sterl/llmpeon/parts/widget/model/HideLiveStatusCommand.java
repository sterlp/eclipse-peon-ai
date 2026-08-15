package org.sterl.llmpeon.parts.widget.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Hide the live-preview status bar (singleton — no payload fields). */
@JsonAutoDetect(fieldVisibility = Visibility.ANY)
public final class HideLiveStatusCommand implements UiCommand {

    public static final HideLiveStatusCommand INSTANCE = new HideLiveStatusCommand();

    private HideLiveStatusCommand() {}

    @JsonProperty("type")
    @Override
    public String type() { return "hideLiveStatus"; }
}
