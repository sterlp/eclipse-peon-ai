package org.sterl.open.aifactory.config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Static and value-free on purpose — this text reaches the 400 body (R-WP-16 "kein Zeichen nach
     * draußen"). One wording for both doors that take a provider name, the save and the model list
     * ({@link ModelListService}).
     */
    static final String PROVIDER_HINT = "provider must name one of the supported providers";
    private static final String TOKEN_WINDOW_HINT = "tokenWindow must be a positive number of tokens";
    private static final String TIMEOUT_HINT = "timeoutSeconds must be a positive number of seconds";
    private static final String SEND_THINKING_HINT = "sendThinking must be true or false";
    /** 0 is a legal answer here and means "the provider decides" — unlike the two fields above (R-WP-51). */
    private static final String MAX_TOKENS_HINT = "maxTokens must be a whole number of tokens, or 0 for the "
            + "provider default";
    private static final String QUERY_PARAMS_HINT = "queryParams must be comma separated key=value pairs";
    private static final String HEADER_PARAMS_HINT = "headerParams must be comma separated key=value pairs";
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
     * a think input at all, plus that input's form for the agent slots (UC-WP-67) and whether the provider
     * accepts an {@code extra_body} (R-WP-52). No model default, because
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
                            support instanceof ThinkSupport.Values values ? values.values() : List.of(),
                            provider.supportsExtraBody());
                })
                .toList();
    }

    /**
     * The masked read model: the one place that ever sees {@link LlmConfig#getApiKey()}.
     *
     * <p>{@code think} is the dev slot's record read <em>after</em> {@link #pin(LlmConfig)}, so the
     * default page and the agent section answer the very value a turn sends (R-WP-49). The two param
     * fields answer as the text the file carries: the core keeps them as a map, and re-rendering that
     * map would give one field a second spelling.</p>
     */
    public SettingsSnapshot snapshot() {
        EnvFallbackLlmConfigStore connector = store();
        LlmConfig config = pin(LlmConfigLoader.load(connector));
        return new SettingsSnapshot(config.getProviderType().name(), config.getModel(),
                config.modelConfigFor(AgentModelConfig.DEV).think(), config.getUrl(),
                config.getAutoCompactAfter(), config.getTimeout().getSeconds(), config.isSendThinkingEnabled(),
                providerDefaultOrNumber(config.getMaxTokens()), connector.get(LlmConfigKeys.QUERY_PARAMS, null),
                connector.get(LlmConfigKeys.HEADER_PARAMS, null),
                present(config.getApiKey()), connector.apiKeySource());
    }

    /** The core's 0 is "the provider decides" (R-WP-51), which an empty field shows — not a typed zero. */
    private static Integer providerDefaultOrNumber(int maxTokens) {
        return maxTokens == 0 ? null : maxTokens;
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
            apply(connector, LlmConfigKeys.MAX_TOKENS, providerDefaultMeansGone(update.maxTokens()));
            apply(connector, LlmConfigKeys.QUERY_PARAMS, update.queryParams());
            apply(connector, LlmConfigKeys.HEADER_PARAMS, update.headerParams());
            applyDefaultThink(update.think());
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
        if (present(update.maxTokens())) {
            requireTokenCount(update.maxTokens(), MAX_TOKENS_HINT);
        }
        if (present(update.queryParams())) {
            requireParamList(update.queryParams(), QUERY_PARAMS_HINT);
        }
        if (present(update.headerParams())) {
            requireParamList(update.headerParams(), HEADER_PARAMS_HINT);
        }
    }

    /**
     * {@code LlmConfigLoader.parseInt} turns garbage into 0, which is the core's "provider default" — so
     * a value it cannot read has to be refused here, where an answer can still name the field. 0 itself
     * is legal and stores as the key being gone; the range is the one the core parses back.
     */
    private static void requireTokenCount(String value, String hint) {
        long number;
        try {
            number = Long.parseLong(value.trim());
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(hint);
        }
        if (number < 0 || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(hint);
        }
    }

    /**
     * One segment without {@code =} would reach the core as a header or query variable named after
     * itself with an empty value ({@code parseCsvMap}) — that is never what was typed, so R-WP-51's
     * "unparsbar ⇒ nichts speichern" answers it. Blank segments are the core's own tolerance (a trailing
     * comma stays a trailing comma), so they stay legal.
     */
    private static void requireParamList(String value, String hint) {
        for (String segment : value.split(",")) {
            if (!segment.isBlank() && !segment.contains("=")) {
                throw new IllegalArgumentException(hint);
            }
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

    /** Max tokens 0 says "the provider decides" (R-WP-51), which is the key being absent, not a stored zero. */
    private static String providerDefaultMeansGone(String value) {
        return present(value) && "0".equals(value.trim()) ? "" : value;
    }

    /**
     * "Think (Default)" <b>is</b> the dev slot's field (R-WP-49): the core has no base think key, so a
     * properties entry would be a second store the agent section could not see. The write therefore goes
     * through the slot's own file and its lock, both under {@link #writeLock}. The file lock is released
     * again before {@link #snapshot()} reads the value back, so the two locks are never nested.
     */
    private void applyDefaultThink(String think) {
        if (think == null) {
            return;
        }
        AgentConfigFile.forSlot(home, AgentModelConfig.DEV)
                .write(new AgentConfigFile.Changes(false, null, null, null, think, null, null, null));
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
     *
     * <p>{@code tools} (R-WP-52) does not live in the record — the core reads it from its own map — so a
     * file that names tools joins that map, entry per slot, and wins over a {@code llm.agent.<id>.tools}
     * line the same way the record wins over the property. The raw wording travels untouched; only the
     * core interprets it (D9).</p>
     */
    private LlmConfig withAgentOverrides(LlmConfig loaded) {
        LlmConfig config = loaded;
        Map<String, String> tools = null;
        for (AgentSlots.AgentSlot slot : AgentSlots.all()) {
            AgentConfigFile.Values values = AgentConfigFile.forSlot(home, slot.id()).read();
            if (values.overridden()) {
                config = config.withModelConfig(slot.id(), recordFor(slot.id(), values));
            }
            if (present(values.tools())) {
                if (tools == null) {
                    tools = new LinkedHashMap<>(config.getAgentTools());
                }
                tools.put(slot.id(), values.tools().trim());
            }
        }
        return tools == null ? config : config.toBuilder().agentTools(tools).build();
    }

    /** The file's seven values as the core's record — blanks unset; {@code tools} is no record field. */
    private static AgentModelConfig recordFor(String id, AgentConfigFile.Values values) {
        boolean dev = AgentModelConfig.DEV.equals(id);
        return new AgentModelConfig(dev ? null : blankToNull(values.url()),
                dev ? null : blankToNull(values.apiKey()),
                dev ? null : blankToNull(values.model()),
                blankToNull(values.think()),
                blankToNull(values.extraBody()),
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

    /** Package-internal because {@link ModelListService} refuses the same names for the same reason. */
    static boolean isProviderName(String value) {
        return Arrays.stream(AiProvider.values()).anyMatch(name -> name.name().equals(value));
    }
}
