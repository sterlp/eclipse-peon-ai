package org.sterl.open.aifactory.config;

import java.util.Map;

import org.springframework.core.env.Environment;

import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.ai.LlmConfigStore;

/**
 * Connector for the factory's config: its own file first, the process environment as start value only
 * (R-WP-9 — "zusätzlich aus Umgebungsvariablen als Startwert, wenn die Datei den Wert nicht trägt",
 * R-WP-16 — "was in der Datei steht, gewinnt").
 *
 * <p>A blank value is an absent value: {@code llm.model=} falls through to the environment and then to
 * the caller's default, so an emptied line never shadows the start value.</p>
 *
 * <p>Only these four keys have an environment name. {@code llm.configDirectory} and
 * {@code llm.diskToolsEnabled} are named here <em>not at all</em> and are pinned after the load
 * ({@link FactoryConfigService#load()}) — the two values the factory alone decides.</p>
 */
final class EnvFallbackLlmConfigStore implements LlmConfigStore {

    /**
     * Resolved through {@link Environment} rather than {@code System.getenv} so the fallback branch is
     * reachable from a test; at runtime {@code SystemEnvironmentPropertySource} answers for the real OS
     * variable — the same mechanism {@code FactoryHome} uses for {@code AIFACTORY_HOME}.
     */
    static final Map<String, String> ENV_NAMES = Map.of(
            LlmConfigKeys.PROVIDER_TYPE, "AIFACTORY_LLM_PROVIDER",
            LlmConfigKeys.MODEL, "AIFACTORY_LLM_MODEL",
            LlmConfigKeys.URL, "AIFACTORY_LLM_URL",
            LlmConfigKeys.API_KEY, "AIFACTORY_LLM_API_KEY");

    private final LlmConfigStore file;
    private final Environment environment;

    EnvFallbackLlmConfigStore(LlmConfigStore file, Environment environment) {
        this.file = file;
        this.environment = environment;
    }

    @Override
    public String get(String key, String defaultValue) {
        String value = present(file.get(key, null));
        if (value == null) {
            value = fromEnvironment(key);
        }
        return value == null ? defaultValue : value;
    }

    @Override
    public void put(String key, String value) {
        file.put(key, value);
    }

    @Override
    public void remove(String key) {
        file.remove(key);
    }

    /**
     * The provenance of the effective api key — the only thing about the key that may be reported
     * (R-WP-16), which is why it answers with a source and never with the value.
     */
    KeySource apiKeySource() {
        if (present(file.get(LlmConfigKeys.API_KEY, null)) != null) {
            return KeySource.FILE;
        }
        return fromEnvironment(LlmConfigKeys.API_KEY) == null ? KeySource.NONE : KeySource.ENV;
    }

    private String fromEnvironment(String key) {
        String name = ENV_NAMES.get(key);
        return name == null ? null : present(environment.getProperty(name));
    }

    private static String present(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
