package org.sterl.open.aifactory.config;

/**
 * The internal write model of {@link FactoryConfigService#update(SettingsUpdate)} — the command form
 * of the inbound request, with {@code null} meaning "the client sent nothing for this field".
 *
 * <p>{@code null} and blank both mean <b>leave unchanged</b>: neither ever reaches the store, so an
 * emptied input cannot delete a value (R-WP-16 "leeres Feld = unverändert") and the api key can be set
 * but never cleared through the API.</p>
 *
 * <p>There is deliberately no field for the config directory or the disk tools — the two values the
 * factory pins for itself (UC-WP-36/37) are unreachable from the wire. The DTO boundary is the external
 * facade, so this type is not the inbound JSON model.</p>
 */
public record SettingsUpdate(String provider, String model, String url, String apiKey,
        Integer tokenWindow, Long timeoutSeconds, Boolean sendThinking) {
}
