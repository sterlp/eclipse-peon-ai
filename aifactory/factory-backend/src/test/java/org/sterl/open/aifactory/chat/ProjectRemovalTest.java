package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

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
import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.project.FakeGitConfig;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The one named deviation of Increment 3d: removing a project drops its runtime. {@code project}
 * publishes the fact of a removal and {@code chat} answers it — there is no call from {@code project}
 * into {@code chat}, and there is no third deletion target: {@code state/<id>} survives, so the project
 * that is registered again finds its own conversation and no second one (R-WP-11, plan Q3).
 *
 * <p>The runtime is reached through the package-private seam of the registry, which is the whole point:
 * a caller over HTTP cannot see a runtime, only the 404 of a project that is gone.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakeGitConfig.class)
@Timeout(90)
class ProjectRemovalTest {

    private static final String GIT_URL = "https://git.example.com/acme/removed-runtime.git";
    private static final String QUESTION = "frage vor dem entfernen";
    private static final String ANSWER = "die Antwort vor dem entfernen";

    @TempDir
    static Path home;

    private MockLlmServer model;

    @LocalServerPort
    private int port;

    @Autowired
    private ProjectRuntimeRegistry runtimes;

    @Autowired
    private FactoryHome factoryHome;

    private RestTemplate http;

    @BeforeEach
    void startModelAndClient() {
        this.model = new MockLlmServer(0);
        this.model.start();
        this.http = new RestTemplate();
        // the status IS the assertion — a throwing client would hide what the app answers
        this.http.setErrorHandler(new NoOpResponseErrorHandler());
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
    void removedProjectLosesItsRuntime() throws Exception {
        writeFactoryConfig("inc3d-removal");
        CreatedProject project = createProject();
        int runtimesBefore = runtimes.runtimeCount();

        model.queueResponse(ANSWER);
        assertThat(send(project.id(), QUESTION).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        awaitUntil(() -> messages(project.id()).stream()
                .anyMatch(line -> ANSWER.equals(line.text())), 20_000, "der Turn ist im Verlauf gelandet");

        ProjectRuntime before = runtimes.runtime(project.id());
        List<ChatMessageDto> historyBefore = messages(project.id());
        Path stateDir = factoryHome.stateDir(project.id());
        assertThat(runtimes.runtimeCount())
                .as("gefragt hat genau ein Projekt, also läuft genau eine Runtime")
                .isEqualTo(runtimesBefore + 1);
        assertThat(filesUnder(stateDir))
                .as("der Core hat seinen Verlauf unter state/<id> abgelegt")
                .isNotEmpty();

        assertThat(delete(project.id())).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(runtimes.runtimeCount())
                .as("ein entferntes Projekt hält keinen Agenten und keine Queue wach")
                .isEqualTo(runtimesBefore);
        assertThat(statusOf(HttpMethod.GET, messagesPath(project.id()), null))
                .as("nach dem Entfernen gibt es den Chat nicht mehr — auch nicht als geleerte Liste")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(HttpMethod.GET, eventsPath(project.id()), null))
                .as("und keine Live-Leitung für ein Projekt, das es nicht mehr gibt")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(HttpMethod.POST, messagesPath(project.id()), new SendMessageRequest("danach")))
                .as("auch eine Eingabe findet kein Projekt mehr")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(runtimes.runtimeCount())
                .as("und ein 404 baut nichts neu auf")
                .isEqualTo(runtimesBefore);

        assertThat(filesUnder(stateDir))
                .as("remove löscht Entry und Checkout, kein drittes Ziel — der Verlauf bleibt (Q3)")
                .isNotEmpty();

        CreatedProject again = createProject();
        assertThat(again.id())
                .as("dieselbe URL ist dasselbe Projekt, also auch dasselbe Gespräch")
                .isEqualTo(project.id());
        assertThat(messages(again.id()))
                .as("der alte Chat ist wieder da, kein leeres Zweitgespräch daneben")
                .isEqualTo(historyBefore);
        assertThat(runtimes.runtime(again.id()))
                .as("die verworfene Runtime ist weg, eine neue liest denselben Bestand")
                .isNotSameAs(before);
    }

    // ------------------------------------------------------------------ edges

    private String messagesPath(String projectId) {
        return "/api/projects/" + projectId + "/messages";
    }

    private String eventsPath(String projectId) {
        return "/api/projects/" + projectId + "/events";
    }

    private ResponseEntity<String> send(String projectId, String text) {
        return http.postForEntity(url(messagesPath(projectId)), jsonEntity(new SendMessageRequest(text)),
                String.class);
    }

    private HttpStatus delete(String projectId) {
        return (HttpStatus) http.exchange(url("/api/projects/" + projectId), HttpMethod.DELETE,
                HttpEntity.EMPTY, String.class).getStatusCode();
    }

    private HttpStatus statusOf(HttpMethod method, String path, Object body) {
        return (HttpStatus) http.exchange(url(path), method,
                body == null ? HttpEntity.EMPTY : jsonEntity(body), String.class).getStatusCode();
    }

    private List<ChatMessageDto> messages(String projectId) {
        return http.exchange(url(messagesPath(projectId)), HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<ChatMessageDto>>() {
                }).getBody();
    }

    private HttpEntity<Object> jsonEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    /** The same url twice — identity is the normalized url, so the id must come back identical. */
    private CreatedProject createProject() {
        ResponseEntity<String> created = http.postForEntity(url("/api/projects"),
                jsonEntity(Map.of("gitUrl", GIT_URL)), String.class);

        assertThat(created.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.OK);
        return new CreatedProject(JsonPath.read(created.getBody(), "$.id"));
    }

    /** The factory's own file — the same one the settings view writes. Never {@code ~/.peon}. */
    private void writeFactoryConfig(String model) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(LlmConfigKeys.PROVIDER_TYPE, "OPEN_AI");
        properties.setProperty(LlmConfigKeys.MODEL, model);
        properties.setProperty(LlmConfigKeys.URL, this.model.getUrl());
        properties.setProperty(LlmConfigKeys.API_KEY, "test-key");
        try (var out = Files.newOutputStream(home.resolve(FactoryHome.CONFIG_FILE))) {
            properties.store(out, "ProjectRemovalTest");
        }
    }

    private static List<Path> filesUnder(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> tree = Files.walk(directory)) {
            return tree.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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

    private record CreatedProject(String id) {
    }
}
