package org.sterl.open.aifactory.config;

/**
 * The internal write model of {@link FactoryConfigService#update(SettingsUpdate)} — the command form
 * of the inbound request. Every visible field carries <b>three</b> states (R-WP-16, UC-WP-46):
 *
 * <ul>
 * <li>{@code null} — the client named nothing, the file keeps the key as it is,</li>
 * <li>empty — the user cleared the field, the key is <b>removed</b> from the file, so what applies
 * without it applies again (the environment, or the core default),</li>
 * <li>a value — stored, trimmed.</li>
 * </ul>
 *
 * <p>Token window, timeout and think are text here and only here: a number or a boolean cannot express
 * the empty state, and "cleared" is exactly what has to reach the file. Parsing and range checking are
 * the service's job, so an unparsable value is answered with the field's name and never stored.</p>
 *
 * <p>The api key is the documented exception — write-only and never prefilled, so an empty field means
 * unchanged and only {@code removeApiKey} deletes (UC-WP-24 GIVEN 2, UC-WP-63).</p>
 *
 * <p>There is deliberately no field for the config directory or the disk tools — the two values the
 * factory pins for itself (UC-WP-36/37) are unreachable from the wire. The DTO boundary is the external
 * facade, so this type is not the inbound JSON model.</p>
 */
public record SettingsUpdate(String provider, String model, String url, String apiKey,
        String tokenWindow, String timeoutSeconds, String sendThinking, boolean removeApiKey) {
}
