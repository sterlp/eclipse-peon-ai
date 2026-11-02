package org.sterl.open.aifactory.config;

/**
 * What the core knows about one provider, as the config page needs it to follow a provider change
 * (R-WP-16 "Dynamische Felder wie im Plugin", UC-WP-62).
 *
 * <p>There is deliberately no model default: the core knows none, so the page clears the model field
 * instead of carrying over a value that belongs to another provider. {@code defaultUrl} is
 * {@code null} for a provider that requires a configured URL.</p>
 *
 * <p>{@code thinkSupported} is the provider's own think form reduced to what the field can do — the
 * yes/no value stays a boolean, no second think concept is introduced (Q3).</p>
 */
public record ProviderDefaults(String name, String defaultUrl, boolean thinkSupported) {
}
