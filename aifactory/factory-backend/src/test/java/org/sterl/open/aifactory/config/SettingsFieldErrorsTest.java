package org.sterl.open.aifactory.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

import org.sterl.llmpeon.ai.FileLlmConfigStore;
import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.open.aifactory.FactorySpringTest;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * A refused field, named on the wire (R-WP-55, UC-WP-92): every rule of a save lives on the request's own
 * shape, so what is measured here is the answer the page gets — the field, the server's own sentence for
 * it, and a config file that did not change.
 *
 * <p>The sentences are pinned as literals on purpose: the client shows this very text, so a reworded hint
 * has to break a test somewhere rather than drift silently. The <em>order</em> of the violations is the
 * validator's business and never asserted — only that each wrong field appears exactly once.</p>
 *
 * <p>Runs on the shared home of {@link FactorySpringTest}, empty per method.</p>
 */
class SettingsFieldErrorsTest extends FactorySpringTest {

    private static final String KEY = "sk-file-secret-0123456789";
    private static final String WRITTEN_KEY = "sk-written-secret-2468013579";
    private static final String HEADER_SECRET = "Authorization=Bearer sk-header-secret-0123456789";

    /** Dead on purpose: reaching it would mean the name was accepted and a connection was attempted. */
    private static final String DEAD_URL = "http://127.0.0.1:1/models";

    @LocalServerPort
    int port;

    private RestTemplate http;

    @BeforeEach
    void theStatusIsTheAssertion() {
        this.http = new RestTemplate();
        // a client that throws on 4xx would hide exactly the answer this class is about
        this.http.setErrorHandler(new NoOpResponseErrorHandler());
    }

    @Test
    // UC-WP-92
    void aRejectedFieldNamesItselfInTheAnswer() throws IOException {
        writeConfig("%s=OLLAMA%n%s=4096%n".formatted(LlmConfigKeys.PROVIDER_TYPE, LlmConfigKeys.MAX_TOKENS));
        byte[] untouched = Files.readAllBytes(config());

        ResponseEntity<String> rejected = put("{\"maxTokens\":\"abc\"}");

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String body = rejected.getBody();
        assertThat(fields(body)).containsExactly("maxTokens");
        assertThat(field(body, "$.fieldErrors[0].message"))
                .isEqualTo("maxTokens must be a whole number of tokens, or 0 for the provider default");
        // UC-WP-92 GIVEN 3: the reason of the field is not buried in the message — the message IS that
        // reason, so a client that only reads `message` still says the right thing
        assertThat(field(body, "$.message")).isEqualTo(field(body, "$.fieldErrors[0].message"));
        assertThat(Files.readAllBytes(config())).containsExactly(untouched);
    }

    @Test
    // UC-WP-92
    void everyWrongFieldIsNamedOnceInOneAnswer() {
        ResponseEntity<String> rejected = put("""
                {"maxTokens":"abc","tokenWindow":"0","sendThinking":"vielleicht"}""");

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // GIVEN 2: all three in one answer, each exactly once — a fourth entry for a named field would
        // make the page show the same reason twice
        assertThat(fields(rejected.getBody())).containsExactlyInAnyOrder("maxTokens", "tokenWindow",
                "sendThinking");
    }

    @Test
    // UC-WP-92
    void aFieldReasonIsNotBuriedInTheMessage() {
        String body = put("{\"maxTokens\":\"abc\",\"tokenWindow\":\"0\"}").getBody();

        @SuppressWarnings("unchecked")
        List<String> reasons = (List<String>) field(body, "$.fieldErrors[*].message");
        String message = (String) field(body, "$.message");

        assertThat(message).isEqualTo(reasons.get(0));
        // one whole sentence of one field, never the two reasons joined into a line to parse
        assertThat(message).isIn("maxTokens must be a whole number of tokens, or 0 for the provider default",
                "tokenWindow must be a positive number of tokens");
    }

    /**
     * Moved from the service test with the rule it proves (plan D1): the refusal is answered by the
     * shape now, so the proof of "nothing written" belongs to the wire. Both names of the key are the
     * page's own — sending both is the request the factory refuses instead of guessing at (UC-WP-63).
     */
    @Test
    // UC-WP-63, UC-WP-92
    void aKeyIsNeverSetAndRemovedAtOnce() throws IOException {
        writeConfig("%s=%s%n".formatted(LlmConfigKeys.API_KEY, KEY));
        byte[] untouched = Files.readAllBytes(config());

        ResponseEntity<String> rejected = put("""
                {"apiKey":"%s","removeApiKey":true}""".formatted(WRITTEN_KEY));

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String body = rejected.getBody();
        assertThat(fields(body)).containsExactly("apiKey");
        assertThat(field(body, "$.message"))
                .isEqualTo("apiKey and removeApiKey are two answers — send one");
        // R-WP-16: the refusal is about the key and repeats not one character of it
        assertThat(body).doesNotContain(WRITTEN_KEY, "secret");

        assertThat(Files.readAllBytes(config())).containsExactly(untouched);
        assertThat(stored(LlmConfigKeys.API_KEY)).isEqualTo(KEY);
    }

    /**
     * Moved from the service test (plan D1). {@code AiProvider.parse} would have answered 200 and stored
     * OLLAMA — garbage accepted, typo invisible — so the name is checked against the enum (R-WP-55).
     */
    @Test
    // R-WP-55, UC-WP-92
    void anUnknownProviderIsRejectedWithoutTouchingTheFile() throws IOException {
        writeConfig("%s=OLLAMA%n".formatted(LlmConfigKeys.PROVIDER_TYPE));
        byte[] untouched = Files.readAllBytes(config());

        ResponseEntity<String> rejected = put("""
                {"provider":"NOPE","apiKey":"%s"}""".formatted(WRITTEN_KEY));

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String body = rejected.getBody();
        assertThat(fields(body)).containsExactly("provider");
        assertThat(field(body, "$.message"))
                .isEqualTo("provider must name one of the supported providers");
        assertThat(body).doesNotContain(WRITTEN_KEY, "NOPE");

        assertThat(Files.readAllBytes(config())).containsExactly(untouched);
        assertThat(stored(LlmConfigKeys.API_KEY)).isNull();
    }

    /**
     * Moved from the service test (plan D1): text on the wire means text the core can read back, so
     * garbage and a number out of the core's range are refused, and only the field is named.
     */
    @Test
    // R-WP-55, UC-WP-92
    void nonPositiveTokenWindowOrTimeoutIsRejectedAndNothingIsWritten() throws IOException {
        writeConfig("%s=old-model%n".formatted(LlmConfigKeys.MODEL));
        byte[] untouched = Files.readAllBytes(config());

        assertThat(fields(put("{\"tokenWindow\":\"0\"}").getBody())).containsExactly("tokenWindow");
        assertThat(fields(put("{\"timeoutSeconds\":\"-1\"}").getBody())).containsExactly("timeoutSeconds");

        ResponseEntity<String> garbage = put("{\"tokenWindow\":\"many\"}");
        assertThat(garbage.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fields(garbage.getBody())).containsExactly("tokenWindow");
        assertThat(garbage.getBody()).doesNotContain("many");

        ResponseEntity<String> notABoolean = put("{\"sendThinking\":\"maybe\"}");
        assertThat(notABoolean.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fields(notABoolean.getBody())).containsExactly("sendThinking");
        assertThat(notABoolean.getBody()).doesNotContain("maybe");

        // refused before the first write — and no other file appeared either
        assertThat(Files.readAllBytes(config())).containsExactly(untouched);
    }

    /**
     * Moved from the service test (plan D1): 0 is legal here ("the provider decides"), everything the
     * core cannot read back is not — and a refusal changes no byte of the stored number.
     */
    @Test
    // UC-WP-87
    void aNonNumericMaxTokensIsRefusedAndNamesTheField() throws IOException {
        writeConfig("%s=OLLAMA%n%s=4096%n".formatted(LlmConfigKeys.PROVIDER_TYPE, LlmConfigKeys.MAX_TOKENS));
        List<String> filesBefore = homeListing();
        byte[] untouched = Files.readAllBytes(config());

        for (String rejected : List.of("abc", "-1", "4.5", "99999999999999")) {
            ResponseEntity<String> answer = put("{\"maxTokens\":\"%s\"}".formatted(rejected));

            assertThat(answer.getStatusCode()).as("value %s", rejected).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(fields(answer.getBody())).as("value %s", rejected).containsExactly("maxTokens");
            assertThat(answer.getBody()).as("value %s is not repeated", rejected).doesNotContain(rejected);
        }

        assertThat(homeListing()).isEqualTo(filesBefore);
        assertThat(Files.readAllBytes(config())).containsExactly(untouched);
        assertThat(stored(LlmConfigKeys.MAX_TOKENS)).isEqualTo("4096");
    }

    /**
     * Moved from the service test (plan D1): the core turns a segment without {@code =} into a variable
     * named after the whole segment with an empty value — nobody means that. A blank segment stays legal,
     * because that one the core reads as nothing at all, which is why a trailing comma still saves.
     */
    @Test
    // UC-WP-87
    void aParamSegmentWithoutEqualsIsRefusedAndNamesTheField() throws IOException {
        writeConfig("%s=OLLAMA%n%s=a=b%n".formatted(LlmConfigKeys.PROVIDER_TYPE, LlmConfigKeys.QUERY_PARAMS));
        byte[] untouched = Files.readAllBytes(config());

        ResponseEntity<String> query = put("{\"queryParams\":\"oops\"}");
        assertThat(query.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fields(query.getBody())).containsExactly("queryParams");
        assertThat(query.getBody()).doesNotContain("oops");

        ResponseEntity<String> header = put("{\"headerParams\":\"a=b,cdef\"}");
        assertThat(header.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fields(header.getBody())).containsExactly("headerParams");
        assertThat(header.getBody()).doesNotContain("cdef");

        assertThat(Files.readAllBytes(config())).containsExactly(untouched);

        // and the tolerance is unbroken: the trailing comma is a trailing comma, not a field error
        assertThat(put("{\"queryParams\":\"a=b,\"}").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stored(LlmConfigKeys.QUERY_PARAMS)).isEqualTo("a=b,");
    }

    /**
     * Moved from the service test (plan D1). Both param fields are prefilled, so their values do belong to
     * the read model — but they are outbound request values, so a refusal may not repeat them
     * ({@code aifactory/AGENTS-DEV.md}). That the record's own text stays clean is proven where the record
     * is built ({@code FactoryConfigServiceTest}).
     */
    @Test
    // UC-WP-87
    void headerParamsNeverAppearInALogOrInAHint() throws IOException {
        writeConfig("%s=OLLAMA%n".formatted(LlmConfigKeys.PROVIDER_TYPE));
        byte[] untouched = Files.readAllBytes(config());

        ResponseEntity<String> rejected = put("{\"headerParams\":\"%s,oops\"}".formatted(HEADER_SECRET));

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fields(rejected.getBody())).containsExactly("headerParams");
        assertThat(rejected.getBody()).doesNotContain("sk-header-secret", "Bearer", "oops", HEADER_SECRET);

        assertThat(Files.readAllBytes(config())).containsExactly(untouched);
    }

    /**
     * The model lookup takes the same provider name as the save and answers it the same way (R-WP-55).
     * The url is dead on purpose: an accepted name would answer an empty list, which is what this field
     * must never be mistaken for.
     */
    @Test
    // UC-WP-92
    void aModelListConnectionNamesItsProviderField() {
        ResponseEntity<String> rejected = post("/api/settings/models", """
                {"provider":"irgendwas","url":"%s"}""".formatted(DEAD_URL));

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fields(rejected.getBody())).containsExactly("provider");
        assertThat(rejected.getBody()).doesNotContain("irgendwas");
        // listing models reads and never writes
        assertThat(config()).doesNotExist();
    }

    /**
     * R-WP-16 stays true under the constraints: an empty field is "cleared", never a wrong value. One body
     * names every field that has a rule, so a validator that refused a blank would answer 400 here.
     */
    @Test
    // UC-WP-46
    void clearingAFieldIsNeverAFieldError() {
        ResponseEntity<String> saved = put("""
                {"tokenWindow":"","timeoutSeconds":"","sendThinking":"",\
                "maxTokens":"","queryParams":"","headerParams":""}""");

        assertThat(saved.getStatusCode()).as("body: %s", saved.getBody()).isEqualTo(HttpStatus.OK);
        // three states intact under the constraints: an empty field is "cleared" (UC-WP-46), so the answer
        // names no field at all — nothing of this body was refused
        assertThat(saved.getBody()).doesNotContain("fieldErrors");
    }

    // ------------------------------------------------------------------ wire helpers

    private ResponseEntity<String> put(String json) {
        return send(HttpMethod.PUT, "/api/settings", json);
    }

    private ResponseEntity<String> post(String path, String json) {
        return send(HttpMethod.POST, path, json);
    }

    private ResponseEntity<String> send(HttpMethod method, String path, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return http.exchange("http://localhost:" + port + path, method, new HttpEntity<>(json, headers),
                String.class);
    }

    /** The fields the answer names — the whole point of UC-WP-92, so every test reads it this way. */
    @SuppressWarnings("unchecked")
    private static List<String> fields(String body) {
        return (List<String>) field(body, "$.fieldErrors[*].field");
    }

    private static Object field(String body, String path) {
        return JsonPath.read(body, path);
    }

    /** Every file below the shared home, by relative name — a stray second file cannot hide. */
    private static List<String> homeListing() throws IOException {
        try (var files = Files.walk(HOME)) {
            return files.filter(Files::isRegularFile).map(file -> HOME.relativize(file).toString())
                    .sorted().toList();
        }
    }

    /** The stored value of one key, read straight off the factory's own file. */
    private static String stored(String key) {
        return new FileLlmConfigStore(config()).get(key, null);
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
}
