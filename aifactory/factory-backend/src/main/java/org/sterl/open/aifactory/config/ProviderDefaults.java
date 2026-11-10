package org.sterl.open.aifactory.config;

import java.util.List;

/**
 * What the core knows about one provider, as the config page needs it to follow a provider change
 * (R-WP-16 "Dynamische Felder wie im Plugin", UC-WP-62) and to build the agent think field (R-WP-23,
 * UC-WP-67).
 *
 * <p>There is deliberately no model default: the core knows none, so the page clears the model field
 * instead of carrying over a value that belongs to another provider. {@code defaultUrl} is
 * {@code null} for a provider that requires a configured URL.</p>
 *
 * <p>{@code thinkSupported} is the provider's own think form reduced to what the field can do — the
 * yes/no value stays a boolean, no second think concept is introduced (Q3). {@code thinkForm} and
 * {@code thinkValues} are the same answer without the reduction: the form the field has to take and, for
 * {@code VALUES}, the list it offers. {@code thinkValues} is empty for every other form.</p>
 *
 * <p>{@code extraBodySupported} is the provider's own answer (R-WP-52): without it the page would keep a
 * field the core silently drops.</p>
 */
public record ProviderDefaults(String name, String defaultUrl, boolean thinkSupported, ThinkForm thinkForm,
        List<String> thinkValues, boolean extraBodySupported) {
}
