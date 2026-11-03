package org.sterl.open.aifactory.config.dto;

import java.util.List;

import org.sterl.open.aifactory.config.ProviderDefaults;
import org.sterl.open.aifactory.config.ThinkForm;

/**
 * The wire shape of one provider's defaults (UC-WP-62). Five fields, none of them a value the factory
 * invented: the name, the base URL of the core, whether that core provider can carry think at all, and
 * the form plus fixed values of that think input (R-WP-23, UC-WP-67).
 */
public record ProviderDefaultsDto(String name, String defaultUrl, boolean thinkSupported, ThinkForm thinkForm,
        List<String> thinkValues) {

    public static ProviderDefaultsDto of(ProviderDefaults defaults) {
        return new ProviderDefaultsDto(defaults.name(), defaults.defaultUrl(), defaults.thinkSupported(),
                defaults.thinkForm(), defaults.thinkValues());
    }
}
