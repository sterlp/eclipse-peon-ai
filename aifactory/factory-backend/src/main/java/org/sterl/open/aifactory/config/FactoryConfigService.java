package org.sterl.open.aifactory.config;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.FileLlmConfigStore;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.ai.LlmConfigLoader;
import org.sterl.llmpeon.ai.LlmConfigStore;
import org.sterl.llmpeon.provider.LlmProvider;
import org.sterl.llmpeon.provider.LlmProviders;
import org.sterl.llmpeon.provider.ThinkSupport;
import org.sterl.open.aifactory.config.agent.AgentConfigFile;
import org.sterl.open.aifactory.config.agent.AgentSlots;
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
    private static final String SEND_THINKING_HINT = "sendThinking must be true or false";
    /**
     * Both at once is no answer at all: the write order would silently delete the key the same request
     * just set, so the request is refused instead of guessed. The agent level reads the same rule
     * ({@link AgentSettingsService}) — one sentence, one source.
     */
    static final String KEY_INTENT_HINT = "apiKey and removeApiKey are two answers — send one";
    /** Value-free for the same reason — a turn's failure reaches the browser as a live line (Q1). */
    private static final String TURN_MODEL_HINT = "turn needs a model — set model in the factory config";

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
            throw new TurnNotConfiguredException(turnApiKeyHint(provider));
        }
        return config;
    }

    /**
     * R-WP-16: the failure of UC-WP-20 names the page that solves it and the provider the key is missing
     * for. Both are the factory's own values — the provider has been checked against the enum before this
     * line — so the rule that a submitted value never returns still holds.
     */
    private static String turnApiKeyHint(AiProvider provider) {
        return "turn needs an api key — set apiKey in Settings, or choose a provider that needs none"
                + " (provider: " + provider.name() + ")";
    }

    /**
     * What the core knows per provider (UC-WP-62): the base URL it falls back to and whether it can carry
     * a think input at all, plus that input's form for the agent slots (UC-WP-67). No model default, because
     * the core knows none — a page that invented one would carry over a value belonging to another provider.
     *
     * <p>{@link ThinkSupport.Unknown} counts as supported: it says the form is not known <em>yet</em>,
     * which is not the claim "this provider has no think input" that the disabled field makes.</p>
     */
    public List<ProviderDefaults> providerDefaults() {
        return Arrays.stream(AiProvider.values())
                .map(name -> {
                    LlmProvider provider = LlmProviders.of(name);
                    ThinkSupport support = provider.thinkSupport();
                    return new ProviderDefaults(name.name(), provider.defaultBaseUrl(),
                            !(support instanceof ThinkSupport.None), ThinkForm.of(support),
                            support instanceof ThinkSupport.Values values ? values.values() : List.of());
                })
                .toList();
    }

    /**
     * The masked read model: the one place that ever sees {@link LlmConfig#getApiKey()}.
     */
    public SettingsSnapshot snapshot() {
        EnvFallbackLlmConfigStore connector = store();
        LlmConfig config = pin(LlmConfigLoader.load(connector));
        return new SettingsSnapshot(config.getProviderType().name(), config.getModel(), config.getUrl(),
                config.getAutoCompactAfter(), config.getTimeout().getSeconds(), config.isSendThinkingEnabled(),
                present(config.getApiKey()), connector.apiKeySource());
    }

    /**
     * Saves in the three states of R-WP-16: a field the request does not name keeps its value, a field
     * the user <b>cleared</b> is deleted from the file (UC-WP-46 — afterwards what applies without it
     * applies again), and a field with a value is stored trimmed. The api key is the documented
     * exception: write-only, never prefilled, so an empty field means unchanged and only
     * {@code removeApiKey} deletes (UC-WP-24 GIVEN 2, UC-WP-63).
     *
     * <p>Validation runs before the first write, so a rejected request leaves the file untouched, and the
     * answer is a fresh masked read rather than the submitted body — the next delivery therefore shows
     * what is really stored (UC-WP-24 GIVEN 1).</p>
     *
     * <p>Deleting a key the file does not carry rewrites nothing, so a save without an edit still changes
     * no byte even though the page names every field. The write uses the same connector as the read: that
     * connector is the single write owner, so a saved value lands in the factory's own file and never in
     * the environment, and a hand edit made since the last read is respected instead of overwritten.</p>
     */
    public SettingsSnapshot update(SettingsUpdate update) {
        writeLock.lock();
        try {
            validate(update);
            LlmConfigStore connector = store();
            apply(connector, LlmConfigKeys.PROVIDER_TYPE, update.provider());
            apply(connector, LlmConfigKeys.MODEL, update.model());
            apply(connector, LlmConfigKeys.URL, update.url());
            applyApiKey(connector, update.apiKey(), update.removeApiKey());
            apply(connector, LlmConfigKeys.TOKEN_WINDOW, update.tokenWindow());
            apply(connector, LlmConfigKeys.TIMEOUT, update.timeoutSeconds());
            apply(connector, LlmConfigKeys.SEND_THINKING_ENABLED, normalizedBoolean(update.sendThinking()));
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
        if (update.removeApiKey() && present(update.apiKey())) {
            throw new IllegalArgumentException(KEY_INTENT_HINT);
        }
        // AiProvider.parse is forbidden here: it coerces an unknown name to OLLAMA, so a typo would be
        // answered with 200 and stored as garbage.
        if (present(update.provider()) && !isProviderName(update.provider().trim())) {
            throw new IllegalArgumentException(PROVIDER_HINT);
        }
        if (present(update.tokenWindow())) {
            requirePositive(update.tokenWindow(), TOKEN_WINDOW_HINT, Integer.MAX_VALUE);
        }
        if (present(update.timeoutSeconds())) {
            requirePositive(update.timeoutSeconds(), TIMEOUT_HINT, Long.MAX_VALUE);
        }
        if (present(update.sendThinking()) && !isBoolean(update.sendThinking().trim())) {
            throw new IllegalArgumentException(SEND_THINKING_HINT);
        }
    }

    /**
     * The file holds text and the core reads these fields back as a number, so the range the core can
     * parse is the range this edge accepts — anything else would be stored happily and answer later with
     * the core's silent default, which is a wrong value the user never asked for.
     */
    private static void requirePositive(String value, String hint, long limit) {
        long number;
        try {
            number = Long.parseLong(value.trim());
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(hint);
        }
        if (number <= 0 || number > limit) {
            throw new IllegalArgumentException(hint);
        }
    }

    private static boolean isBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
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

    /**
     * The three states of one visible field (UC-WP-46). An absent field is not submitted at all; an empty
     * one is the user clearing it, which the store reads as removal — that is the whole point of handing
     * it a blank here.
     */
    private static void apply(LlmConfigStore store, String key, String value) {
        if (value == null) {
            return;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            store.remove(key);
            return;
        }
        store.put(key, text);
    }

    /**
     * The key is the exception of R-WP-16: its field is write-only and never prefilled, so a blank means
     * unchanged and only {@code removeApiKey} deletes. Removal is idempotent and reaches the file only —
     * a key the environment still supplies stays effective, and the answer names ENV as its source.
     */
    private static void applyApiKey(LlmConfigStore store, String value, boolean removeApiKey) {
        if (removeApiKey) {
            store.remove(LlmConfigKeys.API_KEY);
            return;
        }
        if (present(value)) {
            store.put(LlmConfigKeys.API_KEY, value.trim());
        }
    }

    /** Stored the way the core reads it, so a saved {@code TRUE} does not stay in the file as {@code TRUE}. */
    private static String normalizedBoolean(String value) {
        return present(value) ? Boolean.toString(Boolean.parseBoolean(value.trim())) : value;
    }

    private LlmConfig pin(LlmConfig loaded) {
        return withAgentOverrides(loaded.toBuilder()
                .configDir(home.root())
                .diskToolsEnabled(true)
                .build());
    }

    /**
     * R-WP-23 (D-11): what an agent's own file says wins over the loaded config — one record per slot, no
     * second store, and the file read again with every load, so a save needs no restart and no runtime
     * cache. A slot whose file carries nothing is never put on the config at all, which keeps "empty
     * means unset" the core's resolution instead of an empty record of ours.
     *
     * <p>The record is built the way {@link LlmConfigLoader} builds one, so the factory adds no semantics
     * of its own: the dev slot carries no model, url or key because dev <em>is</em> the base slot
     * (ADR-0062; {@code devAgentConfig()} forces the base model whatever the file says).</p>
     */
    private LlmConfig withAgentOverrides(LlmConfig loaded) {
        LlmConfig config = loaded;
        for (AgentSlots.AgentSlot slot : AgentSlots.all()) {
            AgentConfigFile.Values values = AgentConfigFile.forSlot(home, slot.id()).read();
            if (values.overridden()) {
                config = config.withModelConfig(slot.id(), recordFor(slot.id(), values));
            }
        }
        return config;
    }

    /** The file's five values as the core's record — blanks unset, {@code extra_body} unused (D-13). */
    private static AgentModelConfig recordFor(String id, AgentConfigFile.Values values) {
        boolean dev = AgentModelConfig.DEV.equals(id);
        return new AgentModelConfig(dev ? null : blankToNull(values.url()),
                dev ? null : blankToNull(values.apiKey()),
                dev ? null : blankToNull(values.model()),
                blankToNull(values.think()),
                null,
                blankToNull(values.temperature()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * The one store seam for both directions — {@code load}, {@code snapshot} and {@code update} all
     * open it fresh, so a hand edit is seen without a restart and reading never writes.
     */
    private EnvFallbackLlmConfigStore store() {
        return new EnvFallbackLlmConfigStore(new FileLlmConfigStore(home.configFile()), environment);
    }

    /** A submitted value — {@code null} and blank are the two states that never store anything. */
    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isProviderName(String value) {
        return Arrays.stream(AiProvider.values()).anyMatch(name -> name.name().equals(value));
    }
}
