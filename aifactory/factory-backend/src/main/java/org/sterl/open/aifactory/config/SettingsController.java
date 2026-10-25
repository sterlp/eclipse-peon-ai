package org.sterl.open.aifactory.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.sterl.open.aifactory.config.dto.SettingsDto;

/**
 * The masked settings over HTTP (R-WP-16). The external facade only maps snapshot to DTO — nothing is
 * filtered here, because {@link SettingsDto} has no field a key could sit in. Reading never writes the
 * config file.
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
}
