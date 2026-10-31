package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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

import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.project.FakeGitConfig;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The final answer of a team member is a line of its own (UC-WP-40, R-WP-4), and it is a <b>history</b>
 * line: the live wire carries Jon's own answer and his delegation trace, and the member's answer appears
 * with the read that {@code done} causes — read out of the one file the core already writes, not from a
 * second store and not from an event of its own (ADR-0078 Decision 6).
 *
 * <p>Nothing below {@code AiPoAgent} is stubbed. The delegation is a real {@code askDev} turn of the real
 * {@code AiDevAgent} against a scripted answer double, so the member's text here is the text the core
 * really handed back to Jon. The assertions show that the history carries that result <b>verbatim</b> —
 * answer and statistics line, exactly what the model reads — and that the member's thinking and its own
 * tool run stay off the wire.</p>
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
    /** What the member hands back — a member text: no live line shows it, the history names it. */
    private static final String MEMBER_ANSWER = "MITGLIEDS-ANTWORT: Birne";
    /** Two delegations of one turn answer differently — each answer is a line of its own. */
    private static final String MEMBER_ANSWER_FIRST = "MITGLIEDS-ANTWORT-ERST: Birne";
    private static final String MEMBER_ANSWER_SECOND = "MITGLIEDS-ANTWORT-ZWEIT: Banane";
    /** What the member "answers" when it answers nothing at all — still its result, still its line. */
    private static final String MEMBER_NO_ANSWER = "   ";
    /** Jon's own follow-up answer — it stays his line, after the team's. */
    private static final String JON_ANSWER = "JONS-FOLGE-ANTWORT: die Birne";
    private static final String QUESTION = "frag Dev nach einer Frucht";

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

            assertThat(stream.wireText())
                    .as("while the turn runs, no line of the wire carries the member's answer — the line"
                            + " with its name arrives with the history, not as an event")
                    .doesNotContain(MEMBER_ANSWER);
            assertThat(stream.partialsBetween("Dev start:", "Dev done."))
                    .as("the member still thinks to itself — nothing reopened that")
                    .isZero();
            assertThat(stream.wireText())
                    .as("its tool line stays invisible: only its result becomes a line (R-WP-4)")
                    .doesNotContain(MEMBER_COMMAND_MARKER);
            assertThat(stream.payloads("tool").get(0))
                    .as("of Jon's own two delegation lines, the start line names the member's task")
                    .contains(MEMBER_PROMPT);
            assertThat(toolResultTexts())
                    .as("the member really ran its own tool — what is filtered did happen")
                    .anySatisfy(result -> assertThat(result).contains(MEMBER_COMMAND_MARKER));
            assertThat(stream.answerText())
                    .as("Jon's own answer still grows live on the same wire")
                    .isEqualTo(JON_ANSWER);
            assertThat(stream.eventNames())
                    .as("the wire speaks exactly the three known names — a member line leaking under a"
                            + " fourth name is red here, not merely unnamed")
                    .containsOnly("partial", "tool", "done");
            assertThat(stream.countOf("done"))
                    .as("and it stays one done for the whole turn — the team's line is not a second answer")
                    .isEqualTo(1);
            assertThat(stream.countOf("error")).isZero();

            List<ChatMessageDto> history = messages(project.id());
            assertThat(history)
                    .as("with the read that done causes, the member's line stands in the history: its own"
                            + " name as author, Jon's answer behind it, the typed input nameless")
                    .extracting(ChatMessageDto::role, ChatMessageDto::origin)
                    .containsExactly(
                            tuple("user", null),
                            tuple("jon", "Dev"),
                            tuple("jon", AiPoAgent.NAME));
            assertThat(history.get(1).text())
                    .as("the tool result verbatim — answer and statistics line, nothing shortened")
                    .startsWith(MEMBER_ANSWER + System.lineSeparator())
                    .contains("Dev done.");
        }
    }

    /**
     * The identity of a member is the core's work, never a line of the shared static context: the request
     * the delegated member sends names the member and speaks as nobody else. Internal, no own id — it
     * guards the one rule the delegation rests on, that a member answers as itself.
     */
    @Test
    @Timeout(60)
    void devTurnNeverCarriesJonsIdentityLine() throws Exception {
        writeFactoryConfig("inc1-identity");
        CreatedProject project = createProject("delegated-identity");

        try (LiveStream stream = new LiveStream(project.id())) {
            scriptDelegation();
            assertThat(send(project.id(), QUESTION).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            stream.awaitEvent("done", 1);
        }

        List<String> devRequests = model.getRequestBodies().stream()
                .filter(body -> body.contains("Your name is " + AiDevAgent.NAME))
                .toList();
        assertThat(devRequests)
                .as("the delegation really ran as its own request, carrying its own name")
                .isNotEmpty();
        assertThat(devRequests)
                .as("a member's request never carries another agent's identity — the shared static"
                        + " context holds no name but the member's own")
                .allSatisfy(body -> assertThat(body).doesNotContain("Your name is " + AiPoAgent.NAME + "."));
    }

    /**
     * Two delegations of one turn are two lines (internal, no own id — it sharpens UC-WP-40's rule): the
     * final answer is a fact of one delegation call, not „the last answer a member gave". A second
     * delegation must therefore neither overwrite the first answer in the history nor lose it, and the
     * reading needs no book over which call was which: two delegations are two tool results, each with
     * its own tool name.
     */
    @Test
    @Timeout(60)
    void twoDelegationsInOneTurnProduceOneLineEach() throws Exception {
        writeFactoryConfig("inc3f5-twice");
        CreatedProject project = createProject("delegated-twice");

        try (LiveStream stream = new LiveStream(project.id())) {
            scriptTwoDelegations();
            assertThat(send(project.id(), QUESTION).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            stream.awaitEvent("done", 1);

            List<String> toolLines = stream.payloads("tool");
            assertThat(toolLines)
                    .as("Jon's trace grows with the delegations — one start line and one done line each")
                    .hasSize(4);
            assertThat(toolLines.get(0)).contains("Dev start:").contains("(erste)");
            assertThat(toolLines.get(1)).contains("Dev done.");
            assertThat(toolLines.get(2)).contains("Dev start:").contains("(zweite)");
            assertThat(toolLines.get(3)).contains("Dev done.");
            assertThat(stream.partialsBetween("(erste)", "Dev done."))
                    .as("the first member still thought to itself — a second delegation changes nothing")
                    .isZero();
            assertThat(stream.answerText())
                    .as("Jon's own answer stays one answer over two delegations")
                    .isEqualTo(JON_ANSWER);
            assertThat(stream.countOf("done"))
                    .as("two delegations stay one turn, so they stay one done")
                    .isEqualTo(1);
            assertThat(stream.countOf("error")).isZero();
        }

        List<ChatMessageDto> history = messages(project.id());
        assertThat(history)
                .as("the history holds both team lines in the order of Jon's memory, his own answer last")
                .extracting(ChatMessageDto::role, ChatMessageDto::origin)
                .containsExactly(
                        tuple("user", null),
                        tuple("jon", "Dev"),
                        tuple("jon", "Dev"),
                        tuple("jon", AiPoAgent.NAME));
        assertThat(history.get(1).text())
                .as("the first answer kept its own line — the second did not take it over")
                .startsWith(MEMBER_ANSWER_FIRST + System.lineSeparator());
        assertThat(history.get(2).text())
                .as("and the second is its own line, not a repeat of the first")
                .startsWith(MEMBER_ANSWER_SECOND + System.lineSeparator());
    }

    @Test
    @Timeout(60)
    // UC-WP-40
    void delegationWithoutAnswerStillProducesALine() throws Exception {
        writeFactoryConfig("inc3f3-noanswer");
        CreatedProject project = createProject("delegated-empty");

        try (LiveStream stream = new LiveStream(project.id())) {
            scriptDelegationWithoutAnAnswer();
            send(project.id(), QUESTION);
            stream.awaitEvent("done", 1);

            assertThat(toolResultTexts())
                    .as("the member did run, and it came back empty — that result is what the line reads")
                    .anySatisfy(result -> assertThat(result).contains("returned no result"));
            List<String> toolLines = stream.payloads("tool");
            assertThat(toolLines)
                    .as("Jon keeps his two delegation lines — the failure stays visible to him")
                    .hasSize(2);
            assertThat(toolLines.get(0)).contains("Dev start:");
            assertThat(toolLines.get(1)).contains("Dev done.");
        }

        List<ChatMessageDto> history = messages(project.id());
        assertThat(history)
                .as("the empty result is a line with an author like any other — an author is a fact, not"
                        + " a verdict over the text (UC-WP-40)")
                .extracting(ChatMessageDto::origin)
                .containsExactly(null, "Dev", AiPoAgent.NAME);
        assertThat(history.get(1).text())
                .as("and it says so in the member's own words, not in a reworded form")
                .startsWith("Dev returned no result");
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

    /**
     * Jon asks for two delegations in one answer of his own. Both calls name the same tool, so the same
     * member runs twice one after the other — its second answer is what a „last answer of a member" rule
     * alone would show twice, and the first answer would vanish.
     */
    private void scriptTwoDelegations() {
        model.queueResponse(new AiMessage(null, List.of(
                ToolExecutionRequest.builder().id("call_dev_first").name("askDev")
                        .arguments("{\"prompt\":\"" + MEMBER_PROMPT + " (erste)\"}").build(),
                ToolExecutionRequest.builder().id("call_dev_second").name("askDev")
                        .arguments("{\"prompt\":\"" + MEMBER_PROMPT + " (zweite)\"}").build())));
        model.queueResponse(MEMBER_ANSWER_FIRST);
        model.queueResponse(MEMBER_ANSWER_SECOND);
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

        /** Every {@code event:} name seen, in arrival order — duplicates kept, the name set is asserted. */
        List<String> eventNames() {
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
