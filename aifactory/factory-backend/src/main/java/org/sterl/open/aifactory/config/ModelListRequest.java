package org.sterl.open.aifactory.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.lang.Nullable;

/**
 * Which connection to list models for (R-WP-50): the page's draft of it — provider and base URL for the
 * default level, the slot and that slot's own URL for an agent, plus {@code refresh} for the refresh
 * button.
 *
 * <p>No field for a key, on purpose: the key stays on the server side and never travels in either
 * direction (R-WP-16), so a body cannot offer one — and an unknown property is ignored rather than
 * refused, which is what keeps a hand-made body from reaching anything.</p>
 *
 * <p>A field that is absent or empty names nothing, so that level keeps the value the config holds; the
 * page prefills every field, so what it sends is what it shows.</p>
 *
 * <p>Component record rather than a {@code dto} shape: nothing is translated here — no key stops at the
 * facade, no three states have to be interpreted — so a second copy of these five fields would only be
 * a second place to keep them in step.</p>
 *
 * <p>The provider carries the same rule as the save (R-WP-55): a name the core does not have is refused
 * here, where the answer can still name the field, and never answered with an empty list that looks
 * like a provider without models.</p>
 *
 * <p>Every field is {@code @Nullable} — that is what {@code server-api.ts} reads as optional (R-WP-54).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelListRequest(@WireProviderName @Nullable String provider, @Nullable String url,
        @Nullable String agent, @Nullable String agentUrl, @Nullable Boolean refresh) {
}
