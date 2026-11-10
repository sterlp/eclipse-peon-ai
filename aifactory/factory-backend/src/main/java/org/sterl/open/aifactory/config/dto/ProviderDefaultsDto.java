package org.sterl.open.aifactory.config.dto;

import java.util.List;

import org.springframework.lang.Nullable;

import org.sterl.open.aifactory.config.ProviderDefaults;
import org.sterl.open.aifactory.config.ThinkForm;

/**
 * The wire shape of one provider's defaults (UC-WP-62). None of them a value the factory invented: the
 * name, the base URL of the core, whether that core provider can carry think at all, the form plus fixed
 * values of that think input (R-WP-23, UC-WP-67), and whether it accepts an {@code extra_body} (R-WP-52).
 */
public record ProviderDefaultsDto(String name, @Nullable String defaultUrl, boolean thinkSupported,
        ThinkForm thinkForm, List<String> thinkValues, boolean extraBodySupported) {

    public static ProviderDefaultsDto of(ProviderDefaults defaults) {
        return new ProviderDefaultsDto(defaults.name(), defaults.defaultUrl(), defaults.thinkSupported(),
                defaults.thinkForm(), defaults.thinkValues(), defaults.extraBodySupported());
    }
}
