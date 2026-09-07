package org.sterl.llmpeon.parts.widget.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Scroll the chat view to the bottom (singleton — no payload fields). */
@JsonAutoDetect(fieldVisibility = Visibility.ANY)
public final class ScrollToBottomCommand implements UiCommand {

    public static final ScrollToBottomCommand INSTANCE = new ScrollToBottomCommand();

    private ScrollToBottomCommand() {}

    @JsonProperty("type")
    @Override
    public String type() { return "scrollToBottom"; }
}
