package org.sterl.open.aifactory.config;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.FileLlmConfigStore;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.ai.LlmConfigLoader;
import org.sterl.llmpeon.ai.LlmConfigStore;
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

    /** Static and value-free on purpose — this text reaches the 400 body (R-WP-16 "kein Zeichen nach draußen"). */
    private static final String PROVIDER_HINT = "provider must name one of the supported providers";
    private static final String TOKEN_WINDOW_HINT = "tokenWindow must be a positive number of tokens";
    private static final String TIMEOUT_HINT = "timeoutSeconds must be a positive number of seconds";
    /** Value-free for the same reason — a turn's failure reaches the browser as a live line (Q1). */
    private static final String TURN_MODEL_HINT = "turn needs a model — set model in the factory config";
    private static final String TURN_API_KEY_HINT = "turn needs an api key — set apiKey in the factory config, or choose a provider that needs none";

    /** Key-free is factory knowledge (R-WP-9 names exactly these two); the core has no such flag. */
    private static final Set<AiProvider> KEY_FREE_PROVIDERS = Set.of(AiProvider.OLLAMA, AiProvider.LM_STUDIO);

    private final FactoryHome home;
    private final Environment environment;

    /**
     * Serialises the read-modify-write of {@link #update(SettingsUpdate)}: the store rewrites the whole
     * file, so an interleaved save would drop the other writer's key. Single node, single writer by
     * contract — the lock is the cheap insurance, not the guarantee.
     */
    private final ReentrantLock writeLock = new ReentrantLock();

    public FactoryConfigService(FactoryHome home, Environment environment) {
        this.home = home;
        this.environment = environment;
    }

    /**
     * Reads the file and the environment fresh on every call — a hand edit is picked up without a
     * restart, and reading never writes.
     */
    public LlmConfig load() {
        return pin(LlmConfigLoader.load(store()));
    }

    /**
     * {@link #load()} plus what a turn needs to reach a model at all — R-WP-9's "bereit für den Turn".
     * Checked here and not inside the provider: {@code OpenAiProvider} hands an unset key straight to
     * langchain4j, so the turn would fail provider-internal and never name the field (UC-WP-20 GIVEN 3).
     *
     * <p>The provider name is read from the store and compared by name. {@code AiProvider.parse} is
     * unusable for this: it coerces an unknown name to OLLAMA, which is one of the two key-free
     * providers, so a typo would have been waved through the key check instead of answered.</p>
     *
     * @throws TurnNotConfiguredException a field the turn needs is missing; the message names the field
     *                                    and never a value
     */
    public LlmConfig loadForTurn() {
        EnvFallbackLlmConfigStore connector = store();
        LlmConfig config = pin(LlmConfigLoader.load(connector));
        AiProvider provider = providerForTurn(connector, config);
        if (!present(config.getModel())) {
            throw new TurnNotConfiguredException(TURN_MODEL_HINT);
        }
        if (!KEY_FREE_PROVIDERS.contains(provider) && !present(config.getApiKey())) {
            throw new TurnNotConfiguredException(TURN_API_KEY_HINT);
        }
        return config;
    }


    /** The masked read model: the one place that ever sees {@link LlmConfig#getApiKey()}. */
    public SettingsSnapshot snapshot() {
        EnvFallbackLlmConfigStore connector = store();
        LlmConfig config = pin(LlmConfigLoader.load(connector));
        return new SettingsSnapshot(config.getProviderType().name(), config.getModel(), config.getUrl(),
                config.getAutoCompactAfter(), config.getTimeout().getSeconds(), config.isSendThinkingEnabled(),
                present(config.getApiKey()), connector.apiKeySource());
    }

    /**
     * Saves a submitted value and only a submitted value: a {@code null} or blank field never reaches
     * the store, which is what makes an emptied input mean "unchanged" although the core store reads a
     * blank as removal — through this API the key can be set, never deleted (UC-WP-24).
     *
     * <p>Validation runs before the first write, so a rejected request leaves the file untouched, and
     * the answer is a fresh masked read rather than the submitted body — the next delivery therefore
     * shows what is really stored (UC-WP-24 GIVEN 1).</p>
     *
     * <p>The write uses the same connector as the read: that connector is the single write owner, so a
     * saved value lands in the factory's own file and never in the environment, and a hand edit made
     * since the last read is respected instead of overwritten.</p>
     */
    public SettingsSnapshot update(SettingsUpdate update) {
        writeLock.lock();
        try {
            validate(update);
            LlmConfigStore connector = store();
            putIfPresent(connector, LlmConfigKeys.PROVIDER_TYPE, update.provider());
            putIfPresent(connector, LlmConfigKeys.MODEL, update.model());
            putIfPresent(connector, LlmConfigKeys.URL, update.url());
            putIfPresent(connector, LlmConfigKeys.API_KEY, update.apiKey());
            putIfPresent(connector, LlmConfigKeys.TOKEN_WINDOW, update.tokenWindow());
            putIfPresent(connector, LlmConfigKeys.TIMEOUT, update.timeoutSeconds());
            putIfPresent(connector, LlmConfigKeys.SEND_THINKING_ENABLED, update.sendThinking());
            return snapshot();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Hand-written because this module has no Bean Validation starter — and policy is the internal
     * facade's job anyway. Every message is a constant naming the <em>field</em>: a submitted value,
     * least of all a key, must not be repeated into a response body or a log line.
     */
    private static void validate(SettingsUpdate update) {
        // AiProvider.parse is forbidden here: it coerces an unknown name to OLLAMA, so a typo would be
        // answered with 200 and stored as garbage.
        if (present(update.provider()) && !isProviderName(update.provider().trim())) {
            throw new IllegalArgumentException(PROVIDER_HINT);
        }
        if (update.tokenWindow() != null && update.tokenWindow() <= 0) {
            throw new IllegalArgumentException(TOKEN_WINDOW_HINT);
        }
        if (update.timeoutSeconds() != null && update.timeoutSeconds() <= 0) {
            throw new IllegalArgumentException(TIMEOUT_HINT);
        }
    }

    private static boolean isProviderName(String value) {
        return Arrays.stream(AiProvider.values()).anyMatch(name -> name.name().equals(value));
    }

    /**
     * The provider a turn is built for, from the raw stored name — the loaded {@link LlmConfig} has
     * already been through the core's silent coercion, so it cannot answer whether the name was real.
     */
    private static AiProvider providerForTurn(LlmConfigStore store, LlmConfig loaded) {
        String rawName = store.get(LlmConfigKeys.PROVIDER_TYPE, null);
        if (rawName == null) {
            return loaded.getProviderType(); // absent everywhere = the core default, never a typo
        }
        rawName = rawName.trim();
        if (!isProviderName(rawName)) {
            throw new TurnNotConfiguredException(PROVIDER_HINT);
        }
        return AiProvider.valueOf(rawName);
    }

    /** Trimmed, and only when a value was actually submitted — absent and blank both write nothing. */
    private static void putIfPresent(LlmConfigStore store, String key, Object value) {
        if (value == null) {
            return;
        }
        String text = value.toString().trim();
        if (!text.isEmpty()) {
            store.put(key, text);
        }
    }

    private LlmConfig pin(LlmConfig loaded) {
        return loaded.toBuilder()
                .configDir(home.root())
                .diskToolsEnabled(true)
                .build();
    }

    /**
     * The one store seam for both directions — {@code load}, {@code snapshot} and {@code update} all
     * open it fresh, so a hand edit is seen without a restart and reading never writes.
     */
    private EnvFallbackLlmConfigStore store() {
        return new EnvFallbackLlmConfigStore(new FileLlmConfigStore(home.configFile()), environment);
    }

    /** A submitted value — {@code null} and blank are both "the client left this one alone". */
    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
