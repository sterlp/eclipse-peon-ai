package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.JsonPath;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.config.FactoryConfigService;
import org.sterl.open.aifactory.project.FakeGitConfig;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The three HTTP edges of the chat over real HTTP, against the real agent: nothing here is stubbed
 * below {@code AiPoAgent}, so the tool loop, the memory, the queue and the {@code AiMonitor} chain are
 * the production ones. Only the two outside worlds are replaced — the model by {@link MockLlmServer}
 * (the core's own test double, so the OpenAI wire format stays honest) and git by
 * {@link FakeGitConfig} (a checkout folder without a network).
 *
 * <p>Every test works on its own project, so none of them can inherit another one's runtime or
 * history — that sharing is the subject of UC-WP-16, not a fixture here.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakeGitConfig.class)
@Timeout(120)
class ChatTurnTest {

    /** Longer than the mock's 10-char chunk width, so a streamed answer is provable. */
    private static final String LONG_ANSWER = "Die Antwort kommt in mehreren Stücken beim Leser an.";

    /**
     * HTTP/1.1 on purpose: the SSE edge is what the browser talks, and Tomcat answers a h2c upgrade
     * differently than it answers a plain event stream.
     */
    private static final HttpClient STREAMS = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @TempDir
    static Path home;

    private static final MockLlmServer MODEL = new MockLlmServer(0);

    @LocalServerPort
    private int port;

    @Autowired
    private ProjectRuntimeRegistry runtimes;

    @Autowired
    private FactoryConfigService config;

    private RestTemplate http;

    @BeforeAll
    static void startModel() {
        MODEL.start();
    }

    @AfterAll
    static void stopModelAndStreams() {
        MODEL.stop();
        // close(), not shutdownNow(): only close() releases the client's selector thread — shutdownNow
        // leaves it, and a live non-daemon selector thread is what makes the test fork wait for its exit
        STREAMS.close();
    }

    @BeforeEach
    void resetModelAndClient() {
        MODEL.reset();
        this.http = new RestTemplate();
        // the status IS the assertion — a client that throws would hide what the app answers
        this.http.setErrorHandler(new NoOpResponseErrorHandler());
    }

    @DynamicPropertySource
    static void isolateHome(DynamicPropertyRegistry registry) {
        registry.add("aifactory.home", home::toString);
    }

    @Test
    void unknownProjectHasNoChatAndABlankSendIsRejected() {
        int runtimesBefore = runtimes.runtimeCount();

        assertThat(statusOf(HttpMethod.GET, "/api/projects/missing/messages", null)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(HttpMethod.POST, messagesPath("missing"), Map.of("text", "hallo")))
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(HttpMethod.GET, eventsPath("missing"), null)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(runtimes.runtimeCount())
                .as("a request for a project that does not exist never builds an agent")
                .isEqualTo(runtimesBefore);

        CreatedProject project = createProject("blank-send");
        assertThat(statusOf(HttpMethod.POST, messagesPath(project.id()), Map.of("text", "   ")))
                .as("the composer's blank send is a client error, not a turn")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(runtimes.runtimeCount())
                .as("and it is rejected before an agent is built")
                .isEqualTo(runtimesBefore);
    }

    @Test
    // UC-WP-5
    void openProjectBindsWorkDir() throws Exception {
        writeFactoryConfig("OPEN_AI", "inc3a-work-dir", "test-key", MODEL.getUrl());
        CreatedProject project = createProject("binds-work-dir");

        try (ChatStream stream = new ChatStream(project.id())) {
            MODEL.queueResponse(toolCall("call_pwd", "shellRunCommand", "{\"command\":\"pwd\"}"));
            MODEL.queueResponse("das Verzeichnis steht in der Werkzeugzeile");
            assertThat(send(project.id(), "wo arbeitest du?").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

            stream.awaitEvent("done", 1);

            assertThat(stream.payloads("tool"))
                    .as("the shell tool runs in the project's checkout, not where the server started")
                    .anySatisfy(line -> assertThat(line).contains(project.localPath().toString()));
            assertThat(toolResults(MODEL.getCapturedMessages()))
                    .as("and that is what the model reads back from the tool")
                    .anySatisfy(result -> assertThat(result).startsWith("cwd=" + project.localPath()));

            MODEL.queueResponse(toolCall("call_write", "diskWriteFile",
                    "{\"filePath\":\"notes/marker.txt\",\"content\":\"fabrik-marker\"}"));
            MODEL.queueResponse("notiert");
            send(project.id(), "schreib notes/marker.txt");

            stream.awaitEvent("done", 2);

            Path marker = project.localPath().resolve("notes/marker.txt");
            assertThat(marker).as("a write of Da Boss lands inside the checkout").exists();
            assertThat(Files.readString(marker)).isEqualTo("fabrik-marker");
            assertThat(stream.payloads("tool"))
                    .as("the tool line names the absolute path, so the user sees where it went")
                    .anySatisfy(line -> assertThat(line).contains(marker.toString()));
        }

        assertThat(MODEL.getCapturedTool("shellRunCommand")).as("Jon offers the shell").isPresent();
        assertThat(MODEL.getCapturedTool("diskWriteFile")).as("Jon offers the disk tools").isPresent();
    }

    @Test
    // UC-WP-6
    void chatAlwaysGoesToJon() throws Exception {
        writeFactoryConfig("OPEN_AI", "inc3a-jon", "test-key", MODEL.getUrl());
        CreatedProject project = createProject("always-jon");

        assertThat(Arrays.stream(SendMessageRequest.class.getRecordComponents())
                .map(recordComponent -> recordComponent.getName()).toList())
                .as("the composer sends the text and nothing else — no field can name another agent")
                .containsExactly("text");

        try (ChatStream stream = new ChatStream(project.id())) {
            MODEL.queueResponse("erste Antwort");
            send(project.id(), "erste Frage");
            stream.awaitEvent("done", 1);

            MODEL.queueResponse("zweite Antwort");
            send(project.id(), "zweite Frage");
            stream.awaitEvent("done", 2);

            assertThat(MODEL.getLastRequestBody())
                    .as("both turns went to one and the same agent")
                    .contains("Your name is Da Boss.")
                    .contains("erste Frage")
                    .contains("zweite Frage");
            assertThat(MODEL.getCapturedTool("buildWithDev"))
                    .as("and the agent that answers is Jon: only his team delegation offers it")
                    .isPresent();
        }

        ProjectRuntime runtime = runtimes.runtime(project.id());
        assertThat(runtimes.runtime(project.id())).isSameAs(runtime);
        assertThat(runtime.jon().getName()).isEqualTo(AiPoAgent.NAME);
        assertThat(messages(project.id())).containsExactly(
                new ChatMessageDto("user", "erste Frage"),
                new ChatMessageDto("jon", "erste Antwort"),
                new ChatMessageDto("user", "zweite Frage"),
                new ChatMessageDto("jon", "zweite Antwort"));
    }

    @Test
    // UC-WP-7
    void streamingUpdatesChatWithoutReload() throws Exception {
        writeFactoryConfig("OPEN_AI", "inc3a-live", "test-key", MODEL.getUrl());
        CreatedProject project = createProject("live-lines");

        try (ChatStream early = new ChatStream(project.id())) {
            MODEL.queueResponse(toolCall("call_sleep", "shellRunCommand",
                    "{\"command\":\"sleep 1\",\"timeout\":20}"));
            MODEL.queueResponse(LONG_ANSWER);
            assertThat(send(project.id(), "eine Frage").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(early.countOf("done"))
                    .as("the POST is never the turn's thread — it answered while Jon is still working")
                    .isZero();

            early.awaitEvent("partial", 2);
            // a prefix, not the whole text: the reader lags on purpose, so what is provable at this point
            // is that the answer arrives piece by piece on this connection — the complete text is
            // asserted at its deterministic anchor, after the done of this very input
            assertThat(LONG_ANSWER)
                    .as("the answer grows chunk by chunk on the open connection")
                    .startsWith(early.answerText());
            assertThat(early.payloads("tool"))
                    .as("a tool line is its own line, not part of the answer")
                    .isNotEmpty();

            early.awaitEvent("done", 1);
            assertThat(early.answerText())
                    .as("every piece of the answer came live — no reload, no second source")
                    .isEqualTo(LONG_ANSWER);
            assertThat(early.names())
                    .as("one answered input is one done, and nothing is replayed")
                    .filteredOn("done"::equals).hasSize(1);
            assertThat(early.status()).isEqualTo(200);
            assertThat(early.contentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
            assertThat(early.header("cache-control")).isEqualTo("no-cache");
            assertThat(early.header("x-accel-buffering")).isEqualTo("no");

            assertThat(messages(project.id()))
                    .as("the finished text is read from the history, it never came over the stream")
                    .endsWith(new ChatMessageDto("jon", LONG_ANSWER));

            try (ChatStream late = new ChatStream(project.id())) {
                MODEL.queueResponse("zweite Antwort");
                send(project.id(), "zweite Frage");
                late.awaitEvent("done", 1);

                assertThat(late.answerText())
                        .as("a subscriber that comes later sees the next turn only, never this one again")
                        .isEqualTo("zweite Antwort");
            }

            assertThat(messages(project.id()))
                    .as("and the history holds exactly one copy of the answer")
                    .filteredOn(message -> LONG_ANSWER.equals(message.text())).hasSize(1);
        }
    }

    @Test
    // UC-WP-20
    void configReadFromFactoryOwnStore() throws Exception {
        // GIVEN 1 is the factory's own file and not the plugin's: proven by
        // FactoryConfigServiceTest#configDirIsAlwaysTheFactoryHome and #configFileWinsOverEnvironment.
        // The pinning is one assertion, it is not repeated here.
        writeFactoryConfig("OPEN_AI", "inc3a-first-model", "test-key", MODEL.getUrl());
        CreatedProject project = createProject("own-store");

        try (ChatStream stream = new ChatStream(project.id())) {
            MODEL.queueResponse("erste Antwort");
            send(project.id(), "erste Frage");
            stream.awaitEvent("done", 1);

            assertThat(modelOfLastRequest())
                    .as("the turn ran with the model named in the factory's file")
                    .isEqualTo("inc3a-first-model");

            writeFactoryConfig("OPEN_AI", "inc3a-second-model", "test-key", MODEL.getUrl());
            MODEL.queueResponse("zweite Antwort");
            send(project.id(), "zweite Frage");
            stream.awaitEvent("done", 2);

            assertThat(modelOfLastRequest())
                    .as("the value in the file is what the turn uses")
                    .isEqualTo("inc3a-second-model");

            writeFactoryConfig("OPEN_AI", "inc3a-second-model", "", MODEL.getUrl());
            MODEL.reset();
            int donesBefore = stream.countOf("done");
            assertThat(send(project.id(), "dritte Frage").getStatusCode())
                    .as("an incomplete config is still accepted — the answer to it is a live line")
                    .isEqualTo(HttpStatus.ACCEPTED);

            stream.awaitEvent("error", 1);
            assertThat(stream.payloads("error").get(0))
                    .as("and the live line names the missing field")
                    .contains("apiKey");
            assertThat(MODEL.getLastRequestBody())
                    .as("nothing was sent to a provider that would have failed on its own")
                    .isNull();
            assertThat(MODEL.getCapturedMessages()).isEmpty();
            stream.assertNoEventWithin("done", donesBefore, 500);

            writeFactoryConfig("OLLAMA", "inc3a-second-model", "", MODEL.rootUrl());
            assertThatNoException().as("a provider that needs no key passes the same check")
                    .isThrownBy(() -> config.loadForTurn());

            MODEL.queueResponse("vierte Antwort");
            send(project.id(), "vierte Frage");
            stream.awaitEvent("done", 3);

            assertThat(modelOfLastRequest())
                    .as("and the turn really went out to that provider")
                    .isEqualTo("inc3a-second-model");
        }
    }

    // ------------------------------------------------------------------
    // the three edges
    // ------------------------------------------------------------------

    private String messagesPath(String projectId) {
        return "/api/projects/" + projectId + "/messages";
    }

    private String eventsPath(String projectId) {
        return "/api/projects/" + projectId + "/events";
    }

    private ResponseEntity<String> send(String projectId, String text) {
        return http.postForEntity("http://127.0.0.1:" + port + messagesPath(projectId),
                jsonEntity(new SendMessageRequest(text)), String.class);
    }

    private HttpStatusCode statusOf(HttpMethod method, String path, Object body) {
        return http.exchange("http://127.0.0.1:" + port + path, method,
                body == null ? HttpEntity.EMPTY : jsonEntity(body), String.class).getStatusCode();
    }

    private HttpEntity<Object> jsonEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private List<ChatMessageDto> messages(String projectId) {
        return http.exchange("http://127.0.0.1:" + port + messagesPath(projectId), HttpMethod.GET, HttpEntity.EMPTY,
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

    /** The model the provider was told to run — read from the request, not from a config object. */
    private static String modelOfLastRequest() {
        return JsonPath.read(MODEL.getLastRequestBody(), "$.model");
    }

    private static List<String> toolResults(List<ChatMessage> captured) {
        return captured.stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(message -> ((ToolExecutionResultMessage) message).text())
                .toList();
    }

    private static AiMessage toolCall(String id, String toolName, String arguments) {
        return new AiMessage(null,
                List.of(ToolExecutionRequest.builder().id(id).name(toolName).arguments(arguments).build()));
    }

    /**
     * A turn reads the factory's own file, so every test writes that file — the same file the settings
     * view writes. Never {@code ~/.peon}.
     */
    private static void writeFactoryConfig(String providerType, String model, String apiKey, String url)
            throws IOException {
        Properties properties = new Properties();
        properties.setProperty(LlmConfigKeys.PROVIDER_TYPE, providerType);
        properties.setProperty(LlmConfigKeys.MODEL, model);
        properties.setProperty(LlmConfigKeys.URL, url);
        properties.setProperty(LlmConfigKeys.API_KEY, apiKey);
        try (var out = Files.newOutputStream(home.resolve(FactoryHome.CONFIG_FILE))) {
            properties.store(out, "ChatTurnTest");
        }
    }

    private record CreatedProject(String id, Path localPath) {
    }

    /**
     * One live connection. It reads with the JDK client (not {@code HttpURLConnection} — its
     * {@code disconnect()} deadlocks against a reader blocked in the same stream lock), records
     * {@code event:}/{@code data:} pairs and waits on a deadline, so a missing event is a red test and
     * never a hanging one.
     */
    private final class ChatStream implements Flow.Subscriber<String>, AutoCloseable {

        private final List<String> names = new CopyOnWriteArrayList<>();
        private final List<String> payloads = new CopyOnWriteArrayList<>();
        private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        private final CompletableFuture<?> exchange;
        private volatile String event = "message";
        private volatile int status;
        private volatile String contentType;
        private volatile java.net.http.HttpHeaders headers;
        private volatile String failure;

        ChatStream(String projectId) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + eventsPath(projectId)))
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
            status = response.statusCode();
            headers = response.headers();
            contentType = response.headers().firstValue("content-type").orElse("");
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
                payloads.add(line.substring("data:".length()).trim());
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

        void awaitEvent(String name, int count) {
            await(() -> countOf(name) >= count, "event " + name + " x" + count + " on the stream");
        }

        /** A negative assertion needs a grace window — this is the only fixed wait in this class. */
        void assertNoEventWithin(String name, int baseline, long millis) {
            long until = System.currentTimeMillis() + millis;
            while (System.currentTimeMillis() < until && countOf(name) == 0) {
                sleep();
            }
            assertThat(countOf(name)).as("%s must not follow the live error", name).isEqualTo(baseline);
        }

        int countOf(String name) {
            return (int) names.stream().filter(name::equals).count();
        }

        List<String> names() {
            return List.copyOf(names);
        }

        List<String> payloads(String name) {
            List<String> result = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                if (name.equals(names.get(i))) {
                    result.add(payloads.get(i));
                }
            }
            return result;
        }

        /** The {@code answer} chunks concatenated — what the user reads, in order. */
        String answerText() {
            StringBuilder text = new StringBuilder();
            for (String payload : payloads("partial")) {
                if ("answer".equals(JsonPath.read(payload, "$.kind"))) {
                    text.append((String) JsonPath.read(payload, "$.text"));
                }
            }
            return text.toString();
        }

        int status() {
            return status;
        }

        String header(String name) {
            return headers == null ? null : headers.firstValue(name).orElse(null);
        }

        String contentType() {
            return contentType;
        }

        private void await(BooleanSupplier condition, String what) {
            long until = System.currentTimeMillis() + 30_000;
            while (!condition.getAsBoolean()) {
                assertThat(System.currentTimeMillis())
                        .as("%s arrived within the deadline (connection: %s)", what, failure)
                        .isLessThan(until);
                sleep();
            }
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

    private static void sleep() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the turn", interrupted);
        }
    }
}
