package org.sterl.open.aifactory.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

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

    @TempDir
    Path home;

    /** The directory a hand-written {@code llm.configDirectory} tries to move agent state into. */
    @TempDir
    Path other;

    @Test
    // UC-WP-36
    void configDirIsAlwaysTheFactoryHome() {
        // GIVEN 1: the file names another directory by hand — the factory home wins anyway
        writeConfig("%s=%s%n".formatted(LlmConfigKeys.CONFIG_DIRECTORY, other));

        assertThat(service().load().getConfigDir()).isEqualTo(home.toAbsolutePath().normalize());
        // nothing was read from or written to the directory the file asked for
        assertThat(other).isEmptyDirectory();

        // GIVEN 2: the same key with the plugin's own directory as value (doc's example)
        writeConfig("%s=~/.peon%n".formatted(LlmConfigKeys.CONFIG_DIRECTORY));

        assertThat(service().load().getConfigDir()).isEqualTo(home.toAbsolutePath().normalize());
        assertThat(other).isEmptyDirectory();
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
