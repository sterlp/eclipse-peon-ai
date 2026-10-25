package org.sterl.open.aifactory.config.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.sterl.open.aifactory.config.SettingsUpdate;

/**
 * The inbound wire shape of a save. Every field is a wrapper, so "absent" is not "empty" is not
 * "a value" — only the distinction between those three makes "leeres Feld = unverändert" expressible.
 *
 * <p>This is the only type of the component that may carry a key; it is translated at the external
 * facade and never travels further in.</p>
 *
 * <p>Unknown properties are ignored on purpose: {@code llm.configDirectory} and
 * {@code llm.diskToolsEnabled} have no field here, so a hand-crafted body cannot reach the two values
 * the factory pins for itself (UC-WP-36/37).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SettingsUpdateRequest(String provider, String model, String url, String apiKey,
        Integer tokenWindow, Long timeoutSeconds, Boolean sendThinking) {

    public SettingsUpdate toUpdate() {
        return new SettingsUpdate(provider, model, url, apiKey, tokenWindow, timeoutSeconds, sendThinking);
    }
}
