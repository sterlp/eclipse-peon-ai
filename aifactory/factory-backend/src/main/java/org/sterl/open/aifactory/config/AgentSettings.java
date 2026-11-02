package org.sterl.open.aifactory.config;

/**
 * The masked state of one agent slot (R-WP-23): what its own file carries, plus the checkbox the file
 * implies. The key is never part of it — only {@code keySet} is, because the page must not be able to
 * show a character of it (R-WP-16 "kein Zeichen nach draußen").
 */
public record AgentSettings(String id, String name, boolean overridden, String model, String url,
        String think, String temperature, boolean keySet) {
}
