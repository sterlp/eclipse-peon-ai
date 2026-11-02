package org.sterl.open.aifactory.config.dto;

import org.sterl.open.aifactory.config.ProviderDefaults;

/**
 * The wire shape of one provider's defaults (UC-WP-62). Three fields, none of them a value the factory
 * invented: the name, the base URL of the core, and whether that core provider can carry think at all.
 */
public record ProviderDefaultsDto(String name, String defaultUrl, boolean thinkSupported) {

    public static ProviderDefaultsDto of(ProviderDefaults defaults) {
        return new ProviderDefaultsDto(defaults.name(), defaults.defaultUrl(), defaults.thinkSupported());
    }
}
