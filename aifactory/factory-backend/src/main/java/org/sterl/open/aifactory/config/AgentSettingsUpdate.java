package org.sterl.open.aifactory.config;

/**
 * One save of one slot. {@code overridden} is the checkbox: {@code false} deletes every config value of
 * the slot (R-WP-23), {@code true} writes the fields. The fields are text so the three states of R-WP-16
 * stay apart — {@code null} keeps, {@code ""} deletes, a value sets. Not the inbound JSON model.
 */
public record AgentSettingsUpdate(boolean overridden, String model, String url, String think,
        String temperature, String apiKey, boolean removeApiKey) {
}
