package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;

import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.open.aifactory.FactorySpringTest;
import org.sterl.open.aifactory.chat.dto.AgentTileDto;
import org.sterl.open.aifactory.chat.dto.AgentTileState;
import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.project.Project;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * Stop over the real edge (UC-WP-50): one flag on the project's one monitor ends the whole turn — Jon
 * <b>and</b> a delegation that is running inside him — and it does so as an end, not as an error.
 *
 * <p>Nothing below {@code AiPoAgent} is stubbed. A turn is held by a real {@link TurnGate} shell tool, the
 * pattern of {@code ChatQueueTest}, and every wait is a deadline on the <b>end</b> state. The core's own
 * boundary is respected and never asserted away: it reads the cancel <b>between</b> its model calls, so a
 * tool that is already running runs to its end — which is exactly why the gate stays shut until the stop
 * has landed, and why the test keeps a generous deadline instead of a claim about the middle of a
 * stream.</p>
 *
 * <p>What is proven here is the turn, not the classification of an exception: a stop that lands while the
 * model is mid-stream cannot be produced on demand (the mock writes a streamed answer in one piece), so the
 * cancel-is-no-error rule is pinned purely in {@code TurnOutcomeTest} and its use in the turn is proven by
 * the missing {@code error} event below.</p>
 */
@Timeout(180)
class AgentStopTest extends FactorySpringTest {

    /** Holds the turn inside a running tool until the test releases it — the stop has to land in there. */
    private static final String GATE_TOOL = TurnGate.toolArguments();
    private static final String SLOW_INPUT = "LAUF: Der Turn hält das Werkzeug";
    private static final String MEMBER_PROMPT = "HALTE: Dev hält das Werkzeug";
    private static final String QUEUED_INPUT = "WARTENDE-ZWEITFRAGE";

    /**
     * HTTP/1.1 on purpose — the SSE edge is what the browser talks (see {@code ChatTurnTest}).
     */
    private static final HttpClient STREAMS = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Autowired
    private ProjectRuntimeRegistry runtimes;

    @LocalServerPort
    private int port;

    private MockLlmServer model;
    private RestTemplate http;

    /** The body of the last stop — a bare status would leave a wrong one unreadable. */
    private String lastStopBody;

    @AfterAll
    static void stopStreams() {
        // close(), not shutdownNow(): only close() releases the client's selector thread
        STREAMS.close();
    }

    @BeforeEach
    void startModelAndClient() throws IOException {
        this.model = new MockLlmServer(0);
        this.model.start();
        this.http = new RestTemplate();
        // the status IS the assertion — a throwing client would hide what the app answers
        this.http.setErrorHandler(new NoOpResponseErrorHandler());
        writeFactoryConfig();
    }

    @AfterEach
    void stopModel() {
        if (model != null) {
            model.stop();
        }
    }

    @Test
    @Timeout(90)
    // UC-WP-50
    void stopEndsTheTurnAndEndsItAsAnEnd() {
        Project project = projects.add("https://git.example.com/acme/stop-turn.git").project();
        ProjectRuntime runtime = runtimes.runtime(project.id());
        try (StopStream wire = new StopStream(project.id())) {
            model.queueResponse(toolCall("call_gate", "shellRunCommand", GATE_TOOL));
            model.queueResponse("DIESE-ANTWORT-DARF-NICHT-MEHR-KOMMEN");
            send(project.id(), SLOW_INPUT);
            awaitUntil(() -> wire.hasToolLine(TurnGate.runningLine()), 20_000,
                    "der laufende Shell meldet seinen Befehl");

            assertThat(stop(project.id()))
                    .as("a stop of a running turn is taken")
                    .isEqualTo(HttpStatus.ACCEPTED.value());
            assertThat(stop(project.id()))
                    .as("and taken again while the tool still drains — the same wish, not a new error")
                    .isEqualTo(HttpStatus.ACCEPTED.value());

            // the gate is what holds the turn open; opening it after the stop is what lets the running
            // tool reach its end, and that end is the only place the core reads the cancel
            TurnGate.release(project.localPath());
            awaitUntil(() -> !runtime.jon().isWorking(), 30_000, "Jons Turn endete nach dem Stop");
            wire.await(() -> wire.countOf("done") >= 1, "das Ende kam auf die Leitung");
            wire.awaitRosterAsLastEvent();

            assertThat(wire.countOf("done"))
                    .as("one end for one turn — the stop starts no second one")
                    .isEqualTo(1);
            assertThat(wire.countOf("error"))
                    .as("a stop is the user's own end and never an error (D-16)")
                    .isZero();
            assertThat(tiles(project.id()))
                    .as("no tile keeps a failure it was never given")
                    .allSatisfy(tile -> assertThat(tile.state())
                            .as("%s after the stop", tile.name())
                            .isEqualTo(AgentTileState.IDLE));
            assertThat(memoryText(runtime.jon()))
                    .as("the answer the model had left was never asked for again — the turn is over, not hidden")
                    .doesNotContain("DIESE-ANTWORT-DARF-NICHT-MEHR-KOMMEN");

            assertThat(stop(project.id()))
                    .as("the same click on the turn that has ended is refused")
                    .isEqualTo(HttpStatus.CONFLICT.value());
        }
    }

    /**
     * The second exit of a cancel, and the only one the factory can be shown it on: the core's patience
     * ({@code ApiRetry}) waits out a backoff between model calls and reads the cancel there, raising a
     * {@link java.util.concurrent.CancellationException} out of {@code call()}. Only this path can prove
     * that such an exception is an end and not an error (D-16) — the tool-loop break of the tests above
     * ends the turn on a plain answer and never reaches that branch.
     */
    @Test
    @Timeout(90)
    // UC-WP-50
    void stopInsideTheRetryWaitEndsTheTurnAsAnEnd() {
        Project project = projects.add("https://git.example.com/acme/stop-retry.git").project();
        ProjectRuntime runtime = runtimes.runtime(project.id());
        try (StopStream wire = new StopStream(project.id())) {
            // the first call earns one credit of patience, the second one finds no server at all
            model.queueResponse(toolCall("call_gate", "shellRunCommand", GATE_TOOL));
            send(project.id(), SLOW_INPUT);
            awaitUntil(() -> wire.hasToolLine(TurnGate.runningLine()), 20_000,
                    "der laufende Shell meldet seinen Befehl");
            model.stop();
            // the answer the tool would come back to is gone: the gate may open, because what the stop has
            // to find now is the wait the retry takes before its next call
            TurnGate.release(project.localPath());

            awaitUntil(() -> wire.hasToolLine("API error — attempt"), 30_000,
                    "die Retry-Geduld meldet den Fehler auf der Leitung");
            assertThat(stop(project.id()))
                    .as("the wait before a retry belongs to the turn, so it is stoppable")
                    .isEqualTo(HttpStatus.ACCEPTED.value());

            awaitUntil(() -> !runtime.jon().isWorking(), 30_000, "Jons Turn endete nach dem Stop");
            // waits for whichever end arrives, so the assertion below is the one that decides which it was
            wire.await(() -> wire.countOf("done") + wire.countOf("error") >= 1,
                    "ein Ende kam auf die Leitung");

            assertThat(wire.countOf("error"))
                    .as("a cancel that leaves the core as an exception is still an end (D-16)")
                    .isZero();
            assertThat(wire.countOf("done"))
                    .as("one end for one turn")
                    .isEqualTo(1);
            assertThat(tile(project.id(), "Jon").state())
                    .as("his tile shows no failure he was never given")
                    .isEqualTo(AgentTileState.IDLE);
        }
    }


    @Test
    @Timeout(90)
    // UC-WP-50
    void stopEndsJonAndTheRunningDelegation() {
        Project project = projects.add("https://git.example.com/acme/stop-delegation.git").project();
        ProjectRuntime runtime = runtimes.runtime(project.id());
        AiAgent dev = member(runtime, AiDevAgent.NAME);
        try (StopStream wire = new StopStream(project.id())) {
            // Jon delegates, Dev holds a shell inside the delegation — one monitor for both of them
            model.queueResponse(toolCall("call_dev", "askDev", "{\"prompt\":\"" + MEMBER_PROMPT + "\"}"));
            model.queueResponse(toolCall("call_dev_sleep", "shellRunCommand", GATE_TOOL));
            model.queueResponse("DEV-ANTWORT-NACH-DEM-STOP");
            model.queueResponse("JON-ANTWORT-NACH-DEM-STOP");
            send(project.id(), "DELEGATION: Frag Dev, während sein Werkzeug läuft");
            awaitUntil(() -> dev.isWorking() && runtime.jon().isWorking(), 25_000,
                    "Dev arbeitet in Jons Turn");

            assertThat(stop(project.id()))
                    .as("the stop is asked of the turn, and Dev runs inside it")
                    .isEqualTo(HttpStatus.ACCEPTED.value());

            // Dev's tool is the innermost thing still running, so releasing it is what ends both turns
            TurnGate.release(project.localPath());
            awaitUntil(() -> !dev.isWorking() && !runtime.jon().isWorking(), 30_000,
                    "auch die laufende Delegation endete — „alles gestopt“");
            wire.await(() -> wire.countOf("done") >= 1, "das Ende kam auf die Leitung");

            assertThat(wire.countOf("error")).isZero();
            assertThat(wire.countOf("done")).isEqualTo(1);
            assertThat(tile(project.id(), AiDevAgent.NAME).state())
                    .as("Dev's tile goes dark with his turn, not with an error")
                    .isEqualTo(AgentTileState.IDLE);
            assertThat(memoryText(runtime.jon()))
                    .as("the delegation was cut short, so his own closing answer never arrived")
                    .doesNotContain("JON-ANTWORT-NACH-DEM-STOP");
        }
    }

    @Test
    @Timeout(90)
    // UC-WP-50
    void turnAfterAStopStartsFresh() {
        Project project = projects.add("https://git.example.com/acme/stop-then-turn.git").project();
        ProjectRuntime runtime = runtimes.runtime(project.id());
        try (StopStream wire = new StopStream(project.id())) {
            model.queueResponse(toolCall("call_gate", "shellRunCommand", GATE_TOOL));
            send(project.id(), SLOW_INPUT);
            awaitUntil(() -> wire.hasToolLine(TurnGate.runningLine()), 20_000, "der laufende Shell meldet seinen Befehl");
            assertThat(stop(project.id()))
                    .as("stop auf den laufenden Turn: %s", lastStopBody)
                    .isEqualTo(HttpStatus.ACCEPTED.value());
            TurnGate.release(project.localPath());
            awaitUntil(() -> !runtime.jon().isWorking(), 30_000, "der gestoppte Turn endete");
            wire.await(() -> wire.countOf("done") >= 1, "das Ende kam auf die Leitung");

            model.queueResponse("ZWEITE-ANTWORT-NACH-DEM-STOP");
            send(project.id(), "ZWEITE FRAGE");
            wire.await(() -> wire.countOf("done") >= 2, "der nächste Turn endete");

            assertThat(messages(project.id()))
                    .as("the next turn answers normally — the flag was reset before it, so no turn is born stopped")
                    .anySatisfy(line -> assertThat(line.text()).contains("ZWEITE-ANTWORT-NACH-DEM-STOP"));
            assertThat(wire.countOf("error")).isZero();
        }
    }

    @Test
    @Timeout(90)
    // UC-WP-50
    void stopWithoutATurnIsRefused() {
        Project project = projects.add("https://git.example.com/acme/stop-idle.git").project();
        ProjectRuntime runtime = runtimes.runtime(project.id());
        try (StopStream wire = new StopStream(project.id())) {
            assertThat(stop(project.id()))
                    .as("nothing runs, so there is nothing to stop — 409 and no side effect")
                    .isEqualTo(HttpStatus.CONFLICT.value());
            wire.assertNoEventWithin("done", 500);
            wire.assertNoEventWithin("error", 500);
            assertThat(runtime.jon().getMemory().getCopy())
                    .as("a refused stop left no trace in the conversation")
                    .isEmpty();
            assertThat(stop("does-not-exist"))
                    .as("an unknown project stays 404 — the stop is no new address space")
                    .isEqualTo(HttpStatus.NOT_FOUND.value());
        }
    }

    /**
     * A stop addresses the turn, never the queue: the send that arrived while Jon worked is the core's
     * business (UC-WP-14), and its task must not free the project — that is what makes this stop 202 and
     * not 409. One turn, one end, and afterwards the conversation is usable again.
     *
     * <p><b>What this class cannot claim</b> (measured here, reported 2026-11-01): a queued input that the
     * running turn has already polled is dropped when the cancel ends that turn
     * ({@code AbstractAgent.call} polls at :318, the loop then leaves at :331 on the cancel and the drain at
     * :334 finds an empty queue), so it is neither answered nor kept for the next request. The factory
     * cannot reach that message, and no assertion here pretends otherwise — chat.md's "gequeuete Eingaben
     * bleiben in der Queue" is open until the core answers it (Paul's decision).</p>
     */
    @Test
    @Timeout(90)
    // UC-WP-50
    void stopWithAQueuedInputStillStopsTheOneTurn() {
        Project project = projects.add("https://git.example.com/acme/stop-queue.git").project();
        ProjectRuntime runtime = runtimes.runtime(project.id());
        try (StopStream wire = new StopStream(project.id())) {
            model.queueResponse(toolCall("call_gate", "shellRunCommand", GATE_TOOL));
            send(project.id(), SLOW_INPUT);
            awaitUntil(() -> wire.hasToolLine(TurnGate.runningLine()), 20_000,
                    "der laufende Shell meldet seinen Befehl");
            // Jon is still working, so this input goes to his queue (UC-WP-14). The gate leaves no
            // three-second window to lean on, so the queue itself is what this test waits for.
            send(project.id(), QUEUED_INPUT);
            awaitUntil(() -> runtime.jon().getQueuedMessageCount() == 1, 2_000,
                    "die zweite Eingabe liegt in der Queue, bevor der Stop kommt");

            assertThat(stop(project.id()))
                    .as("a second send in the queue does not free the project — the turn is still there to stop")
                    .isEqualTo(HttpStatus.ACCEPTED.value());
            TurnGate.release(project.localPath());
            awaitUntil(() -> !runtime.jon().isWorking(), 30_000, "der gestoppte Turn endete");
            wire.await(() -> wire.countOf("done") >= 1, "das Ende kam auf die Leitung");

            assertThat(wire.countOf("done"))
                    .as("one end for the one turn — the queued input started none of its own")
                    .isEqualTo(1);
            assertThat(wire.countOf("error")).isZero();
            assertThat(stop(project.id()))
                    .as("and after the end there is again nothing to stop")
                    .isEqualTo(HttpStatus.CONFLICT.value());

            model.queueResponse("ANTWORT-NACH-DEM-STOP");
            send(project.id(), "FRAGE NACH DEM STOP");
            wire.await(() -> wire.countOf("done") >= 2, "der nächste Turn endete");
            assertThat(messages(project.id()))
                    .as("the conversation is usable again after a stop that found a queued input")
                    .anySatisfy(line -> assertThat(line.text()).contains("ANTWORT-NACH-DEM-STOP"));
        }
    }

    // ------------------------------------------------------------------ the edges

    private int stop(String projectId) {
        ResponseEntity<String> answer = http.postForEntity(url(projectId, "messages/stop"), null,
                String.class);
        lastStopBody = answer.getBody();
        return answer.getStatusCode().value();
    }

    private void send(String projectId, String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> answer = http.postForEntity(url(projectId, "messages"),
                new HttpEntity<>(new SendMessageRequest(text), headers), String.class);
        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    /** The roster as the browser reads it — the same DTOs the SSE snapshot carries. */
    private List<AgentTileDto> tiles(String projectId) {
        ResponseEntity<List<AgentTileDto>> answer = http.exchange(url(projectId, "agents"), HttpMethod.GET,
                null, new ParameterizedTypeReference<List<AgentTileDto>>() {
                });
        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
        return answer.getBody();
    }

    private AgentTileDto tile(String projectId, String name) {
        return tiles(projectId).stream()
                .filter(tile -> name.equals(tile.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no tile named " + name));
    }

    private List<ChatMessageDto> messages(String projectId) {
        return http.exchange(url(projectId, "messages"), HttpMethod.GET, null,
                new ParameterizedTypeReference<List<ChatMessageDto>>() {
                }).getBody();
    }

    private String url(String projectId, String segment) {
        return "http://127.0.0.1:" + port + "/api/projects/" + projectId + "/" + segment;
    }

    // ------------------------------------------------------------------ fixtures

    private static AiMessage toolCall(String id, String toolName, String arguments) {
        return new AiMessage(null,
                List.of(ToolExecutionRequest.builder().id(id).name(toolName).arguments(arguments).build()));
    }

    private AiAgent member(ProjectRuntime runtime, String name) {
        return runtime.jon().getTeam().stream()
                .filter(agent -> name.equals(agent.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no team member named " + name));
    }

    /** Everything the memory says, as one text — what arrived and what never did. */
    private static String memoryText(AiAgent agent) {
        StringBuilder text = new StringBuilder();
        for (ChatMessage message : agent.getMemory().getCopy()) {
            text.append(message).append('\n');
        }
        return text.toString();
    }

    /** The factory's own file — the same one the settings view writes. Never {@code ~/.peon}. */
    private void writeFactoryConfig() throws IOException {
        Properties properties = new Properties();
        properties.setProperty(LlmConfigKeys.PROVIDER_TYPE, "OPEN_AI");
        properties.setProperty(LlmConfigKeys.MODEL, "stop-model");
        properties.setProperty(LlmConfigKeys.URL, model.getUrl());
        properties.setProperty(LlmConfigKeys.API_KEY, "test-key");
        try (var out = Files.newOutputStream(HOME.resolve(FactoryHome.CONFIG_FILE))) {
            properties.store(out, "AgentStopTest");
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

    /**
     * One live connection: reads with the JDK client (not {@code HttpURLConnection}, whose
     * {@code disconnect()} deadlocks against a reader blocked in the same stream lock), records
     * {@code event:}/{@code data:} pairs and waits on a deadline — a missing event is a red test and never
     * a hanging one (the pattern of {@code ChatTurnTest.ChatStream}).
     */
    private final class StopStream implements Flow.Subscriber<String>, AutoCloseable {

        private final List<String> names = new CopyOnWriteArrayList<>();
        private final List<String> payloads = new CopyOnWriteArrayList<>();
        private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        private final CompletableFuture<?> exchange;
        private volatile String event = "message";
        private volatile String failure;

        StopStream(String projectId) {
            HttpRequest request = HttpRequest
                    .newBuilder(URI.create("http://127.0.0.1:" + port + "/api/projects/" + projectId
                            + "/events"))
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

        /** A tool line of the live wire, told by its text — no JSON reading needed for a plain line. */
        boolean hasToolLine(String text) {
            return payloads("tool").stream().anyMatch(payload -> payload.contains(text));
        }

        /** A negative assertion needs a grace window — this is the only fixed wait in this class. */
        void assertNoEventWithin(String name, long millis) {
            long until = System.currentTimeMillis() + millis;
            while (System.currentTimeMillis() < until && countOf(name) == 0) {
                sleep();
            }
            assertThat(countOf(name)).as("%s must not arrive", name).isZero();
        }

        /** A wait on the wire names the connection in its failure — a dead stream must not read as a slow one. */
        void await(BooleanSupplier condition, String what) {
            long until = System.currentTimeMillis() + 30_000;
            while (!condition.getAsBoolean()) {
                assertThat(System.currentTimeMillis())
                        .as("%s traf vor der Deadline ein (Verbindung: %s)", what, failure)
                        .isLessThan(until);
                sleep();
            }
        }

        /**
         * The last event of a stopped turn is the roster of its {@code finally} — it follows the
         * {@code done} and nothing comes after it, so waiting for it cannot pass too early.
         */
        void awaitRosterAsLastEvent() {
            await(() -> !names.isEmpty() && "agents".equals(names.get(names.size() - 1)),
                    "der letzte Roster-Snapshot schloss die Leitung ab");
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
