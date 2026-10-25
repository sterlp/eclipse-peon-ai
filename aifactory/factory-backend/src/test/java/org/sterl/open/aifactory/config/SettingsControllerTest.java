package org.sterl.open.aifactory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sterl.open.aifactory.config.FactoryConfigServiceTest.componentNamesOf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.JsonPath;

import org.sterl.llmpeon.ai.FileLlmConfigStore;
import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.open.aifactory.config.dto.SettingsDto;
import org.sterl.open.aifactory.config.dto.SettingsUpdateRequest;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The HTTP contract of the settings view, over real HTTP: masking has to hold in the bytes on the wire,
 * which is what a started server and the raw response body can prove and an in-process mapper cannot.
 * Every test runs against a throwaway home.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SettingsControllerTest {

    private static final String KEY = "sk-file-secret-0123456789";
    private static final String ENV_KEY = "sk-env-secret-9876543210";

    /**
     * The start values of this run. A {@link DynamicPropertySource} supplier is resolved on every
     * lookup, so a test can switch one environment value on and off without a restart — the same
     * {@code Environment} the running server reads.
     */
    private static final Map<String, String> START_VALUES = new ConcurrentHashMap<>();

    @TempDir
    static Path home;

    @LocalServerPort
    int port;

    private RestTemplate http;

    @DynamicPropertySource
    static void homeAndStartValues(DynamicPropertyRegistry registry) {
        registry.add("aifactory.home", () -> home.toString());
        EnvFallbackLlmConfigStore.ENV_NAMES.values()
                .forEach(name -> registry.add(name, () -> START_VALUES.get(name)));
    }

    @BeforeEach
    void emptyHomeWithoutStartValues() {
        START_VALUES.clear();
        deleteConfig();
        this.http = new RestTemplate();
        // the status IS the assertion — a client that throws would hide what the app answers
        this.http.setErrorHandler(new NoOpResponseErrorHandler());
    }

    @Test
    // UC-WP-23
    void settingsShowsMaskedConfig() {
        Path config = config();
        assertThat(config).doesNotExist();
        assertThat(http.getForEntity(url("/api/settings"), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(config).doesNotExist(); // the view opened once, and nothing was written

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

        ResponseEntity<String> response = http.getForEntity(url("/api/settings"), String.class);
        String body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().includes(MediaType.APPLICATION_JSON)).isTrue();

        assertThat(field(body, "$.provider")).isEqualTo("OPEN_AI");
        assertThat(field(body, "$.model")).isEqualTo("gpt-demo");
        assertThat(field(body, "$.url")).isEqualTo("https://api.example.com/v1");
        assertThat(field(body, "$.tokenWindow")).isEqualTo(64000);
        assertThat(field(body, "$.timeoutSeconds")).isEqualTo(45);
        assertThat(field(body, "$.sendThinking")).isEqualTo(false);
        assertThat(field(body, "$.keySet")).isEqualTo(true);
        assertThat(field(body, "$.keySource")).isEqualTo("FILE");

        // the wire itself, not a parsed copy: no part of the key, not even its shape
        assertThat(body).doesNotContain(KEY, KEY.substring(0, 6), KEY.substring(KEY.length() - 6), "sk-");

        // no field a key could sit in — added components fail whatever they are called
        assertThat(componentNamesOf(SettingsDto.class)).containsExactly("provider", "model", "url", "tokenWindow",
                "timeoutSeconds", "sendThinking", "keySet", "keySource");
    }

    @Test
    // UC-WP-38
    void configFileWinsOverEnvironment() {
        useStartValues("MISTRAL", "env-model", "https://env.example", ENV_KEY);

        // GIVEN both carry a value, differently — the file is what applies and what the view names
        writeConfig("""
                %s=OPEN_AI
                %s=file-model
                %s=https://file.example
                %s=%s
                """.formatted(LlmConfigKeys.PROVIDER_TYPE, LlmConfigKeys.MODEL, LlmConfigKeys.URL,
                        LlmConfigKeys.API_KEY, KEY));

        String fromFile = bodyOfSettings();
        assertThat(field(fromFile, "$.provider")).isEqualTo("OPEN_AI");
        assertThat(field(fromFile, "$.model")).isEqualTo("file-model");
        assertThat(field(fromFile, "$.url")).isEqualTo("https://file.example");
        assertThat(field(fromFile, "$.keySource")).isEqualTo("FILE");
        assertThat(fromFile).doesNotContain(KEY, ENV_KEY, "sk-");

        // GIVEN the file carries no value for them — the start value applies and is named as the source
        writeConfig("%s=%n".formatted(LlmConfigKeys.MODEL));

        String fromEnvironment = bodyOfSettings();
        assertThat(field(fromEnvironment, "$.provider")).isEqualTo("MISTRAL");
        assertThat(field(fromEnvironment, "$.model")).isEqualTo("env-model");
        assertThat(field(fromEnvironment, "$.url")).isEqualTo("https://env.example");
        assertThat(field(fromEnvironment, "$.keySet")).isEqualTo(true);
        assertThat(field(fromEnvironment, "$.keySource")).isEqualTo("ENV");
        assertThat(fromEnvironment).doesNotContain(ENV_KEY, "sk-");

        // GIVEN neither carries anything — the documented core defaults, and the key is not set
        deleteConfig();
        START_VALUES.clear();

        String fromDefaults = bodyOfSettings();
        assertThat(field(fromDefaults, "$.provider")).isEqualTo("OLLAMA");
        assertThat(field(fromDefaults, "$.tokenWindow")).isEqualTo(80000);
        assertThat(field(fromDefaults, "$.timeoutSeconds")).isEqualTo(180);
        assertThat(field(fromDefaults, "$.sendThinking")).isEqualTo(true);
        assertThat(field(fromDefaults, "$.keySet")).isEqualTo(false);
        assertThat(field(fromDefaults, "$.keySource")).isEqualTo("NONE");
        assertThat(config()).doesNotExist();
    }

    /**
     * UC-WP-24 GIVEN 1 + GIVEN 2 over real HTTP. GIVEN 3 ("der nächste Turn nutzt den neuen Wert") is
     * <b>not</b> covered — no runtime, no turn in the MVP yet; the UC is partially covered and finished
     * in Increment 3.
     */
    @Test
    // UC-WP-24
    void settingsOverrideNeverWritesSharedStore() throws IOException {
        writeConfig("""
                %s=OLLAMA
                %s=old-model
                %s=40000
                %s=agent-model
                custom.key=hand-made
                """.formatted(LlmConfigKeys.PROVIDER_TYPE, LlmConfigKeys.MODEL, LlmConfigKeys.TOKEN_WINDOW,
                        LlmConfigKeys.agentKey("plan", LlmConfigKeys.AGENT_FIELD_MODEL)));
        List<String> filesBefore = fileNames();

        ResponseEntity<String> saved = put("""
                {"model":"put-model","url":"https://put.example","apiKey":"%s"}
                """.formatted(KEY).trim());
        String body = saved.getBody();

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(field(body, "$.model")).isEqualTo("put-model");
        assertThat(field(body, "$.url")).isEqualTo("https://put.example");
        assertThat(field(body, "$.keySet")).isEqualTo(true);
        assertThat(field(body, "$.keySource")).isEqualTo("FILE");
        // the answer is the stored state and not the submitted body: a field that was never sent still
        // reads as stored, which an echo of the request cannot produce
        assertThat(field(body, "$.provider")).isEqualTo("OLLAMA");
        assertThat(field(body, "$.tokenWindow")).isEqualTo(40000);
        // a save answers masked, exactly like a read: no field, no character
        assertThat(body).doesNotContain(KEY, KEY.substring(0, 6), KEY.substring(KEY.length() - 6), "sk-");
        assertThat(componentNamesOf(SettingsDto.class)).containsExactly("provider", "model", "url", "tokenWindow",
                "timeoutSeconds", "sendThinking", "keySet", "keySource");

        FileLlmConfigStore stored = new FileLlmConfigStore(config());
        assertThat(stored.get(LlmConfigKeys.MODEL, null)).isEqualTo("put-model");
        assertThat(stored.get(LlmConfigKeys.API_KEY, null)).isEqualTo(KEY); // the store holds it, the wire never does
        assertThat(stored.get(LlmConfigKeys.PROVIDER_TYPE, null)).isEqualTo("OLLAMA");
        assertThat(stored.get(LlmConfigKeys.TOKEN_WINDOW, null)).isEqualTo("40000");
        assertThat(stored.get(LlmConfigKeys.agentKey("plan", LlmConfigKeys.AGENT_FIELD_MODEL), null))
                .isEqualTo("agent-model");
        assertThat(stored.get("custom.key", null)).isEqualTo("hand-made");

        // the file that now holds a key is owner-only — also when the factory wrote it (R-WP-16)
        Assumptions.assumeTrue(config().getFileSystem().supportedFileAttributeViews().contains("posix"),
                "no posix view on this file system — the mode is the operating system's promise there");
        assertThat(Files.getPosixFilePermissions(config())).isEqualTo(
                EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));

        // GIVEN 2: an empty field is no value — the file keeps every byte it had
        byte[] unchanged = Files.readAllBytes(config());
        ResponseEntity<String> emptied = put("{\"model\":\"\"}");
        assertThat(emptied.getStatusCode()).isEqualTo(HttpStatus.OK);
        // an empty field did not clear the stored value — the answer re-read it instead of echoing
        assertThat(field(emptied.getBody(), "$.model")).isEqualTo("put-model");
        assertThat(Files.readAllBytes(config())).containsExactly(unchanged);
        assertThat(new FileLlmConfigStore(config()).get(LlmConfigKeys.API_KEY, null)).isEqualTo(KEY);

        // a wire field the request model does not know stays unreachable, whatever it is called
        assertThat(put("""
                {"model":"other-model","configDirectory":"/tmp/aifactory-never","diskToolsEnabled":false}
                """).getStatusCode()).isEqualTo(HttpStatus.OK);
        FileLlmConfigStore pinned = new FileLlmConfigStore(config());
        assertThat(pinned.get(LlmConfigKeys.MODEL, null)).isEqualTo("other-model");
        assertThat(pinned.get(LlmConfigKeys.CONFIG_DIRECTORY, null)).isNull();
        assertThat(pinned.get(LlmConfigKeys.DISK_TOOLS_ENABLED, null)).isNull();

        // the request model has no component for the two pinned values either
        assertThat(componentNamesOf(SettingsUpdateRequest.class)).containsExactly("provider", "model", "url",
                "apiKey", "tokenWindow", "timeoutSeconds", "sendThinking");

        // and the home still holds exactly the one file — no temp file survived the atomic move
        assertThat(fileNames()).isEqualTo(filesBefore);
    }

    @Test
    void unknownProviderIsRejectedAndTheKeyNeverAppears() throws IOException {
        writeConfig("%s=OLLAMA%n".formatted(LlmConfigKeys.PROVIDER_TYPE));
        byte[] untouched = Files.readAllBytes(config());

        ResponseEntity<String> rejected = put("{\"provider\":\"NOPE\",\"apiKey\":\"sk-secret-value\"}");

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String body = rejected.getBody();
        assertThat(field(body, "$.message").toString()).contains("provider");
        // UC-WP-23's "auch nicht in einem Fehlerfall": the whole body, not only a field, stays clean
        assertThat(body).doesNotContain("sk-secret-value", "secret", "NOPE");

        // rejected before the first write — neither the provider nor the key reached the file
        assertThat(Files.readAllBytes(config())).containsExactly(untouched);
        assertThat(new FileLlmConfigStore(config()).get(LlmConfigKeys.API_KEY, null)).isNull();
        assertThat(new FileLlmConfigStore(config()).get(LlmConfigKeys.PROVIDER_TYPE, null)).isEqualTo("OLLAMA");
    }

    /** The raw wire is the point: the body is sent exactly as written here, unknown fields included. */
    private ResponseEntity<String> put(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url("/api/settings"), HttpMethod.PUT, new HttpEntity<>(json, headers), String.class);
    }

    private static List<String> fileNames() throws IOException {
        try (var files = Files.list(home)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }


    private String bodyOfSettings() {
        ResponseEntity<String> response = http.getForEntity(url("/api/settings"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    /** Every field assertion reads the raw response body — the same text the key assertion inspects. */
    private static Object field(String body, String path) {
        return JsonPath.read(body, path);
    }

    private void useStartValues(String provider, String model, String url, String key) {
        START_VALUES.put(EnvFallbackLlmConfigStore.ENV_NAMES.get(LlmConfigKeys.PROVIDER_TYPE), provider);
        START_VALUES.put(EnvFallbackLlmConfigStore.ENV_NAMES.get(LlmConfigKeys.MODEL), model);
        START_VALUES.put(EnvFallbackLlmConfigStore.ENV_NAMES.get(LlmConfigKeys.URL), url);
        START_VALUES.put(EnvFallbackLlmConfigStore.ENV_NAMES.get(LlmConfigKeys.API_KEY), key);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static Path config() {
        return home.resolve(FactoryHome.CONFIG_FILE);
    }

    private static void writeConfig(String content) {
        try {
            Files.writeString(config(), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteConfig() {
        try {
            Files.deleteIfExists(config());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
