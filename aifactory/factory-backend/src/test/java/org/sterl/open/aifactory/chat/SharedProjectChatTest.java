package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sterl.open.aifactory.chat.ChatHistoryWaits.awaitAnswer;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
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

import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.project.FakeGitConfig;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * One project has one Jon and one conversation, and everyone who opens it works in that one (R-WP-15).
 * The two clients here are two {@link RestTemplate}s with nothing in common — no cookie, no session, no
 * account — so what they read back can only be the same conversation of the same runtime, never a private
 * copy. Since Inc 6 each one says who it is (R-WP-25), and that stays a <b>label</b>: two names, one
 * conversation, one Jon (UC-WP-48 GIVEN 1).
 *
 * <p>The model double is per test: a turn is fire-and-forget, so a failing test leaves a turn running on
 * the shared executor, and that orphan must point at its own stopped server instead of draining the next
 * test's scripted answers (the lesson of {@code ChatQueueTest}).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakeGitConfig.class)
@Timeout(90)
class SharedProjectChatTest {

    private static final String QUESTION_A = "Frage von A";
    private static final String ANSWER_A = "Jons Antwort auf A";
    private static final String QUESTION_B = "Frage von B";
    private static final String ANSWER_B = "Jons Antwort auf B";
    /** The two people of UC-WP-48 GIVEN 1 — a label each client sends, not an account (R-WP-17). */
    private static final String PERSON_A = "Anna";
    private static final String PERSON_B = "Paul";

    @TempDir
    static Path home;

    private MockLlmServer model;

    @LocalServerPort
    private int port;

    @Autowired
    private ProjectRuntimeRegistry runtimes;

    /** Two callers, no shared state: what they cannot have is a conversation of their own. */
    private RestTemplate userA;
    private RestTemplate userB;

    @BeforeEach
    void startModelAndClients() {
        this.model = new MockLlmServer(0);
        this.model.start();
        this.userA = newClient();
        this.userB = newClient();
    }

    @AfterEach
    void stopModel() {
        if (model != null) {
            model.stop();
        }
    }

    @DynamicPropertySource
    static void isolateHome(DynamicPropertyRegistry registry) {
        registry.add("aifactory.home", home::toString);
    }

    @Test
    // UC-WP-21, UC-WP-48
    void secondUserSeesSameProjectChat() throws Exception {
        writeFactoryConfig("inc3d-shared");
        String projectId = createProject(userA, "shared-project-chat");
        assertThat(runtimes.runtimeCount()).isEqualTo(0);

        model.queueResponse(ANSWER_A);
        assertThat(send(userA, projectId, QUESTION_A, PERSON_A).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        // B does not open a conversation of its own to wait in — it reads the one A is in
        awaitAnswer(() -> messages(userB, projectId), ANSWER_A);

        assertThat(messages(userB, projectId))
                .as("B sieht dieselben Nachrichten wie A, gleiche Rollenfolge, gleicher Text")
                .isEqualTo(messages(userA, projectId));
        ProjectRuntime shared = runtimes.runtime(projectId);
        assertThat(runtimes.runtimeCount())
                .as("zwei HTTP-Clienten, ein Jon")
                .isEqualTo(1);
        assertThat(shared.jon().getName())
                .as("und der eine Jon ist Jon")
                .isEqualTo(AiPoAgent.NAME);

        model.queueResponse(ANSWER_B);
        // idle before B sends: a still-running turn would take the input into its queue, and the history
        // would carry the core's [Queued Message] marker — a different use case, proven in UC-WP-14
        awaitUntil(() -> !shared.jon().isWorking(), 20_000, "Jons erster Turn ist zu Ende");
        assertThat(send(userB, projectId, QUESTION_B, PERSON_B).getStatusCode())
                .as("B sendet in denselben Verlauf, nicht in eine eigene Kopie")
                .isEqualTo(HttpStatus.ACCEPTED);
        awaitAnswer(() -> messages(userA, projectId), ANSWER_B);

        assertThat(messages(userA, projectId))
                .as("A sieht Bs Eingabe und Jons Antwort darauf in seinem eigenen Verlauf — und an Bs"
                        + " Zeile steht ihr Name, weil der Verlauf geteilt ist (UC-WP-48 GIVEN 1)")
                .containsExactly(
                        new ChatMessageDto("user", QUESTION_A, PERSON_A),
                        new ChatMessageDto("jon", ANSWER_A, AiPoAgent.NAME),
                        new ChatMessageDto("user", QUESTION_B, PERSON_B),
                        new ChatMessageDto("jon", ANSWER_B, AiPoAgent.NAME));
        assertThat(messages(userB, projectId)).isEqualTo(messages(userA, projectId));
        assertThat(runtimes.runtime(projectId))
                .as("beide Anfragen liefen auf dieselbe Runtime — dieselbe Memory, kein zweiter Jon")
                .isSameAs(shared);

        assertThat(fieldNames(ChatMessageDto.class))
                .as("'origin' ist die Herkunft der Zeile — seit Inc 6 nennt sie auch den Menschen, aber"
                        + " kein Feld wählt ein Gespräch oder einen Agenten (R-WP-4, R-WP-25)")
                .containsExactly("role", "text", "origin");
        assertThat(fieldNames(SendMessageRequest.class))
                .as("genau ein Label-Feld für den Absender, sonst nichts — die Eingabe geht weiter an Jon")
                .containsExactly("text", "user");
        assertThat(rawMessages(userB, projectId))
                .as("der Verlauf nennt den Menschen im bestehenden Herkunftsfeld — die Antwort erfindet"
                        + " kein eigenes Nutzerfeld")
                .doesNotContain("\"user\":")
                .doesNotContain("\"name\":");
    }

    // ------------------------------------------------------------------ edges

    private static List<String> fieldNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    private RestTemplate newClient() {
        RestTemplate client = new RestTemplate();
        // the status IS the assertion — a throwing client would hide what the app answers
        client.setErrorHandler(new NoOpResponseErrorHandler());
        return client;
    }

    private String messagesPath(String projectId) {
        return "/api/projects/" + projectId + "/messages";
    }

    /**
     * One of the two people sends — by name, because since Inc 6 a send carries its sender's label
     * (R-WP-25). The name is what makes them two here; it selects no conversation and no agent.
     */
    private ResponseEntity<String> send(RestTemplate client, String projectId, String text, String user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return client.postForEntity(url(messagesPath(projectId)),
                new HttpEntity<>(new SendMessageRequest(text, user), headers), String.class);
    }

    private List<ChatMessageDto> messages(RestTemplate client, String projectId) {
        return client.exchange(url(messagesPath(projectId)), HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<ChatMessageDto>>() {
                }).getBody();
    }

    private String rawMessages(RestTemplate client, String projectId) {
        return client.getForObject(url(messagesPath(projectId)), String.class);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private String createProject(RestTemplate client, String slug) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> created = client.postForEntity(url("/api/projects"),
                new HttpEntity<>(Map.of("gitUrl", "https://git.example.com/acme/" + slug + ".git"), headers),
                String.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return JsonPath.read(created.getBody(), "$.id");
    }

    /** The factory's own file — the same one the settings view writes. Never {@code ~/.peon}. */
    private void writeFactoryConfig(String model) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(LlmConfigKeys.PROVIDER_TYPE, "OPEN_AI");
        properties.setProperty(LlmConfigKeys.MODEL, model);
        properties.setProperty(LlmConfigKeys.URL, this.model.getUrl());
        properties.setProperty(LlmConfigKeys.API_KEY, "test-key");
        try (var out = Files.newOutputStream(home.resolve(FactoryHome.CONFIG_FILE))) {
            properties.store(out, "SharedProjectChatTest");
        }
    }

    private static void awaitUntil(BooleanSupplier condition, long timeoutMs, String what) {
        long until = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis())
                    .as("%s traf vor der Deadline von %d ms ein", what, timeoutMs)
                    .isLessThan(until);
            sleep();
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the turn", interrupted);
        }
    }
}
