package org.sterl.open.aifactory.config;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.sterl.open.aifactory.config.dto.ProviderDefaultsDto;
import org.sterl.open.aifactory.config.dto.SettingsDto;
import org.sterl.open.aifactory.config.dto.SettingsUpdateRequest;

/**
 * The masked settings over HTTP (R-WP-16). The external facade only maps between snapshot and DTO —
 * nothing is filtered here, because {@link SettingsDto} has no field a key could sit in. Reading never
 * writes the config file; writing answers with a fresh masked read, never with the submitted body.
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final FactoryConfigService settings;

    public SettingsController(FactoryConfigService settings) {
        this.settings = settings;
    }

    @GetMapping
    public SettingsDto get() {
        return SettingsDto.of(settings.snapshot());
    }

    /**
     * The provider list the page offers, with the two facts that make it follow a provider change
     * (UC-WP-62). Values of the core, so a new provider appears here without a second list to maintain —
     * and without a model default, which the core does not have.
     */
    @GetMapping("/providers")
    public List<ProviderDefaultsDto> providers() {
        return settings.providerDefaults().stream().map(ProviderDefaultsDto::of).toList();
    }

    /** The only place the inbound key is translated away — it stops in the internal facade. */
    @PutMapping
    public SettingsDto update(@RequestBody SettingsUpdateRequest request) {
        return SettingsDto.of(settings.update(request.toUpdate()));
    }
}
