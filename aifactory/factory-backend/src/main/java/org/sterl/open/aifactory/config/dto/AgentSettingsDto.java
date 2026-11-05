package org.sterl.open.aifactory.config.dto;

import org.sterl.open.aifactory.config.AgentSettings;

/**
 * One agent slot as the config page shows it (R-WP-23). {@code overridden} is the checkbox and is derived
 * from the file, never stored; the four inheritable fields are the stored text or {@code null} when the
 * slot inherits. Like the default level there is no field a key could sit in — only {@code keySet}
 * (R-WP-16).
 */
public record AgentSettingsDto(String id, String name, boolean overridden, String model, String url,
        String think, String temperature, boolean keySet) {

    public static AgentSettingsDto of(AgentSettings settings) {
        return new AgentSettingsDto(settings.id(), settings.name(), settings.overridden(), settings.model(),
                settings.url(), settings.think(), settings.temperature(), settings.keySet());
    }
}
