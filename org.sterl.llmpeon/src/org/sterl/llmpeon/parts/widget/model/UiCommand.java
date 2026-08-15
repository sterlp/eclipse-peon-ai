package org.sterl.llmpeon.parts.widget.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Sealed hierarchy of control-plane commands sent from Java to the embedded browser.
 * Each implementation serializes a JSON {@code type} field via its {@link #type()} method.
 */
sealed interface UiCommand permits SetThemeCommand, LiveStatusCommand, HideLiveStatusCommand {

    @JsonProperty("type")
    String type();
}
