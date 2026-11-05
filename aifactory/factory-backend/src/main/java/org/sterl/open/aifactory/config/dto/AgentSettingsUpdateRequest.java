package org.sterl.open.aifactory.config.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.sterl.open.aifactory.config.AgentSettingsUpdate;

/**
 * Inbound shape of one slot save. Every field is text, so R-WP-16's three states stay apart on the wire:
 * absent keeps, an empty string deletes, a value sets (UC-WP-46). {@code overridden} is the one exception
 * — it has no inherit state, and an absent flag means "not overriding", the same reading as
 * {@code removeApiKey} on the default level. Unknown properties are ignored, so a hand-crafted body can
 * reach neither the agent's other frontmatter keys nor another slot.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentSettingsUpdateRequest(Boolean overridden, String model, String url, String think,
        String temperature, String apiKey, Boolean removeApiKey) {

    public AgentSettingsUpdate toUpdate() {
        return new AgentSettingsUpdate(Boolean.TRUE.equals(overridden), model, url, think, temperature,
                apiKey, Boolean.TRUE.equals(removeApiKey));
    }
}
