package org.sterl.open.aifactory.config.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.lang.Nullable;

import org.sterl.open.aifactory.config.AgentSettingsUpdate;
import org.sterl.open.aifactory.config.KeyIntentRequest;
import org.sterl.open.aifactory.config.WireJsonObjectText;
import org.sterl.open.aifactory.config.WireKeyIntent;
import org.sterl.open.aifactory.config.WireNumberText;

/**
 * Inbound shape of one slot save. Every field is text, so R-WP-16's three states stay apart on the wire:
 * absent keeps, an empty string deletes, a value sets (UC-WP-46). {@code overridden} is the one exception
 * — it has no inherit state, and an absent flag means "not overriding", the same reading as
 * {@code removeApiKey} on the default level. Unknown properties are ignored, so a hand-crafted body can
 * reach neither the agent's other frontmatter keys nor another slot.
 *
 * <p>What a field may hold is a rule of this shape (R-WP-55), and it is the same rule the default level
 * states: the key intent is one constraint on both bodies, {@code temperature} must be a number and
 * {@code extraBody} a JSON object — both are refused here, where the answer can name the field, and never
 * stored for the core to ignore silently. {@code tools} carries no rule on purpose: the factory keeps the
 * wording as typed and the core alone interprets it (R-WP-52).</p>
 *
 * <p>Every field is {@code @Nullable} — absent is a state of the three-state wire, and it is the marker
 * {@code server-api.ts} reads as optional (R-WP-54).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@WireKeyIntent
public record AgentSettingsUpdateRequest(@Nullable Boolean overridden, @Nullable String model,
        @Nullable String url, @Nullable String think, @WireNumberText @Nullable String temperature,
        @WireJsonObjectText @Nullable String extraBody, @Nullable String tools, @Nullable String apiKey,
        @Nullable Boolean removeApiKey) implements KeyIntentRequest {

    public AgentSettingsUpdate toUpdate() {
        return new AgentSettingsUpdate(Boolean.TRUE.equals(overridden), model, url, think, temperature,
                extraBody, tools, apiKey, Boolean.TRUE.equals(removeApiKey));
    }
}
