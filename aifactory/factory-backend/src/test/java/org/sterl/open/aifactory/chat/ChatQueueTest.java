package org.sterl.open.aifactory.chat;

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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;

import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.llmpeon.queuedmessages.UserMessageQueue;
import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.project.FakeGitConfig;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * A second input that arrives while Jon is mid-turn must not start a second turn on the same memory: it
 * lands in the queue of the very same Jon and is answered by him (UC-WP-14, UC-WP-22). Nothing below
 * {@code AiPoAgent} is stubbed — the busy guard, the {@code UserMessageQueue}, the drain and the tool loop
 * are the production ones. The turn is slowed down by a <b>real</b> {@code sleep 3} shell tool (working
 * directory = the checkout, the same binding UC-WP-5 proves), so the window in which the second POST must
 * queue is real, never a faked delay.
 *
 * <p>The measurements are the ones the core actually exposes — {@code MockLlmServer} has no request
 * counter. What is proven: the queue length of the live Jon, the raw body of the last provider request,
 * and the {@code [Queued Message]} marker the core writes into the memory. A parallel defect would already
 * have consumed the second text at the provider (queue back to 0) and sent it without a marker — those are
 * exactly the assertions that turn red. Where the <em>wording</em> of a text is checked it is matched with
 * {@code contains}: the 10 s burst window may merge two quick sends, and merging is allowed core behaviour
 * (plan §3.6), so a queue count is only asserted exactly where one send meets an empty queue.</p>
 *
 * <p>The model double is <b>fresh per test</b>: a turn is fire-and-forget, so a test whose assertions fail
 * midway still has a {@code sleep 3} turn running on the shared executor. With one shared double that
 * orphaned turn would drain the next test's scripted answers; with a per-test double it points at its own
 * already-stopped server and dies harmlessly. Every test also waits for its own turn to end.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakeGitConfig.class)
@Timeout(120)
class ChatQueueTest {

    /** Real tool content that keeps one turn busy long enough for a second POST to have to queue. */
    private static final String SLEEP_TOOL = "{\"command\":\"sleep 3\",\"timeout\":20}";

    /** HTTP/1.1 on purpose — the SSE edge is what the browser talks (see {@code ChatTurnTest}). */
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
    private ProjectRuntimeRegistry runtimes;

    private RestTemplate http;

    @AfterAll
    static void stopStreams() {
        STREAMS.shutdownNow();
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
        registry.add("aifactory.home", home::toString);
    }

    @Test
    @Timeout(60)
    // UC-WP-14
    void secondInputDuringTurnQueues() throws Exception {
        writeFactoryConfig("inc3c-queue-14");
        CreatedProject project = createProject("queue-14");

        try (ChatStream stream = new ChatStream(project.id())) {
            // one tool turn needs both halves: the call, then the answer that ends the loop; then the
            // reply to the queued input — three scripted answers in order.
            model.queueResponse(toolCall("call_sleep", "shellRunCommand", SLEEP_TOOL));
            model.queueResponse("erste Antwort fertig");
            model.queueResponse("zweite Antwort aus der Warteschlange");

            assertThat(send(project.id(), "erste Frage").getStatusCode())
                    .as("die erste Eingabe startet einen Turn")
                    .isEqualTo(HttpStatus.ACCEPTED);
            ProjectRuntime runtime = runtimes.runtime(project.id());
            AiPoAgent jon = runtime.jon();
            awaitUntil(jon::isWorking, 2_000, "Jon arbeitet an der ersten Eingabe");
            assertThat(jon.getQueuedMessageCount())
                    .as("noch ist nichts gewartet — die erste Eingabe ist der laufende Turn")
                    .isZero();
            stream.awaitShellRunning(5_000, "die Shell des ersten Turns läuft");

            assertThat(send(project.id(), "zweite Frage").getStatusCode())
                    .as("die zweite Eingabe wird angenommen")
                    .isEqualTo(HttpStatus.ACCEPTED);

            awaitUntil(() -> jon.getQueuedMessageCount() == 1, 2_000,
                    "die zweite Eingabe liegt in der Queue desselben Jon");
            assertThat(model.getLastRequestBody())
                    .as("wartend heißt: noch nicht beim Anbieter — kein zweiter Turn")
                    .doesNotContain("zweite Frage");

            stream.awaitEvent("done", 2);
            assertThat(stream.countOf("done"))
                    .as("genau ein done je beantworteter Eingabe — die Queue erzeugt kein drittes")
                    .isEqualTo(2);
        }

        assertThat(capturedText())
                .as("der gewartete Text hat das Modell erreicht")
                .contains("zweite Frage");

        List<ChatMessageDto> history = messages(project.id());
        assertThat(history)
                .as("die erste Eingabe ist ein normaler Turn ohne Wartungs-Marke")
                .contains(new ChatMessageDto("user", "erste Frage"));
        assertThat(history)
                .as("die gewartete Eingabe trägt den Core-Marker, so wie der Core sie schreibt (Q6)")
                .anySatisfy(line -> {
                    assertThat(line.role()).isEqualTo("user");
                    assertThat(line.text()).contains("[Queued Message]").contains("zweite Frage");
                });
        assertThat(history)
                .as("beide Antworten stammen von Jon")
                .extracting(ChatMessageDto::text)
                .contains("erste Antwort fertig", "zweite Antwort aus der Warteschlange");
    }

    @Test
    @Timeout(60)
    // UC-WP-22
    void inputOnBusyProjectQueuesNotParallel() throws Exception {
        writeFactoryConfig("inc3c-parallel-22");
        CreatedProject project = createProject("parallel-22");

        try (ChatStream stream = new ChatStream(project.id())) {
        model.queueResponse(toolCall("call_sleep", "shellRunCommand", SLEEP_TOOL));
        model.queueResponse("erste Antwort fertig");
        model.queueResponse("zweite Antwort aus der Warteschlange");

        assertThat(send(project.id(), "erste Frage").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        ProjectRuntime runtime = runtimes.runtime(project.id());
        AiPoAgent jon = runtime.jon();
        int runtimesBefore = runtimes.runtimeCount();
        awaitUntil(jon::isWorking, 2_000, "Jon arbeitet an der ersten Eingabe");
        stream.awaitShellRunning(5_000, "die Shell des ersten Turns läuft");

        long startedAt = System.nanoTime();
        assertThat(send(project.id(), "zweite Frage").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        long postMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        assertThat(postMillis)
                .as("der POST wartet nicht auf den Turn — angenommen statt blockiert (R-WP-15)")
                .isLessThan(1_000);
        awaitUntil(() -> jon.getQueuedMessageCount() == 1, 2_000,
                "die zweite Eingabe liegt in der Queue desselben Jon, nicht in einem zweiten Turn");
        assertThat(model.getLastRequestBody())
                .as("während sie wartet, war die zweite Eingabe nachweislich nie beim Anbieter")
                .doesNotContain("zweite Frage");
        assertThat(runtimes.runtimeCount())
                .as("die beschäftigte Eingabe legt keine zweite Runtime an")
                .isEqualTo(runtimesBefore);
        assertThat(runtimes.runtime(project.id()))
                .as("dieselbe Runtime, derselbe Jon, also auch dieselbe Memory")
                .isSameAs(runtime);
        assertThat(jon.getName())
                .as("und der eine Jon ist Jon")
                .isEqualTo(AiPoAgent.NAME);

        // Completion is anchored on the queued input's answer landing in THIS conversation's history:
        // isWorking() only says a turn is held, not that the waiting input was drained and answered —
        // the answer in the history is the honest signal that the same Jon did it, never a parallel turn.
        awaitUntil(() -> messages(project.id()).stream()
                .map(ChatMessageDto::text)
                .anyMatch("zweite Antwort aus der Warteschlange"::equals), 20_000,
                "die gewartete Eingabe ist von demselben Jon beantwortet");

        assertThat(jon.getQueuedMessageCount())
                .as("nach dem Abarbeiten ist die Queue wieder leer")
                .isZero();
        assertThat(capturedText())
                .as("die gewartete Eingabe hat das Modell erreicht")
                .contains("zweite Frage");
        assertThat(messages(project.id()))
                .as("beide Antworten stammen aus dem einen Verlauf desselben Jons")
                .extracting(ChatMessageDto::text)
                .contains("erste Antwort fertig", "zweite Antwort aus der Warteschlange");
        }
    }

    @Test
    @Timeout(60)
    void twoSendsWhileBusyAreBothAnswered() throws Exception {
        writeFactoryConfig("inc3c-two-3c");
        CreatedProject project = createProject("two-3c");

        try (ChatStream stream = new ChatStream(project.id())) {
            model.queueResponse(toolCall("call_sleep", "shellRunCommand", SLEEP_TOOL));
            model.queueResponse("erste Antwort fertig");
            // verschmolzen braucht die Queue eine Antwort, nicht verschmolzen zwei — beide Fälle abdecken
            model.queueResponse("erste Warteschlangen-Antwort");
            model.queueResponse("zweite Warteschlangen-Antwort");

            assertThat(send(project.id(), "erste Frage").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            ProjectRuntime runtime = runtimes.runtime(project.id());
            AiPoAgent jon = runtime.jon();
            awaitUntil(jon::isWorking, 2_000, "Jon arbeitet");
            stream.awaitShellRunning(5_000, "die Shell des ersten Turns läuft");

            assertThat(send(project.id(), "zweiter Text").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(send(project.id(), "dritter Text").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

            awaitUntil(() -> jon.getQueuedMessageCount() >= 1, 2_000, "beide wartenden Texte sind angemeldet");
            assertThat(jon.getQueuedMessageCount())
                    .as("zum einen Eintrag verschmolzen oder getrennt — beides ist zulässiges Core-Verhalten")
                    .isBetween(1, 2);

            stream.awaitEvent("done", 2);
            assertThat(stream.countOf("done"))
                    .as("mindestens ein done je beantworteter Eingabe, aber nie ein dritter Turn")
                    .isGreaterThanOrEqualTo(2);

            awaitUntil(() -> !jon.isWorking(), 20_000, "alles abgearbeitet");

            assertThat(jon.getQueuedMessageCount())
                    .as("die Queue ist leer — nichts ist hängen geblieben")
                    .isZero();
            assertThat(capturedText())
                    .as("beide Texte haben das Modell erreicht, verschmolzen oder getrennt")
                    .contains("zweiter Text", "dritter Text");
            // the marker is the core's fingerprint of the running turn's own drain (pollNext →
            // queuedMarker); a second turn that overtook would have joined the texts without it
            assertThat(capturedText())
                    .as("die wartenden Texte sind vom laufenden Turn abgeholt worden, nicht von einem zweiten")
                    .contains(UserMessageQueue.QUEUED_MARKER_PREFIX);
            assertThat(runtimes.runtime(project.id()))
                    .as("es antwortet weiterhin genau derselbe Jon — kein dritter Turn auf neuer Memory")
                    .isSameAs(runtime);
        }
    }

    // ------------------------------------------------------------------
    // the three edges
    // ------------------------------------------------------------------

    private ResponseEntity<String> send(String projectId, String text) {
        return http.postForEntity("http://127.0.0.1:" + port + "/api/projects/" + projectId + "/messages",
                jsonEntity(new SendMessageRequest(text)), String.class);
    }

    private HttpEntity<Object> jsonEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private List<ChatMessageDto> messages(String projectId) {
        return http.exchange("http://127.0.0.1:" + port + "/api/projects/" + projectId + "/messages",
                HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<ChatMessageDto>>() {
                }).getBody();
    }

    private CreatedProject createProject(String slug) {
        ResponseEntity<String> created = http.postForEntity("http://127.0.0.1:" + port + "/api/projects",
                jsonEntity(Map.of("gitUrl", "https://git.example.com/acme/" + slug + ".git")), String.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String localPath = JsonPath.read(created.getBody(), "$.localPath");
        return new CreatedProject(JsonPath.read(created.getBody(), "$.id"), Path.of(localPath));
    }

    /** Every message the model was ever shown, as one haystack — the queued marker text appears here. */
    private String capturedText() {
        return model.getCapturedMessages().toString();
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
            properties.store(out, "ChatQueueTest");
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

    private record CreatedProject(String id, Path localPath) {
    }

    /**
     * One live connection, only as far as counting the answered inputs needs: it records the
     * {@code event:} names and waits on a deadline, so a missing {@code done} is a red test and never a
     * hanging one. Same reader as {@code ChatTurnTest} (JDK client, not {@code HttpURLConnection}).
     */
    private final class ChatStream implements Flow.Subscriber<String>, AutoCloseable {

        private final List<String> names = new CopyOnWriteArrayList<>();
        private final List<String> dataLines = new CopyOnWriteArrayList<>();
        private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        private final CompletableFuture<?> exchange;
        private volatile String event = "message";
        private volatile String failure;

        ChatStream(String projectId) {
            HttpRequest request = HttpRequest
                    .newBuilder(URI.create("http://127.0.0.1:" + port + "/api/projects/" + projectId + "/events"))
                    .header("accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                    .timeout(Duration.ofSeconds(60))
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

        /**
         * Blocks until a live {@code tool} line names the running shell command — the shell writes
         * {@code Running: `sleep 3`} just before it blocks in {@code process.waitFor}. Once it is on the
         * wire, the turn is provably inside the three-second tool, so any send that now arrives meets a
         * busy Jon and must queue; the queue assertion stops racing the turn's start.
         */
        void awaitShellRunning(long timeoutMs, String what) {
            long until = System.currentTimeMillis() + timeoutMs;
            while (dataLines.stream().noneMatch(d -> d.contains("Running") && d.contains("sleep 3"))) {
                assertThat(System.currentTimeMillis())
                        .as("%s kam vor der Deadline von %d ms an (Verbindung: %s)", what, timeoutMs, failure)
                        .isLessThan(until);
                sleep();
            }
        }

        void awaitEvent(String name, int count) {
            long until = System.currentTimeMillis() + 30_000;
            while (countOf(name) < count) {
                assertThat(System.currentTimeMillis())
                        .as("event %s x%d kam an (Verbindung: %s)", name, count, failure)
                        .isLessThan(until);
                sleep();
            }
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
