package org.sterl.open.aifactory.config;

/**
 * Internal read model of {@link FactoryConfigService}. Masking is a type here, not a filter: there is
 * no component a key could sit in, so nothing can be forgotten at serialization time. The component
 * list is pinned by exact allow-list equality in the tests — any added component turns that test red
 * whatever it is called.
 */
public record SettingsSnapshot(String provider, String model, String url, int tokenWindow,
        long timeoutSeconds, boolean sendThinking, boolean keySet, KeySource keySource) {
}
