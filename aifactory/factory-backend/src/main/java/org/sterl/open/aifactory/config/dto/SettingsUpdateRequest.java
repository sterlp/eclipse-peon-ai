package org.sterl.open.aifactory.config.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.sterl.open.aifactory.config.KeyIntentRequest;
import org.sterl.open.aifactory.config.SettingsUpdate;
import org.sterl.open.aifactory.config.WireBooleanText;
import org.sterl.open.aifactory.config.WireIntegerText;
import org.sterl.open.aifactory.config.WireKeyIntent;
import org.sterl.open.aifactory.config.WireParamListText;
import org.sterl.open.aifactory.config.WireProviderName;
import org.sterl.open.aifactory.config.WireValidationHints;

/**
 * The inbound wire shape of a save. Every field is text, so the three states of R-WP-16 stay
 * distinguishable on the wire: absent keeps, an empty string clears, a value sets (UC-WP-46) — a number
 * or a boolean has no empty state, which is why token window, timeout, think and max tokens arrive as
 * text and are parsed by the internal facade behind {@link SettingsUpdate} rather than by the mapper.
 * The page is the only client and ships in this artifact, so it is the contract this shape is built for.
 *
 * <p>This is the only type of the component that may carry a key; it is translated at the external
 * facade and never travels further in.</p>
 *
 * <p>What a field may hold is a rule of this shape (R-WP-55): the {@code Wire*} constraints state it
 * once, on the field the page is bound to, and the endpoint validates before anything is written — the
 * internal facade does not weigh the same sentence a second time. A refusal names every field it
 * rejects and never a submitted value (UC-WP-92).</p>
 *
 * <p>Unknown properties are ignored on purpose: {@code llm.configDirectory} and
 * {@code llm.diskToolsEnabled} have no field here, so a hand-crafted body cannot reach the two values
 * the factory pins for itself (UC-WP-36/37).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@WireKeyIntent
public record SettingsUpdateRequest(@WireProviderName String provider, String model, String url,
        String apiKey,
        @WireIntegerText(max = Integer.MAX_VALUE) String tokenWindow,
        @WireIntegerText(max = Long.MAX_VALUE, message = WireValidationHints.TIMEOUT_HINT) String timeoutSeconds,
        @WireBooleanText String sendThinking, String think,
        @WireIntegerText(min = 0, max = Integer.MAX_VALUE,
                message = WireValidationHints.MAX_TOKENS_HINT) String maxTokens,
        @WireParamListText(message = WireValidationHints.QUERY_PARAMS_HINT) String queryParams,
        @WireParamListText(message = WireValidationHints.HEADER_PARAMS_HINT) String headerParams,
        Boolean removeApiKey) implements KeyIntentRequest {

    /** A wrapper on the wire, a fact inside: an absent flag is the same as {@code false}. */
    public SettingsUpdate toUpdate() {
        return new SettingsUpdate(provider, model, url, apiKey, tokenWindow, timeoutSeconds, sendThinking,
                think, maxTokens, queryParams, headerParams, Boolean.TRUE.equals(removeApiKey));
    }
}
