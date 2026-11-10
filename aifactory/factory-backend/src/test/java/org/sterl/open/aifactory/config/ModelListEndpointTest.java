package org.sterl.open.aifactory.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
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

import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.FileLlmConfigStore;
import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.ai.ModelListCache;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.open.aifactory.FactorySpringTest;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The model list over HTTP (R-WP-50, UC-WP-86): the models of the connection the page's draft
 * describes, answered by the core's own provider call. A POST, because the endpoint and the slot of a
 * connection belong in a body and not in a query string and an access log; the key belongs to neither
 * — it stays on the server side and no answer carries it.
 *
 * <p>The provider is the core's stub server, which answers {@code GET /models} in the OpenAI shape and
 * is only ever reached through {@code LlmProvider} — the factory implements no listing of its own.</p>
 *
 * <p>Every test clears the process-wide model cache first: an entry of an earlier test would answer
 * before the stub is asked, and the test would be green without a single request. Where a test has to
 * tell "answered from the cache" from "asked the server", it changes the stub's list in between — a
 * cached answer cannot produce the new one.</p>
 */
class ModelListEndpointTest extends FactorySpringTest {

    private static final String KEY = "sk-file-secret-0123456789";
    /** A port nothing listens on: a connection that cannot answer is a list of nothing, not an error. */
    private static final String DEAD_URL = "http://127.0.0.1:1/v1";

    @LocalServerPort
    int port;

    private RestTemplate http;
    private MockLlmServer models;

    @BeforeEach
    void startTheModelServer() {
        ModelListCache.instance().clear();
        this.http = new RestTemplate();
        // the status IS the assertion — a client that throws would hide what the app answers
        this.http.setErrorHandler(new NoOpResponseErrorHandler());
        this.models = new MockLlmServer(0);
        this.models.start();
    }

    @AfterEach
    void stopTheModelServer() {
        models.stop();
        ModelListCache.instance().clear();
    }

    /**
     * R-WP-50: the list is the server's, in the core's own order, for the connection of the draft — and
     * for the stored one when the page sends nothing of a draft, because then the key is the one value
     * the page never had to send.
     */
    @Test
    // UC-WP-86
    void modelListAnswersTheModelsOfTheDraftConnection() {
        models.setModelIds(List.of("llama-mock", "gpt-x")); // unsorted on purpose — the core sorts, the factory does not

        ResponseEntity<String> listed = post("""
                {"provider":"OPEN_AI","url":"%s"}""".formatted(models.getUrl()));

        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listed.getBody()).isEqualTo("[\"gpt-x\",\"llama-mock\"]");

        // GIVEN the file names the connection and the key, and the page sends an empty draft
        writeConfig("""
                %s=OPEN_AI
                %s=gpt-demo
                %s=%s
                %s=%s
                """.formatted(LlmConfigKeys.PROVIDER_TYPE, LlmConfigKeys.MODEL, LlmConfigKeys.URL,
                        models.getUrl(), LlmConfigKeys.API_KEY, KEY));

        assertThat(post("{}").getBody()).isEqualTo("[\"gpt-x\",\"llama-mock\"]");
    }

    /**
     * R-WP-50 for a slot: a slot without a connection of its own is asked where the base connection is,
     * and the draft URL of the slot is the connection that gets asked — here a dead one, which is an
     * empty list and no error (UC-WP-86 GIVEN 2), so the field stays a free text field.
     */
    @Test
    // UC-WP-86
    void modelListUsesTheAgentConnectionWhenAnAgentIsGiven() {
        writeConfig("""
                %s=OPEN_AI
                %s=gpt-demo
                %s=%s
                %s=%s
                """.formatted(LlmConfigKeys.PROVIDER_TYPE, LlmConfigKeys.MODEL, LlmConfigKeys.URL,
                        models.getUrl(), LlmConfigKeys.API_KEY, KEY));
        models.setModelIds(List.of("gpt-x"));

        assertThat(post("""
                {"provider":"OPEN_AI","url":"%s","agent":"%s"}""".formatted(models.getUrl(), AgentModelConfig.PO))
                        .getBody()).isEqualTo("[\"gpt-x\"]");

        ResponseEntity<String> deadSlot = post("""
                {"provider":"OPEN_AI","url":"%s","agent":"%s","agentUrl":"%s"}"""
                        .formatted(models.getUrl(), AgentModelConfig.PO, DEAD_URL));

        assertThat(deadSlot.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deadSlot.getBody()).isEqualTo("[]");
    }

    /**
     * R-WP-50: the list is fetched once and a refresh asks again. The proof is the stub's answer — a
     * cached list cannot produce the changed one.
     */
    @Test
    // UC-WP-86
    void aRefreshAsksTheServerAgain() {
        models.setModelIds(List.of("first"));
        String draft = """
                {"provider":"OPEN_AI","url":"%s"}""".formatted(models.getUrl());

        assertThat(post(draft).getBody()).isEqualTo("[\"first\"]");

        models.setModelIds(List.of("second"));

        assertThat(post(draft).getBody()).isEqualTo("[\"first\"]");
        assertThat(post("""
                {"provider":"OPEN_AI","url":"%s","refresh":true}""".formatted(models.getUrl())).getBody())
                        .isEqualTo("[\"second\"]");
    }

    /**
     * UC-WP-86 GIVEN 2: a server that knows no model and a server that answers 500 both leave the page
     * with a free text field — 200 and an empty list, no error block. Listing reads, so the home holds
     * afterwards exactly what it held before.
     */
    @Test
    // UC-WP-86
    void afailedOrEmptyListingAnswersEmptyAndWritesNothing() throws IOException {
        writeConfig("""
                %s=OPEN_AI
                %s=gpt-demo
                %s=%s
                """.formatted(LlmConfigKeys.PROVIDER_TYPE, LlmConfigKeys.MODEL, LlmConfigKeys.URL,
                        models.getUrl()));
        byte[] before = Files.readAllBytes(config());
        String draft = """
                {"provider":"OPEN_AI","url":"%s"}""".formatted(models.getUrl());

        models.setModelIds(List.of());
        assertThat(post(draft)).satisfies(response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo("[]");
        });

        models.enableModelsError();
        assertThat(post(draft)).satisfies(response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo("[]");
        });

        assertThat(Files.readAllBytes(config())).containsExactly(before);
        try (var files = Files.list(HOME)) {
            assertThat(files.map(file -> file.getFileName().toString()).sorted().toList())
                    .containsExactly(FactoryHome.CONFIG_FILE);
        }
    }

    /**
     * R-WP-16 for a field that is a secret: the key never travels in either direction. The request needs
     * no key field to reach a connection that has one, a body that offers a key reaches nothing, and the
     * answer carries no character of either.
     */
    @Test
    // UC-WP-86
    void theKeyNeverTravelsInEitherDirection() {
        writeConfig("""
                %s=OPEN_AI
                %s=gpt-demo
                %s=%s
                %s=%s
                """.formatted(LlmConfigKeys.PROVIDER_TYPE, LlmConfigKeys.MODEL, LlmConfigKeys.URL,
                        models.getUrl(), LlmConfigKeys.API_KEY, KEY));
        models.setModelIds(List.of("gpt-x"));

        ResponseEntity<String> response = post("""
                {"provider":"OPEN_AI","url":"%s","agent":"%s","apiKey":"sk-sneaky","removeApiKey":true}"""
                        .formatted(models.getUrl(), AgentModelConfig.PO));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("[\"gpt-x\"]");
        assertThat(response.getBody()).doesNotContain(KEY, "sk-", "sneaky");
        // the offer of a key changed no byte — this is a read, and its fields are no write
        assertThat(new FileLlmConfigStore(config()).get(LlmConfigKeys.API_KEY, null)).isEqualTo(KEY);
    }
    /**
     * R-WP-16 applied to the draft of a connection: the URL field <em>is</em> the draft, so an empty one is
     * <em>unset</em> and not "leave the stored one alone" — the list has to predict the connection that a
     * save would build, and saving an empty URL deletes it. An absent field still says nothing. A slot whose
     * own URL field is empty says something else by its own rule (R-WP-23: empty inherits), and there the
     * base connection is the honest answer.
     */
    @Test
    // UC-WP-86
    void anEmptyUrlFieldListsTheConnectionAnEmptyFieldWouldSave() {
        writeConfig("""
                %s=OPEN_AI
                %s=gpt-demo
                %s=%s
                """.formatted(LlmConfigKeys.PROVIDER_TYPE, LlmConfigKeys.MODEL, LlmConfigKeys.URL,
                        models.getUrl()));
        models.setModelIds(List.of("gpt-x"));

        // the field is absent: nothing drafted, so the stored connection is the one to list
        assertThat(post("{}").getBody()).isEqualTo("[\"gpt-x\"]");

        // the field is empty: that is unset, and this provider has no endpoint of its own — asking the
        // stored URL here would have answered with the list of the call above
        assertThat(post("{\"provider\":\"OPEN_AI\",\"url\":\"\"}").getBody()).isEqualTo("[]");

        // an empty slot URL is the slot's "I say nothing of my own": the base connection answers
        assertThat(post("""
                {"agent":"%s","agentUrl":""}""".formatted(AgentModelConfig.PO)).getBody())
                        .isEqualTo("[\"gpt-x\"]");

        // listing a draft writes nothing: the file still holds the URL the empty field did not touch
        assertThat(new FileLlmConfigStore(config()).get(LlmConfigKeys.URL, null)).isEqualTo(models.getUrl());
    }


    /**
     * The one case that is refused instead of answered with nothing: a provider name the core does not
     * offer is the page's bug, not a connection that cannot answer. Refused before the connection is
     * used — with a URL of its own the answer would have to be an empty list, so the 400 here is what
     * separates the two, and nothing was asked anywhere.
     */
    @Test
    // UC-WP-86
    void anUnknownProviderNameIsRefusedWithoutARequest() {
        models.setModelIds(List.of("gpt-x"));

        ResponseEntity<String> rejected = post("""
                {"provider":"NOPE","url":"%s"}""".formatted(DEAD_URL));

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(JsonPath.read(rejected.getBody(), "$.message").toString()).contains("provider");
        assertThat(config()).doesNotExist();
    }

    /** The raw wire is the point: the body travels exactly as written here, unknown fields included. */
    private ResponseEntity<String> post(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url("/api/settings/models"), HttpMethod.POST, new HttpEntity<>(json, headers),
                String.class);
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
}
