package org.sterl.open.aifactory.config.dto;

import org.sterl.open.aifactory.config.KeySource;
import org.sterl.open.aifactory.config.SettingsSnapshot;

/**
 * Wire shape of the settings view: the six documented fields plus the key <em>status</em> and its
 * source — the key itself has no field (R-WP-16 "Maskieren heißt: kein Zeichen nach draußen"). The
 * component list is pinned by exact allow-list equality in {@code SettingsControllerTest}.
 */
public record SettingsDto(String provider, String model, String url, int tokenWindow,
        long timeoutSeconds, boolean sendThinking, boolean keySet, KeySource keySource) {

    public static SettingsDto of(SettingsSnapshot snapshot) {
        return new SettingsDto(snapshot.provider(), snapshot.model(), snapshot.url(),
                snapshot.tokenWindow(), snapshot.timeoutSeconds(), snapshot.sendThinking(),
                snapshot.keySet(), snapshot.keySource());
    }
}
