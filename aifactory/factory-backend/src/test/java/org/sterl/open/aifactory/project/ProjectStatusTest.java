package org.sterl.open.aifactory.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;

import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.open.aifactory.AifactoryApplication;
import org.sterl.open.aifactory.chat.TurnGate;
import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The project list says what is running, without anyone reloading it (R-WP-24, UC-WP-47). Measured on the
 * wire: one connection carries the snapshot of every project and every change after it, so "the row
 * changed by itself" is a statement about that one connection — a test that asked twice would prove the
 * reload and not the push.
 *
 * <p>The states are read through {@link ProjectWorkStates} — the port and not the runtime: the list
 * belongs to {@code project} and is only <em>told</em> what runs (D-6). Nothing of the turn is faked, so
 * the busy window is a real shell tool that holds until the test releases its {@link TurnGate} (as in the
 * queue test) and the waited input really is answered before the row may go free.</p>
 *
 * <p>Ordered on purpose: the restart case reads the projects of the tests before it, because a project
 * that <em>had</em> been working is the only kind of project that makes "free after a restart" say
 * something.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakeGitConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(180)
class ProjectStatusTest {

    /** Holds one turn inside a running tool until the test releases it — long enough to be watched. */
    private static final String GATE_TOOL = TurnGate.toolArguments();

    /** HTTP/1.1 on purpose — the SSE edge is what the browser talks (see {@code ChatQueueTest}). */
    private static final HttpClient STREAMS = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @TempDir
    static Path home;

    private MockLlmServer model;

    @LocalServerPort
    private int port;

    @Autowired
    private ProjectWorkStates workStates;

    private RestTemplate http;

    @AfterAll
    static void stopStreams() {
        // close(), not shutdownNow(): only close() releases the client's selector thread
        STREAMS.close();
    }

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
        registry.add(FactoryHome.HOME_PROPERTY, home::toString);
    }

    @Test
    @Order(1)
    @Timeout(90)
    // UC-WP-47
    void theListShowsWorkingAndFreeAfterTheTurn() throws Exception {
        writeFactoryConfig("inc2-working-47");
        CreatedProject created = createProject("working-47");
        String projectId = created.id();

        try (StatusStream stream = new StatusStream(port)) {
            assertThat(stream.awaitSnapshot())
                    .as("ein neuer Betrachter wird mit dem vollen Stand verbunden")
                    .contains(ProjectWork.free(projectId));

            model.queueResponse(toolCall("call_sleep", "shellRunCommand", GATE_TOOL));
            model.queueResponse("die Antwort des laufenden Turns");

            assertThat(send(projectId, "die erste Frage").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

            WorkStates states = new WorkStates(stream, projectId);
            assertThat(states.await(workingWith(0), 15_000, "die Zeile sagt arbeitet"))
                    .as("arbeitet, und zwar auf genau der einen Verbindung")
                    .isEqualTo(new WorkStates.State(true, 0));

            // the change is read off the one connection; now the tool may end and the row go free
            TurnGate.release(created.localPath());

            assertThat(states.await(free(), 60_000, "die Zeile sagt frei"))
                    .as("nach dem Turn frei — niemand hat neu geladen")
                    .isEqualTo(new WorkStates.State(false, 0));
            assertThat(stream.countOf("projects"))
                    .as("die zwei Wechsel kamen als Änderung, nicht als zweiter voller Stand")
                    .isEqualTo(1);
        }

        assertThat(workStates.current())
                .as("derselbe Stand, direkt gelesen")
                .contains(ProjectWork.free(projectId));
    }

    @Test
    @Order(2)
    @Timeout(90)
    // UC-WP-47
    void aQueuedInputIsCounted() throws Exception {
        writeFactoryConfig("inc2-queued-47");
        CreatedProject created = createProject("queued-47");
        String projectId = created.id();
        String waitedAnswer = "die Antwort auf die gewartete Eingabe";

        try (StatusStream stream = new StatusStream(port)) {
            stream.awaitSnapshot();
            model.queueResponse(toolCall("call_sleep", "shellRunCommand", GATE_TOOL));
            model.queueResponse("die Antwort des laufenden Turns");
            model.queueResponse(waitedAnswer);

            assertThat(send(projectId, "die erste Frage").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            WorkStates states = new WorkStates(stream, projectId);
            states.await(workingWith(0), 15_000, "die Zeile sagt arbeitet");

            assertThat(send(projectId, "die zweite Frage").getStatusCode())
                    .as("die zweite Eingabe wird angenommen, während der Turn läuft")
                    .isEqualTo(HttpStatus.ACCEPTED);
            assertThat(states.await(workingWith(1), 15_000, "die Zeile sagt arbeitet und eine wartend"))
                    .as("gearbeitet wird an der ersten, die zweite wartet — dazwischen gibt es keinen Zustand")
                    .isEqualTo(new WorkStates.State(true, 1));

            // working and waited are both on the wire, so the tool may end and both inputs be answered
            TurnGate.release(created.localPath());
            states.await(free(), 90_000, "die Zeile sagt frei");
            assertThat(messages(projectId))
                    .as("frei heißt: auch die gewartete Eingabe ist abgearbeitet, sonst wäre die Zeile zu früh frei")
                    .extracting(ChatMessageDto::text)
                    .contains(waitedAnswer);
        }
    }

    @Test
    @Order(3)
    @Timeout(90)
    // UC-WP-47
    void afterARestartEveryProjectIsFree() {
        List<ProjectWork> before = workStates.current();
        assertThat(before)
                .as("die Projekte der Tests vorher sind registriert — sonst sagt diese Zeile nichts")
                .isNotEmpty();
        List<ProjectWork> allFree = before.stream()
                .map(state -> ProjectWork.free(state.projectId()))
                .toList();

        // a restarted process knows only its files: the same application in a fresh context over the same
        // home dir (ChatRestartTest boots the same way), with runtimes and listeners of its own — none
        try (ConfigurableApplicationContext restarted = new SpringApplicationBuilder(
                AifactoryApplication.class, FakeGitConfig.class)
                        .properties(
                                FactoryHome.HOME_PROPERTY + "=" + home,
                                "server.port=0",
                                "spring.main.banner-mode=off",
                                "logging.level.root=WARN")
                        .run()) {

            assertThat(restarted.getBean(ProjectWorkStates.class).current())
                    .as("dieselben Projekte, und jedes von ihnen frei")
                    .containsExactlyInAnyOrderElementsOf(allFree);

            int restartedPort = ((WebServerApplicationContext) restarted).getWebServer().getPort();
            try (StatusStream stream = new StatusStream(restartedPort)) {
                assertThat(stream.awaitSnapshot())
                        .as("und wer sich jetzt verbindet, sieht dasselbe")
                        .containsExactlyInAnyOrderElementsOf(allFree);
            }
        }
    }

    // ------------------------------------------------------------------ the edges

    private ResponseEntity<String> send(String projectId, String text) {
        return http.postForEntity(url("/api/projects/" + projectId + "/messages"),
                jsonEntity(new SendMessageRequest(text)), String.class);
    }

    private List<ChatMessageDto> messages(String projectId) {
        return http.exchange(url("/api/projects/" + projectId + "/messages"), HttpMethod.GET,
                HttpEntity.EMPTY, new ParameterizedTypeReference<List<ChatMessageDto>>() {
                }).getBody();
    }

    private CreatedProject createProject(String slug) {
        ResponseEntity<String> created = http.postForEntity(url("/api/projects"),
                jsonEntity(Map.of("gitUrl", "https://git.example.com/acme/" + slug + ".git")), String.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // a typed local, not a nested read: Path.of(URI, String…) would win the overload for T = URI
        String localPath = JsonPath.read(created.getBody(), "$.localPath");
        return new CreatedProject(JsonPath.read(created.getBody(), "$.id"), Path.of(localPath));
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private HttpEntity<Object> jsonEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private static AiMessage toolCall(String id, String toolName, String arguments) {
        return new AiMessage(null,
                List.of(ToolExecutionRequest.builder().id(id).name(toolName).arguments(arguments).build()));
    }

    /** The factory's own file — the same one the settings view writes. Never {@code ~/.peon}. */
    private void writeFactoryConfig(String model) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(LlmConfigKeys.PROVIDER_TYPE, "OPEN_AI");
        properties.setProperty(LlmConfigKeys.MODEL, model);
        properties.setProperty(LlmConfigKeys.URL, this.model.getUrl());
        properties.setProperty(LlmConfigKeys.API_KEY, "test-key");
        try (var out = Files.newOutputStream(home.resolve(FactoryHome.CONFIG_FILE))) {
            properties.store(out, "ProjectStatusTest");
        }
    }

    private static Predicate<WorkStates.State> workingWith(int queued) {
        return state -> state.working() && state.queued() == queued;
    }

    private static Predicate<WorkStates.State> free() {
        return state -> !state.working() && state.queued() == 0;
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
            throw new IllegalStateException("interrupted while waiting for a state", interrupted);
        }
    }

    /** The project as the create answered it — the checkout is what opens the turn gate. */
    private record CreatedProject(String id, Path localPath) {
    }

    /** The state as the wire names it — the three fields {@code ProjectWorkDto} writes. */
    private record WireState(String projectId, boolean working, int queued) {

        static WireState of(String data) {
            return new WireState(JsonPath.read(data, "$.projectId"), JsonPath.read(data, "$.working"),
                    JsonPath.read(data, "$.queued"));
        }
    }

    /**
     * The row of one project: the states this connection carried for it, in the order it carried them.
     * The snapshot is not one of them — a row that was never working may not be read as "it changed".
     */
    private static final class WorkStates {

        record State(boolean working, int queued) {
        }

        private final StatusStream stream;
        private final String projectId;

        WorkStates(StatusStream stream, String projectId) {
            this.stream = stream;
            this.projectId = projectId;
        }

        State await(Predicate<State> wanted, long timeoutMs, String what) {
            awaitUntil(() -> states().stream().anyMatch(wanted), timeoutMs, what);
            return states().stream().filter(wanted).findFirst().orElseThrow();
        }

        private List<State> states() {
            List<State> states = new ArrayList<>();
            for (String data : stream.changes(projectId)) {
                WireState state = WireState.of(data);
                states.add(new State(state.working(), state.queued()));
            }
            return states;
        }
    }

    /**
     * One live connection to the work-state edge (JDK client, not {@code HttpURLConnection} — see
     * {@code ChatQueueTest}). It records event names and data lines, so a state that never arrives is a
     * red test and never a hanging one.
     */
    private static final class StatusStream implements Flow.Subscriber<String>, AutoCloseable {

        private final List<String> names = new CopyOnWriteArrayList<>();
        private final List<String> dataLines = new CopyOnWriteArrayList<>();
        private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        private final CompletableFuture<?> exchange;
        private volatile String event = "message";
        private volatile String failure;

        StatusStream(int port) {
            HttpRequest request = HttpRequest
                    .newBuilder(URI.create("http://127.0.0.1:" + port + "/api/projects/events"))
                    .header("accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                    .timeout(Duration.ofSeconds(120))
                    .GET()
                    .build();
            exchange = STREAMS.sendAsync(request, HttpResponse.BodyHandlers.ofPublisher())
                    .thenAccept(this::follow);
            exchange.exceptionally(broken -> {
                failure = String.valueOf(broken);
                return null;
            });
        }

        private void follow(HttpResponse<Flow.Publisher<List<ByteBuffer>>> response) {
            response.body().subscribe(HttpResponse.BodySubscribers.fromLineSubscriber(this));
        }

        @Override
        public void onSubscribe(Flow.Subscription sub) {
            subscription.set(sub);
            sub.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(String line) {
            if (line.startsWith("event:")) {
                event = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                names.add(event);
                dataLines.add(line.substring("data:".length()).trim());
            }
        }

        @Override
        public void onError(Throwable broken) {
            failure = String.valueOf(broken);
        }

        @Override
        public void onComplete() {
            // the server closed the stream; nothing is buffered here, so nothing is lost either
        }

        /** The snapshot the connection was answered with — every project, free or not. */
        List<ProjectWork> awaitSnapshot() {
            awaitUntil(() -> names.contains("projects"), 15_000,
                    "der Verbindung ihr voller Stand kam");
            List<Map<String, Object>> projects = JsonPath.read(dataLines.get(names.indexOf("projects")),
                    "$.projects");
            return projects.stream()
                    .map(fields -> new ProjectWork((String) fields.get("projectId"),
                            Boolean.TRUE.equals(fields.get("working")),
                            ((Number) fields.get("queued")).intValue()))
                    .toList();
        }

        /** Every {@code project} change of one project, in the order of the wire. */
        List<String> changes(String projectId) {
            List<String> data = new ArrayList<>();
            for (int index = 0; index < names.size(); index++) {
                if ("project".equals(names.get(index))
                        && WireState.of(dataLines.get(index)).projectId().equals(projectId)) {
                    data.add(dataLines.get(index));
                }
            }
            return data;
        }

        int countOf(String name) {
            return (int) names.stream().filter(name::equals).count();
        }

        @Override
        public void close() {
            Flow.Subscription sub = subscription.getAndSet(null);
            if (sub != null) {
                sub.cancel();
            }
            exchange.cancel(true);
        }
    }
}
