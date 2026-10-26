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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.project.FakeGitConfig;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * What the live wire is allowed to show while Jon delegates (R-WP-4, UC-WP-40): his own lines and his
 * delegation trace, and nothing a team member spoke on its own way. Nothing below {@code AiPoAgent} is
 * stubbed — the delegation is a real {@code askDev} tool turn of the real {@code AiDevAgent}, and the
 * member's own shell run plus its answer are proven in the messages the double captured. So every
 * absence asserted here is an absence of something that demonstrably happened.
 *
 * <p>The negative assertions need no grace window: a turn publishes everything it produces — a leak
 * included — before the outer agent's own {@code done}, so waiting for that one event is a
 * deterministic anchor, and the member finishes strictly before Jon anyway.</p>
 *
 * <p>What is <b>deliberately still missing</b> after this increment: the member's <em>final</em> answer
 * is nowhere on the wire either. That gap is documented, not hidden — it is closed by the
 * {@code delegated} event and the history reading of increment 3f-3.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakeGitConfig.class)
@Timeout(120)
class TeamInternalsTest {

    /** The prompt Jon hands over — it has to stay visible, it names the delegation on his own line. */
    private static final String MEMBER_PROMPT = "MITGLIEDS-AUFGABE: nenne genau eine Frucht";
    /** Only the member's own shell line can carry this; no line of Jon's ever mentions it. */
    private static final String MEMBER_COMMAND_MARKER = "mitglieds-kommando";
    /** What the member hands back — a member text, so it may not appear on the wire. */
    private static final String MEMBER_ANSWER = "MITGLIEDS-ANTWORT: Birne";
    /** Jon's own follow-up answer — it has to arrive live, chunk by chunk. */
    private static final String JON_ANSWER = "JONS-FOLGE-ANTWORT: die Birne";

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

    private RestTemplate http;

    @AfterAll
    static void stopStreams() {
        // close(), not shutdownNow(): only close() releases the client's selector thread
        STREAMS.close();
    }

    @BeforeEach
    void startModelAndClient() {
        // fresh per test: an orphaned turn of a failed test may not drain the next one's script
        this.model = new MockLlmServer(0);
        this.model.start();
        this.http = new RestTemplate();
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
    // UC-WP-40
    void onlyFinalAnswersFromTheTeam() throws Exception {
        writeFactoryConfig("inc3f2-internals");
        CreatedProject project = createProject("team-internals");

        try (LiveStream stream = new LiveStream(project.id())) {
            scriptDelegation(true);

            assertThat(send(project.id(), "frag Da Mek nach einer Frucht").getStatusCode())
                    .as("the composer's send stays a send — filtering changes nothing about the turn")
                    .isEqualTo(HttpStatus.ACCEPTED);
            stream.awaitEvent("done", 1);

            List<String> toolResults = toolResultTexts();
            assertThat(toolResults)
                    .as("the member really worked: its own tool ran and its answer reached Jon — what the "
                            + "wire does not show is something that happened, not something that never did")
                    .anySatisfy(result -> assertThat(result).contains(MEMBER_COMMAND_MARKER))
                    .anySatisfy(result -> assertThat(result).contains(MEMBER_ANSWER));

            assertThat(stream.wireText())
                    .as("neither the member's thinking, nor its tool line, nor its answer (R-WP-4)")
                    .doesNotContain(MEMBER_ANSWER)
                    .doesNotContain(MEMBER_COMMAND_MARKER);
            assertThat(stream.partialsBetween("Da Mek start:", "Da Mek done."))
                    .as("while the member works, the wire is silent — its conversation with itself")
                    .isZero();
            assertThat(stream.answerText())
                    .as("Jon's own answer still grows live, thinking included (UC-WP-7)")
                    .isEqualTo(JON_ANSWER);

            List<String> toolLines = stream.payloads("tool");
            assertThat(toolLines)
                    .as("the member's tool line is gone; Jon keeps his own two delegation lines")
                    .hasSize(2);
            assertThat(toolLines.get(0)).contains("Da Mek start:").contains(MEMBER_PROMPT);
            assertThat(toolLines.get(1)).contains("Da Mek done.");
            assertThat(stream.countOf("done"))
                    .as("one answered input is one done")
                    .isEqualTo(1);
            assertThat(stream.countOf("error")).isZero();
        }
    }

    @Test
    @Timeout(60)
    void memberCompletionSendsNoDone() throws Exception {
        writeFactoryConfig("inc3f2-done");
        CreatedProject project = createProject("member-done");

        try (LiveStream stream = new LiveStream(project.id())) {
            scriptDelegation(false);
            send(project.id(), "frag Da Mek nach einer Frucht");
            stream.awaitEvent("done", 1);

            assertThat(toolResultTexts())
                    .as("the member finished — its end is what used to send a done of its own (F1)")
                    .anySatisfy(result -> assertThat(result).contains(MEMBER_ANSWER));
            assertThat(stream.countOf("done"))
                    .as("though two agents completed, the browser reloads its history exactly once")
                    .isEqualTo(1);
            assertThat(stream.answerText())
                    .as("and the done that is there is Jon's own: his answer was complete when it arrived")
                    .isEqualTo(JON_ANSWER);
        }
    }

    // ------------------------------------------------------------------
    // the scripted delegation
    // ------------------------------------------------------------------

    /**
     * Jon calls {@code askDev}, the member answers — optionally after a tool call of its own, which is
     * what produces a member tool line to be filtered — then Jon answers the user. All of it goes to the
     * same double: the factory's file carries no {@code llm.agent.*} key, so every agent runs on the
     * base model.
     */
    private void scriptDelegation(boolean memberUsesATool) {
        model.queueResponse(toolCall("call_dev", "askDev", "{\"prompt\":\"" + MEMBER_PROMPT + "\"}"));
        if (memberUsesATool) {
            model.queueResponse(toolCall("call_shell", "shellRunCommand",
                    "{\"command\":\"echo " + MEMBER_COMMAND_MARKER + "\",\"timeout\":20}"));
        }
        model.queueResponse(MEMBER_ANSWER);
        model.queueResponse(JON_ANSWER);
    }

    private static AiMessage toolCall(String id, String toolName, String arguments) {
        return new AiMessage(null,
                List.of(ToolExecutionRequest.builder().id(id).name(toolName).arguments(arguments).build()));
    }

    /** Every tool result the agents handed back, in the order the double saw them. */
    private List<String> toolResultTexts() {
        List<String> texts = new ArrayList<>();
        model.getCapturedMessages().stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(message -> ((ToolExecutionResultMessage) message).text())
                .forEach(texts::add);
        return texts;
    }

    // ------------------------------------------------------------------
    // the two edges
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

    private CreatedProject createProject(String slug) {
        ResponseEntity<String> created = http.postForEntity("http://127.0.0.1:" + port + "/api/projects",
                jsonEntity(Map.of("gitUrl", "https://git.example.com/acme/" + slug + ".git")), String.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String localPath = JsonPath.read(created.getBody(), "$.localPath");
        return new CreatedProject(JsonPath.read(created.getBody(), "$.id"), Path.of(localPath));
    }

    /** The factory's own file — the same one the settings view writes. Never {@code ~/.peon}. */
    private void writeFactoryConfig(String model) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(LlmConfigKeys.PROVIDER_TYPE, "OPEN_AI");
        properties.setProperty(LlmConfigKeys.MODEL, model);
        properties.setProperty(LlmConfigKeys.URL, this.model.getUrl());
        properties.setProperty(LlmConfigKeys.API_KEY, "test-key");
        try (var out = Files.newOutputStream(home.resolve(FactoryHome.CONFIG_FILE))) {
            properties.store(out, "TeamInternalsTest");
        }
    }

    private record CreatedProject(String id, Path localPath) {
    }

    /**
     * One live connection in arrival order (JDK client, deadline waits — see {@code ChatTurnTest}).
     * Order is what this class asserts, so names and payloads are kept in the lists they arrived in.
     */
    private final class LiveStream implements Flow.Subscriber<String>, AutoCloseable {

        private final List<String> names = new CopyOnWriteArrayList<>();
        private final List<String> payloads = new CopyOnWriteArrayList<>();
        private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        private final CompletableFuture<?> exchange;
        private volatile String event = "message";
        private volatile String failure;

        LiveStream(String projectId) {
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
            long until = System.currentTimeMillis() + 30_000;
            while (countOf(name) < count) {
                assertThat(System.currentTimeMillis())
                        .as("event %s x%d arrived on the stream (connection: %s)", name, count, failure)
                        .isLessThan(until);
                sleep();
            }
        }

        int countOf(String name) {
            return (int) names.stream().filter(name::equals).count();
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

        /** Every data line of every event, concatenated — the haystack the member's texts must not be in. */
        String wireText() {
            return String.join("|", payloads);
        }

        /** The {@code answer} chunks in order — what the user reads. */
        String answerText() {
            StringBuilder text = new StringBuilder();
            for (String payload : payloads("partial")) {
                if ("answer".equals(JsonPath.read(payload, "$.kind"))) {
                    text.append((String) JsonPath.read(payload, "$.text"));
                }
            }
            return text.toString();
        }

        /** The streamed chunks that arrived between two tool lines of this very turn. */
        int partialsBetween(String firstToolLine, String secondToolLine) {
            int from = indexOfToolLine(firstToolLine);
            int to = indexOfToolLine(secondToolLine);
            int partials = 0;
            for (int i = from + 1; i < to; i++) {
                if ("partial".equals(names.get(i))) {
                    partials++;
                }
            }
            return partials;
        }

        private int indexOfToolLine(String marker) {
            for (int i = 0; i < names.size(); i++) {
                if ("tool".equals(names.get(i)) && payloads.get(i).contains(marker)) {
                    return i;
                }
            }
            throw new AssertionError("no tool line carrying '" + marker + "' on the stream — got " + payloads("tool"));
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
            throw new IllegalStateException("interrupted while waiting for the live stream", interrupted);
        }
    }
}
