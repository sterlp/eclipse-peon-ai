package org.sterl.open.aifactory.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import org.sterl.llmpeon.ai.FileLlmConfigStore;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.LlmConfigLoader;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The factory's own config (R-WP-9): one properties file below the factory home, never the plugin's
 * preference store — that is not even on this classpath, so "the plugin's config stays byte-identical"
 * holds by construction. Nothing of the file is ever logged; the key is read in {@link #snapshot()}
 * and dropped there.
 *
 * <p>Two values the factory alone decides are pinned <b>after</b> the load, so a hand-written key in
 * the file cannot win: the config dir, which would otherwise move agent state to {@code ~/.peon}
 * (UC-WP-36), and {@code diskToolsEnabled}, without which the factory could not work (UC-WP-37).</p>
 */
@Service
public class FactoryConfigService {

    private final FactoryHome home;
    private final Environment environment;

    public FactoryConfigService(FactoryHome home, Environment environment) {
        this.home = home;
        this.environment = environment;
    }

    /**
     * Reads the file and the environment fresh on every call — a hand edit is picked up without a
     * restart, and reading never writes.
     */
    public LlmConfig load() {
        return pin(LlmConfigLoader.load(readStore()));
    }

    /** The masked read model: the one place that ever sees {@link LlmConfig#getApiKey()}. */
    public SettingsSnapshot snapshot() {
        EnvFallbackLlmConfigStore store = readStore();
        LlmConfig config = pin(LlmConfigLoader.load(store));
        return new SettingsSnapshot(config.getProviderType().name(), config.getModel(), config.getUrl(),
                config.getAutoCompactAfter(), config.getTimeout().getSeconds(), config.isSendThinkingEnabled(),
                isSet(config.getApiKey()), store.apiKeySource());
    }

    private LlmConfig pin(LlmConfig loaded) {
        return loaded.toBuilder()
                .configDir(home.root())
                .diskToolsEnabled(true)
                .build();
    }

    private EnvFallbackLlmConfigStore readStore() {
        return new EnvFallbackLlmConfigStore(new FileLlmConfigStore(home.configFile()), environment);
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
