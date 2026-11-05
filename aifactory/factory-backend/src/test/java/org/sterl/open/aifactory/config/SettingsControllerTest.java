package org.sterl.open.aifactory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sterl.open.aifactory.config.FactoryConfigServiceTest.componentNamesOf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.JsonPath;

import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.FileLlmConfigStore;
import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.provider.LlmProviders;
import org.sterl.open.aifactory.FactorySpringTest;
import org.sterl.open.aifactory.config.dto.ProviderDefaultsDto;
import org.sterl.open.aifactory.config.dto.SettingsDto;
import org.sterl.open.aifactory.config.dto.SettingsUpdateRequest;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The HTTP contract of the settings view, over real HTTP: masking has to hold in the bytes on the wire,
 * which is what a started server and the raw response body can prove and an in-process mapper cannot.
 * Every test runs against the shared throwaway home of {@link FactorySpringTest}, empty per method.
 */
class SettingsControllerTest extends FactorySpringTest {

    private static final String KEY = "sk-file-secret-0123456789";
    private static final String ENV_KEY = "sk-env-secret-9876543210";

    @LocalServerPort
    int port;

    private RestTemplate http;

    @BeforeEach
    void withoutStartValues() {
        clearStartValues();
        this.http = new RestTemplate();
        // the status IS the assertion — a client that throws would hide what the app answers
        this.http.setErrorHandler(new NoOpResponseErrorHandler());
    }

    /** System properties reach the whole JVM: a start value of this class never outlives its own test. */
    @AfterEach
    void forgetTheStartValues() {
        clearStartValues();
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
        clearStartValues();

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

        // GIVEN 2: an empty key field is no value — the file keeps every byte it had. The key is the one
        // field for which the page still cannot clear anything by leaving it empty (R-WP-16).
        byte[] unchanged = Files.readAllBytes(config());
        ResponseEntity<String> emptied = put("{\"apiKey\":\"\"}");
        assertThat(emptied.getStatusCode()).isEqualTo(HttpStatus.OK);
        // an empty field did not clear the stored value — the answer re-read it instead of echoing
        assertThat(field(emptied.getBody(), "$.model")).isEqualTo("put-model");
        assertThat(Files.readAllBytes(config())).containsExactly(unchanged);
        assertThat(new FileLlmConfigStore(config()).get(LlmConfigKeys.API_KEY, null)).isEqualTo(KEY);

        // a cleared visible field is the opposite: an empty string is the value "delete" (UC-WP-46), so
        // the same request shape on the model field does remove it
        ResponseEntity<String> cleared = put("{\"model\":\"\"}");
        assertThat(field(cleared.getBody(), "$.model")).isNull();
        assertThat(new FileLlmConfigStore(config()).get(LlmConfigKeys.MODEL, null)).isNull();

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
                "apiKey", "tokenWindow", "timeoutSeconds", "sendThinking", "removeApiKey");

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

    /**
     * UC-WP-62 over HTTP: the list the page follows is the core's own, so the page needs no second list
     * and no invented model default (D-10, Q2). Reading it never writes the config file.
     */
    @Test
    // UC-WP-62
    @SuppressWarnings("unchecked")
    void providerDefaultsAnswerAsTheCoreKnowsThem() {
        assertThat(config()).doesNotExist();

        ResponseEntity<String> response = http.getForEntity(url("/api/settings/providers"), String.class);
        String body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> names = (List<String>) field(body, "$[*].name");
        assertThat(names).containsExactly(
                Arrays.stream(AiProvider.values()).map(Enum::name).toArray(String[]::new));

        // the URL is the core's own value — the factory maintains no list of its own
        assertThat(field(body, "$[" + names.indexOf("OLLAMA") + "].defaultUrl"))
                .isEqualTo(LlmProviders.of(AiProvider.OLLAMA).defaultBaseUrl());
        // a provider that requires a URL gets none: the page leaves the field empty instead of guessing
        assertThat(field(body, "$[" + names.indexOf("OPEN_AI") + "].defaultUrl")).isNull();
        // the providers without a think input are answered as such — that is what disables the field
        assertThat(field(body, "$[" + names.indexOf("GOOGLE_GEMINI") + "].thinkSupported")).isEqualTo(false);
        assertThat(field(body, "$[" + names.indexOf("MISTRAL") + "].thinkSupported")).isEqualTo(false);
        assertThat(field(body, "$[" + names.indexOf("OLLAMA") + "].thinkSupported")).isEqualTo(true);

        // a model default would be an invention, so there is no field that could hold one
        assertThat(componentNamesOf(ProviderDefaultsDto.class)).containsExactly("name", "defaultUrl",
                "thinkSupported", "thinkForm", "thinkValues");
        assertThat(config()).doesNotExist();
    }

    /**
     * UC-WP-67 over HTTP: the agent slots build their think field from the base provider, so the answer
     * carries the core's own form and, for a fixed list, the list itself. Nothing here is the factory's
     * guess — every value is read off {@code LlmProvider.thinkSupport()}.
     */
    @Test
    // UC-WP-67
    @SuppressWarnings("unchecked")
    void providerDefaultsCarryTheThinkForm() {
        String body = http.getForEntity(url("/api/settings/providers"), String.class).getBody();
        List<String> names = (List<String>) field(body, "$[*].name");

        // a fixed list arrives as a list, in the core's own order — the page must not sort or replace it
        assertThat(field(body, "$[" + names.indexOf("ANTHROPIC") + "].thinkForm")).isEqualTo("VALUES");
        assertThat((List<String>) field(body, "$[" + names.indexOf("ANTHROPIC") + "].thinkValues"))
                .containsExactly("adaptive", "enabled");
        // a toggle names no list; the page offers unset/true/false itself
        assertThat(field(body, "$[" + names.indexOf("OLLAMA") + "].thinkForm")).isEqualTo("TOGGLE");
        assertThat((List<String>) field(body, "$[" + names.indexOf("OLLAMA") + "].thinkValues")).isEmpty();
        // free text is a form of its own, not a missing list
        assertThat(field(body, "$[" + names.indexOf("LM_STUDIO") + "].thinkForm")).isEqualTo("FREE");
        // no input at all is answered as such — the page then shows no control, only the hint
        assertThat(field(body, "$[" + names.indexOf("GOOGLE_GEMINI") + "].thinkForm")).isEqualTo("NONE");
        assertThat(field(body, "$[" + names.indexOf("MISTRAL") + "].thinkForm")).isEqualTo("NONE");
        // thinkSupported keeps its old meaning: it disables the field, the form says how it looks
        assertThat(field(body, "$[" + names.indexOf("MISTRAL") + "].thinkSupported")).isEqualTo(false);
        assertThat(field(body, "$[" + names.indexOf("OLLAMA") + "].thinkSupported")).isEqualTo(true);
        // reading the form stays a read
        assertThat(config()).doesNotExist();
    }

    /**
     * UC-WP-63 over HTTP: "Remove key" empties the file and the answer says so. A key the environment
     * still supplies stays effective and is named as ENV — removing it is not the page's to do.
     */
    @Test
    // UC-WP-63
    void removeKeyDeletesItAndRefreshesTheStatus() throws IOException {
        writeConfig("%s=OPEN_AI%n%s=gpt-demo%n%s=%s%n".formatted(
                LlmConfigKeys.PROVIDER_TYPE, LlmConfigKeys.MODEL, LlmConfigKeys.API_KEY, KEY));

        ResponseEntity<String> saved = put("{\"removeApiKey\":true}");
        String body = saved.getBody();

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(field(body, "$.keySet")).isEqualTo(false);
        assertThat(field(body, "$.keySource")).isEqualTo("NONE");
        // the answer is the stored state: what the request never named is still there
        assertThat(field(body, "$.model")).isEqualTo("gpt-demo");
        assertThat(new FileLlmConfigStore(config()).get(LlmConfigKeys.API_KEY, null)).isNull();

        // GIVEN the environment supplies a key — the file is empty of it, the source says ENV, and no
        // character of the key leaves the server (R-WP-16)
        useStartValues("OPEN_AI", "env-model", "https://env.example", ENV_KEY);
        writeConfig("%s=%s%n".formatted(LlmConfigKeys.API_KEY, KEY));

        String withStartValue = put("{\"removeApiKey\":true}").getBody();
        assertThat(field(withStartValue, "$.keySource")).isEqualTo("ENV");
        assertThat(withStartValue).doesNotContain(ENV_KEY, "sk-");
        assertThat(new FileLlmConfigStore(config()).get(LlmConfigKeys.API_KEY, null)).isNull();

        // and asking again for a key that is already gone changes not one byte
        byte[] untouched = Files.readAllBytes(config());
        put("{\"removeApiKey\":true}");
        assertThat(Files.readAllBytes(config())).containsExactly(untouched);
    }

    /**
     * UC-WP-46 GIVEN 1 over HTTP: an empty string is the value "delete" — the file loses the field and
     * the answer shows what applies without it, while a field named with a value survives the same body.
     */
    @Test
    // UC-WP-46
    void aClearedFieldIsAnsweredWithWhatAppliesWithoutIt() {
        writeConfig("""
                %s=OLLAMA
                %s=gpt-demo
                %s=64000
                %s=45
                """.formatted(LlmConfigKeys.PROVIDER_TYPE, LlmConfigKeys.MODEL, LlmConfigKeys.TOKEN_WINDOW,
                        LlmConfigKeys.TIMEOUT));

        ResponseEntity<String> saved = put("""
                {"provider":"OLLAMA","model":"","url":"","tokenWindow":"64000","timeoutSeconds":"45","sendThinking":""}
                """.trim());
        String body = saved.getBody();

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        FileLlmConfigStore stored = new FileLlmConfigStore(config());
        assertThat(stored.get(LlmConfigKeys.MODEL, null)).isNull();
        assertThat(stored.get(LlmConfigKeys.URL, null)).isNull();
        assertThat(stored.get(LlmConfigKeys.SEND_THINKING_ENABLED, null)).isNull();
        assertThat(stored.get(LlmConfigKeys.TOKEN_WINDOW, null)).isEqualTo("64000");
        assertThat(stored.get(LlmConfigKeys.TIMEOUT, null)).isEqualTo("45");

        assertThat(field(body, "$.model")).isNull();
        assertThat(field(body, "$.tokenWindow")).isEqualTo(64000);
        // the think line is gone from the file, so the core default applies again — R-WP-16 "was ohne ihn gilt"
        assertThat(field(body, "$.sendThinking")).isEqualTo(true);
    }

    /** The raw wire is the point: the body is sent exactly as written here, unknown fields included. */
    private ResponseEntity<String> put(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url("/api/settings"), HttpMethod.PUT, new HttpEntity<>(json, headers), String.class);
    }

    private static List<String> fileNames() throws IOException {
        try (var files = Files.list(HOME)) {
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

    /**
     * The start values of this run, as <em>system properties</em> (plan D-6): {@code Environment} answers
     * from {@code System.getProperties()} on every lookup, so a test switches one value on and off without
     * a restart — exactly what the {@code DynamicPropertyRegistry} used to do here, only without a dynamic
     * property of its own, which is what used to give this class a context of its own.
     */
    private void useStartValues(String provider, String model, String url, String key) {
        System.setProperty(EnvFallbackLlmConfigStore.ENV_NAMES.get(LlmConfigKeys.PROVIDER_TYPE), provider);
        System.setProperty(EnvFallbackLlmConfigStore.ENV_NAMES.get(LlmConfigKeys.MODEL), model);
        System.setProperty(EnvFallbackLlmConfigStore.ENV_NAMES.get(LlmConfigKeys.URL), url);
        System.setProperty(EnvFallbackLlmConfigStore.ENV_NAMES.get(LlmConfigKeys.API_KEY), key);
    }

    private static void clearStartValues() {
        EnvFallbackLlmConfigStore.ENV_NAMES.values().forEach(System::clearProperty);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static Path config() {
        return HOME.resolve(FactoryHome.CONFIG_FILE);
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
