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
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.project.FakeGitConfig;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The final answer of a team member is a line of its own (UC-WP-40, R-WP-4): it reaches the browser live
 * as a {@code delegated} event carrying the member's name, and the very same line is in the history
 * afterwards — read out of the one file the core already writes, not from a second store.
 *
 * <p>Nothing below {@code AiPoAgent} is stubbed. The delegation is a real {@code askDev} turn of the real
 * {@code AiDevAgent} against a scripted answer double, so the member's text here is the text the core
 * really handed back to Jon: the assertions show that the wire and the history carry the <b>pure</b>
 * answer and neither the status line the model additionally reads nor the member's own thinking.</p>
 *
 * <p>The author is always asserted as a field ({@code origin}), never as text inside a line — a name in
 * the text would prove that somebody guessed (R-WP-4 "niemand rät die Herkunft"). The reading half over a
 * real restart is {@link DelegatedAnswerRestartTest}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakeGitConfig.class)
@Timeout(120)
class DelegatedAnswerTest {

    /** The prompt Jon hands over — visible on his own delegation line. */
    private static final String MEMBER_PROMPT = "MITGLIEDS-AUFGABE: nenne genau eine Frucht";
    /** Only the member's own shell line can carry this; it must stay off the wire. */
    private static final String MEMBER_COMMAND_MARKER = "mitglieds-kommando";
    /** What the member hands back — the final answer, and the only member text allowed to show. */
    private static final String MEMBER_ANSWER = "MITGLIEDS-ANTWORT: Birne";
    /** What the member "answers" when it answers nothing at all — blank is no answer (no line, no event). */
    private static final String MEMBER_NO_ANSWER = "   ";
    /** Jon's own follow-up answer — it stays his line, after the team's. */
    private static final String JON_ANSWER = "JONS-FOLGE-ANTWORT: die Birne";
    private static final String QUESTION = "frag Da Mek nach einer Frucht";

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
        writeFactoryConfig("inc3f3-live");
        CreatedProject project = createProject("delegated-answer");

        try (LiveStream stream = new LiveStream(project.id())) {
            scriptDelegation();
            assertThat(send(project.id(), QUESTION).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            stream.awaitEvent("done", 1);

            List<String> delegated = stream.payloads("delegated");
            assertThat(delegated).as("one delegation is one team line").hasSize(1);
            assertThat((String) JsonPath.read(delegated.get(0), "$.origin"))
                    .as("the team line names the member, in the factory's own words")
                    .isEqualTo("Da Mek");
            assertThat((String) JsonPath.read(delegated.get(0), "$.text"))
                    .as("the pure answer — no status line, no Context: tail, nothing added")
                    .isEqualTo(MEMBER_ANSWER);
            assertThat(stream.indexOfEvent("delegated"))
                    .as("it arrives while the turn is still running — before Jon's own closing tool line")
                    .isLessThan(stream.indexOfToolLine("Da Mek done."));

            assertThat(stream.partialsBetween("Da Mek start:", "Da Mek done."))
                    .as("the member still thinks to itself — the team line did not reopen that")
                    .isZero();
            assertThat(stream.wireText())
                    .as("its tool line stays invisible: only the final answer is a line (R-WP-4)")
                    .doesNotContain(MEMBER_COMMAND_MARKER);
            assertThat(toolResultTexts())
                    .as("the member really ran its own tool — what is filtered did happen")
                    .anySatisfy(result -> assertThat(result).contains(MEMBER_COMMAND_MARKER));
            assertThat(stream.answerText())
                    .as("Jon's own answer still grows live on the same wire")
                    .isEqualTo(JON_ANSWER);
            assertThat(stream.countOf("done"))
                    .as("and it stays one done for the whole turn — the team line is not a second answer")
                    .isEqualTo(1);
            assertThat(stream.countOf("error")).isZero();
        }

        assertThat(messages(project.id()))
                .as("after the reload the same team line stands in the history: role jon, origin Da Mek,"
                        + " the identical pure text, and Jon's own answer behind it")
                .containsExactly(
                        new ChatMessageDto("user", QUESTION, null),
                        new ChatMessageDto("jon", MEMBER_ANSWER, "Da Mek"),
                        new ChatMessageDto("jon", JON_ANSWER, "Jon"));
    }

    @Test
    @Timeout(60)
    void delegationWithoutAnswerProducesNoLine() throws Exception {
        writeFactoryConfig("inc3f3-noanswer");
        CreatedProject project = createProject("delegated-empty");

        try (LiveStream stream = new LiveStream(project.id())) {
            scriptDelegationWithoutAnAnswer();
            send(project.id(), QUESTION);
            stream.awaitEvent("done", 1);

            assertThat(stream.countOf("delegated"))
                    .as("a member that answered nothing has no final answer to show")
                    .isZero();
            assertThat(toolResultTexts())
                    .as("the member did run, and it came back empty — the tool result carries no attributes")
                    .anySatisfy(result -> assertThat(result).contains("returned no result"));
            List<String> toolLines = stream.payloads("tool");
            assertThat(toolLines)
                    .as("Jon keeps his two delegation lines — the failure stays visible to him")
                    .hasSize(2);
            assertThat(toolLines.get(0)).contains("Da Mek start:");
            assertThat(toolLines.get(1)).contains("Da Mek done.");
        }

        assertThat(messages(project.id()))
                .as("no line carries the member's name — and no half-invented line takes its place")
                .doesNotHaveDuplicates();
        assertThat(messages(project.id()))
                .containsExactly(
                        new ChatMessageDto("user", QUESTION, null),
                        new ChatMessageDto("jon", JON_ANSWER, "Jon"));
    }

    @Test
    @Timeout(60)
    void userAndJonLinesCarryTheirOrigin() throws Exception {
        writeFactoryConfig("inc3f3-origins");
        CreatedProject project = createProject("delegated-origins");

        try (LiveStream stream = new LiveStream(project.id())) {
            model.queueResponse(JON_ANSWER);
            send(project.id(), QUESTION);
            stream.awaitEvent("done", 1);
            assertThat(stream.countOf("delegated"))
                    .as("a turn without a delegation produces no team line")
                    .isZero();
        }

        assertThat(messages(project.id()))
                .as("Jon's line names him, the typed input names nobody (R-WP-4, R-WP-17)")
                .containsExactly(
                        new ChatMessageDto("user", QUESTION, null),
                        new ChatMessageDto("jon", JON_ANSWER, "Jon"));
    }

    // ------------------------------------------------------------------
    // the scripted delegation — Jon calls askDev, the member answers, Jon answers the user. All of it
    // goes to the same double: the factory's file carries no llm.agent.* key, so every agent runs on
    // the base model.
    // ------------------------------------------------------------------

    /** The member works with a tool of its own — the line that must stay off the wire while it works. */
    private void scriptDelegation() {
        model.queueResponse(toolCall("call_dev", "askDev", "{\"prompt\":\"" + MEMBER_PROMPT + "\"}"));
        model.queueResponse(toolCall("call_shell", "shellRunCommand",
                "{\"command\":\"echo " + MEMBER_COMMAND_MARKER + "\",\"timeout\":20}"));
        model.queueResponse(MEMBER_ANSWER);
        model.queueResponse(JON_ANSWER);
    }

    /** The member ends without an answer — blank is no answer, so it is no line either. */
    private void scriptDelegationWithoutAnAnswer() {
        model.queueResponse(toolCall("call_dev", "askDev", "{\"prompt\":\"" + MEMBER_PROMPT + "\"}"));
        model.queueResponse(MEMBER_NO_ANSWER);
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
    // the edges
    // ------------------------------------------------------------------

    private ResponseEntity<String> send(String projectId, String text) {
        return http.postForEntity("http://127.0.0.1:" + port + "/api/projects/" + projectId + "/messages",
                jsonEntity(new SendMessageRequest(text)), String.class);
    }

    private List<ChatMessageDto> messages(String projectId) {
        return http.exchange("http://127.0.0.1:" + port + "/api/projects/" + projectId + "/messages",
                HttpMethod.GET, null, new ParameterizedTypeReference<List<ChatMessageDto>>() {
                }).getBody();
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
            properties.store(out, "DelegatedAnswerTest");
        }
    }

    private record CreatedProject(String id, Path localPath) {
    }

    /**
     * One live connection in arrival order (JDK client, deadline waits — see {@code TeamInternalsTest}).
     * This class asserts both contents and order, so names and payloads keep the order they arrived in.
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

        /** Every data line of every event, concatenated — the haystack nothing of the member may be in. */
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

        /** Position of the first event of that name — the order assertions read their anchors here. */
        int indexOfEvent(String name) {
            int index = names.indexOf(name);
            if (index < 0) {
                throw new AssertionError("no event '" + name + "' on the stream — got " + names);
            }
            return index;
        }

        int indexOfToolLine(String marker) {
            for (int i = 0; i < names.size(); i++) {
                if ("tool".equals(names.get(i)) && payloads.get(i).contains(marker)) {
                    return i;
                }
            }
            throw new AssertionError("no tool line carrying '" + marker + "' on the stream — got "
                    + payloads("tool"));
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
