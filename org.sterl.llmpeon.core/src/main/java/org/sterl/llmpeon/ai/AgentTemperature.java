package org.sterl.llmpeon.ai;

import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

/**
 * Parses a raw per-agent temperature during configuration resolution.
 *
 * <p>Null or blank values remain unset. Invalid numbers are logged and ignored so configuration
 * errors do not break a request; {@code 0.0} is a valid temperature.</p>
 */
@Slf4j
public final class AgentTemperature {

    private AgentTemperature() {
    }

    public static @Nullable Double parse(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Ignoring invalid agent temperature: {}", raw);
            return null;
        }
    }
}
