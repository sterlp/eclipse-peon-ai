package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.context.ApplicationContext;
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

import dev.langchain4j.model.chat.StreamingChatModel;

import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.project.FakeGitConfig;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * UC-WP-24 GIVEN 3: a value changed in <em>Einstellungen</em> is what the <b>next</b> turn runs with —
 * no restart, no second runtime, no second model. {@code ProjectRuntime.submit} hands the model a fresh
 * read per turn, so the change lands on the next turn instead of on the next process.
 *
 * <p>The model name is the proof because it is the one value that survives the whole chain unchanged:
 * Jon runs {@code poAgentConfig()} and falls back to {@code cfg.getModel()}, since the factory's file
 * carries no {@code llm.agent.*} key — so the {@code "model"} the provider was told to run is exactly
 * the value that was edited (§3.8). GIVEN 1 and 2 are not repeated here: they are
 * {@code FactoryConfigServiceTest#configDirIsAlwaysTheFactoryHome},
 * {@code FactoryConfigServiceTest#configFileWinsOverEnvironment} and
 * {@code SettingsControllerTest#settingsShowsMaskedConfig}; the plugin's own store is never read or
 * written by this class (AGENTS poisoning rule) and is compared byte-wise in the gate report instead.</p>
 *
 * <p>The order of the two writes is the trap this class has to walk around: the core compares
 * {@code updateConfig} against the value it holds, so the file is written by hand <b>first</b> (that is
 * the state the runtime is built in) and the change goes through the settings route afterwards.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakeGitConfig.class)
@Timeout(120)
class ChatConfigTurnTest {

    private static final String FIRST_MODEL = "inc3e-first";
    private static final String SECOND_MODEL = "inc3e-second";

    @TempDir
    static Path home;

    /** Per test: a turn is fire-and-forget, so an orphan of a failing test must not drain the next one. */
    private MockLlmServer llm;

    @LocalServerPort
    private int port;

    @Autowired
    private ProjectRuntimeRegistry runtimes;

    /** The witness that nothing was restarted: the same running application served every turn. */
    @Autowired
    private ApplicationContext context;

    private RestTemplate http;

    @BeforeEach
    void startProviderDouble() {
        this.llm = new MockLlmServer(0);
        this.llm.start();
        this.http = new RestTemplate();
        // the status IS the assertion — a client that throws would hide what the app answers
        this.http.setErrorHandler(new NoOpResponseErrorHandler());
    }

    @AfterEach
    void stopProviderDouble() {
        if (llm != null) {
            llm.stop();
        }
    }

    @DynamicPropertySource
    static void isolateHome(DynamicPropertyRegistry registry) {
        registry.add("aifactory.home", home::toString);
    }

    @Test
    // UC-WP-24
    void settingsOverrideNeverWritesSharedStore() throws Exception {
        writeFactoryConfig(FIRST_MODEL, llm.getUrl());
        String projectId = createProject("next-turn-new-value");

        String contextId = context.getId();
        ProjectRuntime runtime = null;
        ConfiguredChatModel model = null;
        StreamingChatModel builtInFirstTurn = null;
        StreamingChatModel builtInSecondTurn = null;

        llm.queueResponse("erste Antwort");
        assertThat(send(projectId, "erste Frage").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        awaitJonAnswer(projectId, "erste Antwort");

        assertThat(modelOnTheWire())
                .as("der erste Turn läuft mit dem Modell aus der Datei der Fabrik")
                .isEqualTo(FIRST_MODEL);
        runtime = runtimes.runtime(projectId);
        model = runtime.model();
        builtInFirstTurn = model.getChatModel();

        // the change goes through the settings route of Inc 2 — a full value, the route validates
        ResponseEntity<String> saved = updateSettings(Map.of(
                "provider", "OPEN_AI",
                "model", SECOND_MODEL,
                "url", llm.getUrl(),
                "apiKey", "test-key"));

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonPath.<String>read(saved.getBody(), "$.model"))
                .as("die Antwort der Route ist ein frischer gelesener Stand, nicht das eingesandte Body")
                .isEqualTo(SECOND_MODEL);
        assertThat(JsonPath.<Boolean>read(saved.getBody(), "$.keySet"))
                .as("ein nicht eingesandtes Feld bleibt, was es war — der Schlüssel ist noch gesetzt")
                .isEqualTo(true);
        assertThat(Files.readString(home.resolve(FactoryHome.CONFIG_FILE)))
                .as("gespeichert wurde in die Datei der Fabrik (GIVEN 1, hier nur die Tat selbst)")
                .contains(SECOND_MODEL);

        awaitIdle(runtime);
        llm.queueResponse("zweite Antwort");
        assertThat(send(projectId, "zweite Frage").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        awaitJonAnswer(projectId, "zweite Antwort");

        assertThat(modelOnTheWire())
                .as("GIVEN 3: der nächste Turn nutzt den neuen Wert, ohne Neu-Start")
                .isEqualTo(SECOND_MODEL);
        assertThat(runtimes.runtime(projectId))
                .as("dieselbe Runtime — nicht eine zweite, die den neuen Wert beim Bau holen müsste")
                .isSameAs(runtime);
        assertThat(runtime.model())
                .as("dasselbe ConfiguredChatModel — die Konfiguration wird pro Turn gelesen, nicht pro Runtime")
                .isSameAs(model);
        assertThat(model.getChatModel())
                .as("und gerade weil pro Turn gelesen wird, wurde das gebaute Modell genau einmal getauscht")
                .isNotSameAs(builtInFirstTurn);
        builtInSecondTurn = model.getChatModel();

        // third turn with an unchanged config: it runs as before, and nothing is rebuilt again
        awaitIdle(runtime);
        llm.queueResponse("dritte Antwort");
        assertThat(send(projectId, "dritte Frage").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        awaitJonAnswer(projectId, "dritte Antwort");

        assertThat(modelOnTheWire())
                .as("ein dritter Turn ohne Änderung läuft mit demselben Wert")
                .isEqualTo(SECOND_MODEL);
        assertThat(model.getChatModel())
                .as("kein Dauer-Neubau: eine unveränderte Konfiguration wirft das gebaute Modell nicht weg")
                .isSameAs(builtInSecondTurn);

        assertThat(context.getId())
                .as("alle drei Turns liefen in derselben laufenden Anwendung")
                .isEqualTo(contextId);
        assertThat(runtimes.runtimeCount()).isEqualTo(1);
        assertThat(messages(projectId))
                .as("drei Eingaben, drei Antworten, in einer Reihenfolge")
                .containsExactly(
                        new ChatMessageDto("user", "erste Frage"),
                        new ChatMessageDto("jon", "erste Antwort"),
                        new ChatMessageDto("user", "zweite Frage"),
                        new ChatMessageDto("jon", "zweite Antwort"),
                        new ChatMessageDto("user", "dritte Frage"),
                        new ChatMessageDto("jon", "dritte Antwort"));
    }

    // ------------------------------------------------------------------
    // the three edges and the settings route
    // ------------------------------------------------------------------

    private ResponseEntity<String> send(String projectId, String text) {
        return http.postForEntity("http://127.0.0.1:" + port + "/api/projects/" + projectId + "/messages",
                json(new SendMessageRequest(text)), String.class);
    }

    private List<ChatMessageDto> messages(String projectId) {
        return http.exchange("http://127.0.0.1:" + port + "/api/projects/" + projectId + "/messages",
                HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<List<ChatMessageDto>>() {
                }).getBody();
    }

    private ResponseEntity<String> updateSettings(Object body) {
        return http.exchange("http://127.0.0.1:" + port + "/api/settings", HttpMethod.PUT, json(body), String.class);
    }

    private String createProject(String slug) {
        ResponseEntity<String> created = http.postForEntity("http://127.0.0.1:" + port + "/api/projects",
                json(Map.of("gitUrl", "https://git.example.com/acme/" + slug + ".git")), String.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return JsonPath.read(created.getBody(), "$.id");
    }

    private HttpEntity<Object> json(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    /** The model the provider was told to run — read from the request body, never from a config object. */
    private String modelOnTheWire() {
        return JsonPath.read(llm.getLastRequestBody(), "$.model");
    }

    private void awaitJonAnswer(String projectId, String answer) {
        awaitUntil(() -> messages(projectId).stream()
                .anyMatch(line -> "jon".equals(line.role()) && answer.equals(line.text())),
                20_000, "Jon hat „" + answer + "“ in den Verlauf geschrieben");
    }

    /**
     * Idle before the next send: a still-running turn would take the input into its queue, and then the
     * config read of that turn is not the one under test (the queue itself is UC-WP-14).
     */
    private void awaitIdle(ProjectRuntime runtime) {
        awaitUntil(() -> !runtime.jon().isWorking(), 20_000, "Jons vorheriger Turn ist zu Ende");
    }

    /**
     * The state the runtime is built in: written by hand, because the settings route is the writer of
     * the <b>change</b> — writing both values through it would hide whether a turn reads the file again.
     * Never {@code ~/.peon}.
     */
    private static void writeFactoryConfig(String model, String url) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(LlmConfigKeys.PROVIDER_TYPE, "OPEN_AI");
        properties.setProperty(LlmConfigKeys.MODEL, model);
        properties.setProperty(LlmConfigKeys.URL, url);
        properties.setProperty(LlmConfigKeys.API_KEY, "test-key");
        try (var out = Files.newOutputStream(home.resolve(FactoryHome.CONFIG_FILE))) {
            properties.store(out, "ChatConfigTurnTest");
        }
    }

    private static void awaitUntil(BooleanSupplier condition, long millis, String what) {
        long until = System.currentTimeMillis() + millis;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis())
                    .as("%s innerhalb der Frist", what)
                    .isLessThan(until);
            try {
                Thread.sleep(25);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("unterbrochen während: " + what, interrupted);
            }
        }
    }
}
