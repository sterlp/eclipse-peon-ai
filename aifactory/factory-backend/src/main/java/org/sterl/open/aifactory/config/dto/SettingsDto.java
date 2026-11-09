package org.sterl.open.aifactory.config.dto;

import org.sterl.open.aifactory.config.KeySource;
import org.sterl.open.aifactory.config.SettingsSnapshot;

/**
 * Wire shape of the settings view: the documented fields plus the key <em>status</em> and its source —
 * the key itself has no field (R-WP-16 "Maskieren heißt: kein Zeichen nach draußen"). The component
 * list is pinned by exact allow-list equality in {@code SettingsControllerTest}.
 *
 * <p>The field order is the form's order, so the page reads top to bottom (R-WP-49, R-WP-51).</p>
 */
public record SettingsDto(String provider, String model, String think, String url, int tokenWindow,
        long timeoutSeconds, boolean sendThinking, Integer maxTokens, String queryParams, String headerParams,
        boolean keySet, KeySource keySource) {

    public static SettingsDto of(SettingsSnapshot snapshot) {
        return new SettingsDto(snapshot.provider(), snapshot.model(), snapshot.think(), snapshot.url(),
                snapshot.tokenWindow(), snapshot.timeoutSeconds(), snapshot.sendThinking(),
                snapshot.maxTokens(), snapshot.queryParams(), snapshot.headerParams(),
                snapshot.keySet(), snapshot.keySource());
    }
}
