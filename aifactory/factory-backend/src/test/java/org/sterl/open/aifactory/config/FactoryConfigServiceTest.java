package org.sterl.open.aifactory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import org.sterl.llmpeon.ai.FileLlmConfigStore;
import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.ai.LlmConfigStore;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The read path of the factory's own config (R-WP-9). Every test runs against a throwaway home — no
 * test reads or writes the developer's {@code ~/.aifactory} or the plugin's config dir, so a deleted
 * pin turns a test red instead of touching a real config.
 */
class FactoryConfigServiceTest {

    private static final String KEY = "sk-file-secret-0123456789";
    private static final String WRITTEN_KEY = "sk-written-secret-2468013579";
    private static final String AGENT_KEY = "sk-agent-secret-9876543210";

    @TempDir
    Path home;

    /** The directory a hand-written {@code llm.configDirectory} tries to move agent state into. */
    @TempDir
    Path other;

    /**
     * Stands in for the plugin's own state location. The doc's literal {@code ~/.peon} is no usable
     * GIVEN — the core resolves with {@code Path.of}, which never expands a tilde — so this is the
     * same threat as a real absolute path that the pin could actually fail to defeat.
     */
    @TempDir
    Path pluginHome;

    @Test
    // UC-WP-36
    void configDirIsAlwaysTheFactoryHome() {
        // GIVEN 1: the file names another directory by hand — the factory home wins anyway
        writeConfig("%s=%s%n".formatted(LlmConfigKeys.CONFIG_DIRECTORY, other));

        assertThat(service().load().getConfigDir()).isEqualTo(home.toAbsolutePath().normalize());
        // nothing was read from or written to the directory the file asked for
        assertThat(other).isEmptyDirectory();

        // GIVEN 2: the value is the plugin's own location, absolute and one level below a real home
        writeConfig("%s=%s%n".formatted(LlmConfigKeys.CONFIG_DIRECTORY, pluginHome.resolve(".peon")));

        assertThat(service().load().getConfigDir()).isEqualTo(home.toAbsolutePath().normalize());
        assertThat(pluginHome).isEmptyDirectory();
    }

    @Test
    // UC-WP-37
    void diskToolsAreAlwaysOn() {
        // GIVEN a config file without any mention of the disk tools — the core default is false
        assertThat(home.resolve(FactoryHome.CONFIG_FILE)).doesNotExist();
        assertThat(service().load().isDiskToolsEnabled()).isTrue();
        assertThat(home.resolve(FactoryHome.CONFIG_FILE)).doesNotExist(); // reading is not writing

        // GIVEN a hand-written false — R-WP-12: the factory could not do its work without them
        writeConfig("%s=false%n".formatted(LlmConfigKeys.DISK_TOOLS_ENABLED));

        assertThat(service().load().isDiskToolsEnabled()).isTrue();
    }

    @Test
    // UC-WP-23
    void settingsShowsMaskedConfig() {
        writeConfig("""
                %s=OPEN_AI
                %s=gpt-demo
                %s=https://api.example.com/v1
                %s=%s
                %s=64000
                %s=45
                %s=false
                """.formatted(LlmConfigKeys.PROVIDER_TYPE, LlmConfigKeys.MODEL, LlmConfigKeys.URL,
                        LlmConfigKeys.API_KEY, KEY, LlmConfigKeys.TOKEN_WINDOW, LlmConfigKeys.TIMEOUT,
                        LlmConfigKeys.SEND_THINKING_ENABLED));

        SettingsSnapshot snapshot = service().snapshot();

        assertThat(snapshot.provider()).isEqualTo("OPEN_AI");
        assertThat(snapshot.model()).isEqualTo("gpt-demo");
        assertThat(snapshot.url()).isEqualTo("https://api.example.com/v1");
        assertThat(snapshot.tokenWindow()).isEqualTo(64000);
        assertThat(snapshot.timeoutSeconds()).isEqualTo(45);
        assertThat(snapshot.sendThinking()).isFalse();
        assertThat(snapshot.keySet()).isTrue();
        assertThat(snapshot.keySource()).isEqualTo(KeySource.FILE);

        // masking by type: no component a key could sit in — whatever a later one is called
        assertThat(componentNamesOf(SettingsSnapshot.class)).containsExactly("provider", "model", "url",
                "tokenWindow", "timeoutSeconds", "sendThinking", "keySet", "keySource");

        assertThat(snapshot.toString()).doesNotContain(KEY, KEY.substring(0, 6), KEY.substring(KEY.length() - 6));
    }

    /**
     * {@code LlmConfig} is the one value-bearing object the factory hands out — the masked snapshot is
     * derived from it, and Inc 3's runtime will hold it. This pins the redaction of its declared
     * {@code apiKey} <b>and</b> the one a per-agent record carries, which {@code LlmConfig.toString()}
     * renders through {@code AgentModelConfig.toString()}: a future field that stringifies a key, on
     * either type, turns this red.
     */
    @Test
    void loadedConfigNeverStringsEitherKey() {
        writeConfig("""
                %s=%s
                %s=%s
                """.formatted(LlmConfigKeys.API_KEY, KEY,
                        LlmConfigKeys.agentKey("po", LlmConfigKeys.AGENT_FIELD_API_KEY), AGENT_KEY));

        String rendered = service().load().toString();

        // head and tail too — a redaction that prints "sk-…9876" would pass a whole-value check
        assertThat(rendered).doesNotContain(KEY, AGENT_KEY)
                .doesNotContain(KEY.substring(0, 6), AGENT_KEY.substring(0, 6))
                .doesNotContain(KEY.substring(KEY.length() - 6), AGENT_KEY.substring(AGENT_KEY.length() - 6));
    }

    @Test
    // UC-WP-38
    void configFileWinsOverEnvironment() {
        MockEnvironment environment = environment();

        // GIVEN both carry a value, differently — the file is the value and the file is the source
        writeConfig("""
                %s=OPEN_AI
                %s=file-model
                %s=https://file.example
                %s=%s
                """.formatted(LlmConfigKeys.PROVIDER_TYPE, LlmConfigKeys.MODEL, LlmConfigKeys.URL,
                        LlmConfigKeys.API_KEY, KEY));

        SettingsSnapshot fromFile = service(environment).snapshot();
        assertThat(fromFile.provider()).isEqualTo("OPEN_AI");
        assertThat(fromFile.model()).isEqualTo("file-model");
        assertThat(fromFile.url()).isEqualTo("https://file.example");
        assertThat(fromFile.keySet()).isTrue();
        assertThat(fromFile.keySource()).isEqualTo(KeySource.FILE);

        // GIVEN the file carries no value — an emptied line is no value either — the env is the start value
        writeConfig("%s=%n".formatted(LlmConfigKeys.MODEL));

        SettingsSnapshot fromEnvironment = service(environment).snapshot();
        assertThat(fromEnvironment.provider()).isEqualTo("MISTRAL");
        assertThat(fromEnvironment.model()).isEqualTo("env-model");
        assertThat(fromEnvironment.url()).isEqualTo("https://env.example");
        assertThat(fromEnvironment.keySet()).isTrue();
        assertThat(fromEnvironment.keySource()).isEqualTo(KeySource.ENV);

        // GIVEN neither carries anything — the documented core defaults appear, and nothing is "set"
        deleteConfig();

        SettingsSnapshot fromDefaults = service(new MockEnvironment()).snapshot();
        assertThat(fromDefaults.provider()).isEqualTo("OLLAMA");
        assertThat(fromDefaults.tokenWindow()).isEqualTo(80_000);
        assertThat(fromDefaults.timeoutSeconds()).isEqualTo(180);
        assertThat(fromDefaults.sendThinking()).isTrue();
        assertThat(fromDefaults.keySet()).isFalse();
        assertThat(fromDefaults.keySource()).isEqualTo(KeySource.NONE);
    }

    /**
     * A store that <em>stores</em> blanks — the plugin's preference store does exactly that — must
     * still not shadow the start value: "GIVEN die Datei trägt keinen Wert" includes the emptied line.
     */
    @Test
    // UC-WP-38
    void aBlankFileValueIsNoValueAndNeverShadowsTheStartValue() {
        EnvFallbackLlmConfigStore store = new EnvFallbackLlmConfigStore(
                new BlankAnsweringStore(Map.of(LlmConfigKeys.MODEL, "   ", LlmConfigKeys.API_KEY, "")),
                environment());

        assertThat(store.get(LlmConfigKeys.MODEL, null)).isEqualTo("env-model");
        assertThat(store.get(LlmConfigKeys.URL, null)).isEqualTo("https://env.example");
        assertThat(store.apiKeySource()).isEqualTo(KeySource.ENV);
    }

    /**
     * UC-WP-24 GIVEN 1 + GIVEN 2. GIVEN 3 ("der nächste Turn nutzt den neuen Wert") is <b>not</b>
     * covered here — there is no runtime and no turn in the MVP yet, so this UC is partially covered
     * and finished in Increment 3.
     */
    @Test
    // UC-WP-24
    void settingsOverrideNeverWritesSharedStore() throws Exception {
        writeConfig("""
                %s=OLLAMA
                %s=old-model
                %s=https://old.example
                %s=40000
                %s=agent-model
                custom.key=hand-made
                """.formatted(LlmConfigKeys.PROVIDER_TYPE, LlmConfigKeys.MODEL, LlmConfigKeys.URL,
                        LlmConfigKeys.TOKEN_WINDOW,
                        LlmConfigKeys.agentKey("plan", LlmConfigKeys.AGENT_FIELD_MODEL)));
        Map<Path, byte[]> before = treeBytes();

        SettingsSnapshot saved = service().update(new SettingsUpdate(null, "new-model", "https://new.example",
                WRITTEN_KEY, null, null, null));

        // the answer of a save is the stored state, masked — never the submitted body
        assertThat(saved.model()).isEqualTo("new-model");
        assertThat(saved.keySet()).isTrue();
        assertThat(saved.keySource()).isEqualTo(KeySource.FILE);

        FileLlmConfigStore stored = new FileLlmConfigStore(config());
        assertThat(stored.get(LlmConfigKeys.MODEL, null)).isEqualTo("new-model");
        assertThat(stored.get(LlmConfigKeys.URL, null)).isEqualTo("https://new.example");
        assertThat(stored.get(LlmConfigKeys.API_KEY, null)).isEqualTo(WRITTEN_KEY);
        // everything the request did not name is still there, unknown keys included
        assertThat(stored.get(LlmConfigKeys.PROVIDER_TYPE, null)).isEqualTo("OLLAMA");
        assertThat(stored.get(LlmConfigKeys.TOKEN_WINDOW, null)).isEqualTo("40000");
        assertThat(stored.get(LlmConfigKeys.agentKey("plan", LlmConfigKeys.AGENT_FIELD_MODEL), null))
                .isEqualTo("agent-model");
        assertThat(stored.get("custom.key", null)).isEqualTo("hand-made");

        // the plugin's preference store is not even on this classpath — the executable half of
        // "the shared plugin config stays byte-identical"
        assertThatThrownBy(() -> Class.forName("org.sterl.llmpeon.parts.config.EclipseLlmConfigStore"))
                .isInstanceOf(ClassNotFoundException.class);

        // byte level over the whole home: config.properties is the one file that changed, no temp file
        assertThat(differingPaths(before, treeBytes())).containsExactly(config());
        // and the next read sees it without a restart
        assertThat(service().load().getModel()).isEqualTo("new-model");

        // GIVEN 2: a blank key field and nothing else submitted — not one byte moves (the store would
        // have read that blank as removal, which is exactly why it is never called with one)
        byte[] storedBytes = Files.readAllBytes(config());
        service().update(new SettingsUpdate(null, null, null, "   ", null, null, null));
        assertThat(Files.readAllBytes(config())).containsExactly(storedBytes);
        assertThat(new FileLlmConfigStore(config()).get(LlmConfigKeys.API_KEY, null)).isEqualTo(WRITTEN_KEY);

        // submitting the value that is already stored is a no-op on disk as well
        service().update(new SettingsUpdate(null, "new-model", null, null, null, null, null));
        assertThat(Files.readAllBytes(config())).containsExactly(storedBytes);
        assertThat(differingPaths(before, treeBytes())).containsExactly(config());
    }

    @Test
    void updateCannotPinTheConfigDirOrTheDiskTools() {
        // every field the write model can carry, including the two keys the factory pins for itself
        service().update(new SettingsUpdate("OLLAMA", "some-model", "https://example", "sk-some-key",
                4000, 60L, false));

        FileLlmConfigStore stored = new FileLlmConfigStore(config());
        assertThat(stored.get(LlmConfigKeys.MODEL, null)).isEqualTo("some-model");
        assertThat(stored.get(LlmConfigKeys.CONFIG_DIRECTORY, null)).isNull();
        assertThat(stored.get(LlmConfigKeys.DISK_TOOLS_ENABLED, null)).isNull();

        // and both stay what the factory decided, not what a caller asked for
        assertThat(service().load().getConfigDir()).isEqualTo(home.toAbsolutePath().normalize());
        assertThat(service().load().isDiskToolsEnabled()).isTrue();
    }

    @Test
    void nonPositiveTokenWindowOrTimeoutIsRejectedAndNothingIsWritten() throws IOException {
        writeConfig("%s=old-model%n".formatted(LlmConfigKeys.MODEL));
        byte[] untouched = Files.readAllBytes(config());

        assertThatThrownBy(() -> service().update(new SettingsUpdate(null, null, null, null, 0, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tokenWindow");
        assertThatThrownBy(() -> service().update(new SettingsUpdate(null, null, null, null, null, -1L, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeoutSeconds");

        // rejected before the first write — and the message carries no submitted value
        assertThat(Files.readAllBytes(config())).containsExactly(untouched);
    }

    @Test
    void anUnknownProviderIsRejectedWithoutTouchingTheFile() throws IOException {
        writeConfig("%s=OLLAMA%n".formatted(LlmConfigKeys.PROVIDER_TYPE));
        byte[] untouched = Files.readAllBytes(config());

        // AiProvider.parse would have answered 200 and stored OLLAMA — garbage accepted, typo invisible
        assertThatThrownBy(() -> service().update(new SettingsUpdate("NOPE", null, null, WRITTEN_KEY,
                null, null, null)))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("provider")
                        .hasMessageNotContaining(WRITTEN_KEY)
                        .hasMessageNotContaining("NOPE");

        assertThat(Files.readAllBytes(config())).containsExactly(untouched);
        assertThat(new FileLlmConfigStore(config()).get(LlmConfigKeys.API_KEY, null)).isNull();
    }

    /**
     * The whole-tree assertion of UC-WP-24 ("config.properties is the one file that changed") is only
     * as strong as this helper: a save that <em>deletes</em> something must count as a change.
     */
    @Test
    void theWholeTreeComparisonAlsoFlagsADisappearingFile() throws IOException {
        writeConfig("%s=old-model%n".formatted(LlmConfigKeys.MODEL));
        Path doomed = home.resolve("doomed.txt");
        Files.writeString(doomed, "hand-made");
        Map<Path, byte[]> before = treeBytes();

        Files.delete(doomed);

        assertThat(differingPaths(before, treeBytes())).containsExactly(doomed);
    }


    /** The four names the environment may supply (R-WP-9) — everything else is file-only. */
    private static MockEnvironment environment() {
        return new MockEnvironment()
                .withProperty(EnvFallbackLlmConfigStore.ENV_NAMES.get(LlmConfigKeys.PROVIDER_TYPE), "MISTRAL")
                .withProperty(EnvFallbackLlmConfigStore.ENV_NAMES.get(LlmConfigKeys.MODEL), "env-model")
                .withProperty(EnvFallbackLlmConfigStore.ENV_NAMES.get(LlmConfigKeys.URL), "https://env.example")
                .withProperty(EnvFallbackLlmConfigStore.ENV_NAMES.get(LlmConfigKeys.API_KEY), "sk-env-secret");
    }

    private FactoryConfigService service() {
        return service(environment());
    }

    private FactoryConfigService service(MockEnvironment environment) {
        return new FactoryConfigService(FactoryHome.of(home), environment);
    }

    private void writeConfig(String content) {
        try {
            Files.writeString(home.resolve(FactoryHome.CONFIG_FILE), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void deleteConfig() {
        try {
            Files.deleteIfExists(home.resolve(FactoryHome.CONFIG_FILE));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path config() {
        return home.resolve(FactoryHome.CONFIG_FILE);
    }

    /** Every file below the home with its bytes — so a stray temp file or a second target cannot hide. */
    private Map<Path, byte[]> treeBytes() throws IOException {
        Map<Path, byte[]> tree = new LinkedHashMap<>();
        try (var files = Files.walk(home)) {
            files.filter(Files::isRegularFile).sorted().forEach(file -> tree.put(file, read(file)));
        }
        return tree;
    }

    /**
     * Every path that is not the same afterwards — added, changed <b>or gone</b>. The disappearance
     * half is named out loud rather than left to {@code Arrays.equals(content, null)} happening to be
     * false, so "config.properties was the only file touched" cannot quietly stop meaning that.
     */
    private static List<Path> differingPaths(Map<Path, byte[]> before, Map<Path, byte[]> after) {
        List<Path> differing = new ArrayList<>(after.keySet());
        differing.removeAll(before.keySet());
        before.forEach((file, content) -> {
            if (!after.containsKey(file) || !Arrays.equals(content, after.get(file))) {
                differing.add(file);
            }
        });
        return differing;
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static java.util.List<String> componentNamesOf(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    /** Answers from its own map, blanks included — the {@code FileLlmConfigStore} blank handling is not under test. */
    private record BlankAnsweringStore(Map<String, String> values) implements LlmConfigStore {

        @Override
        public String get(String key, String defaultValue) {
            return values.getOrDefault(key, defaultValue);
        }

        @Override
        public void put(String key, String value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove(String key) {
            throw new UnsupportedOperationException();
        }
    }
}
