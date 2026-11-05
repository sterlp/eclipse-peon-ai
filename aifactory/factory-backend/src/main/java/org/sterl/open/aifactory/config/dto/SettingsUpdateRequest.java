package org.sterl.open.aifactory.config.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.sterl.open.aifactory.config.SettingsUpdate;

/**
 * The inbound wire shape of a save. Every field is text, so the three states of R-WP-16 stay
 * distinguishable on the wire: absent keeps, an empty string clears, a value sets (UC-WP-46) — a number
 * or a boolean has no empty state, which is why token window, timeout and think arrive as text and are
 * parsed by the internal facade behind {@link SettingsUpdate} rather than by the mapper. The page is the
 * only client and ships in this artifact, so it is the contract this shape is built for.
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
        String tokenWindow, String timeoutSeconds, String sendThinking, Boolean removeApiKey) {

    /** A wrapper on the wire, a fact inside: an absent flag is the same as {@code false}. */
    public SettingsUpdate toUpdate() {
        return new SettingsUpdate(provider, model, url, apiKey, tokenWindow, timeoutSeconds, sendThinking,
                Boolean.TRUE.equals(removeApiKey));
    }
}
