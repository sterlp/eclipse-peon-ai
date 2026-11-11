package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

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
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;

import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.compact.CompactConstants;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.open.aifactory.FactorySpringTest;
import org.sterl.open.aifactory.config.FactoryConfigService;
import org.sterl.open.aifactory.chat.dto.AgentAction;
import org.sterl.open.aifactory.chat.dto.AgentActionRequest;
import org.sterl.open.aifactory.chat.dto.AgentTileDto;
import org.sterl.open.aifactory.chat.dto.AgentTileState;
import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.project.Project;
import org.sterl.open.aifactory.project.ProjectStatusStream;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * What an action on a tile does to the team, over the real edge (UC-WP-12): Clear on Jon takes his whole
 * team with him — plugin parity, because {@code AiPoAgent.clear()} is the cascade — while Clear on a
 * member touches exactly that one member.
 *
 * <p>The memories are seeded through the core's own {@code add}, so the numbers a tile shows are real
 * before the action and really gone after it; nothing here stubs an agent. Only the busy case needs a
 * turn, and it is held by a real {@link TurnGate} shell tool (the pattern of {@code ChatQueueTest}),
 * never by a wait that could pass by accident.</p>
 *
 * <p>Every wait is a deadline on the <b>end</b> state, and every end state is read behind a handshake: a
 * test only starts counting after the tile really holds the action. The core's own {@code COMPACTING} flag
 * is never polled — it is set inside the task and gone again before the next poll of a fast mock, so it
 * would make this class the flaky one it means to be not; the rule behind it is proven purely in
 * {@code AgentTilesTest}.</p>
 *
 * <p>A Compact is only believed when the <b>memory</b> changed: one line carrying the summary, never a
 * status word. And the one collision the review measured — a send while an action holds the project — is
 * produced on demand by an endpoint that accepts and never answers, so the turn's wait is observed instead
 * of hoped for.</p>
 */
@Timeout(90)
class AgentActionTest extends FactorySpringTest {

    /** Holds one turn inside a running tool until the test releases it — long enough for an action. */
    private static final String GATE_TOOL = TurnGate.toolArguments();
    private static final String SLOW_INPUT = "MITGLIEDS-LAUF: Der Turn hält das Werkzeug";

    /** The answer the compressor gets back: a memory holding this line was compacted, not skipped. */
    private static final String SUMMARY = "ZUSAMMENFASSUNG: Devs Auftrag und seine Zwischenschritte";

    /** The queued answer of the input that was sent while an action held the project. */
    private static final String ANSWER_AFTER_THE_ACTION = "ANTWORT-NACH-DER-AKTION";

    @Autowired
    private ProjectRuntimeRegistry runtimes;

    /** For the runtime a test builds by hand — the constructor is the seam, the registry is not asked. */
    @Autowired
    private FactoryConfigService config;

    @Autowired
    private ChatStreamRegistry streams;

    @Autowired
    private FactoryHome factoryHome;

    @LocalServerPort
    private int port;

    private MockLlmServer model;
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

    @Test
    @Timeout(60)
    // UC-WP-12
    void clearOnJonEmptiesJonAndHisTeam() {
        Project project = projects.add("https://git.example.com/acme/clear-jon.git").project();
        ProjectRuntime runtime = runtimes.runtime(project.id());
        seedEveryAgent(runtime);
        assertThat(tiles(project.id()))
                .as("every tile counted something before — otherwise the empty one after proves nothing")
                .filteredOn(tile -> !"Search".equals(tile.name()))
                .allSatisfy(tile -> assertThat(tile.tokens()).isPositive());

        assertThat(act(project.id(), AiPoAgent.NAME, "clear")).isEqualTo(HttpStatus.ACCEPTED.value());
        awaitUntil(() -> tiles(project.id()).stream()
                .allMatch(tile -> "Search".equals(tile.name()) || tile.tokens() == 0), 20_000,
                "jede Kachel zählt wieder null");

        assertThat(runtime.jon().getMemory().getCopy())
                .as("Jon's Clear is the cascade the plugin shows — his own memory is empty")
                .isEmpty();
        assertThat(runtime.jon().getTeam())
                .as("and it took the whole team with him, not only the agent the click was on")
                .allSatisfy(agent -> assertThat(agent.getMemory().getCopy())
                        .as("%s was cleared with Jon", agent.getName())
                        .isEmpty());
        assertThat(messages(project.id()))
                .as("the chat rebuilds empty — there is no second store that could still show a line")
                .isEmpty();
    }

    @Test
    @Timeout(60)
    // UC-WP-12
    void clearOnMemberLeavesTheOthersAlone() {
        Project project = projects.add("https://git.example.com/acme/clear-member.git").project();
        ProjectRuntime runtime = runtimes.runtime(project.id());
        seedEveryAgent(runtime);

        assertThat(act(project.id(), AiDevAgent.NAME, "clear")).isEqualTo(HttpStatus.ACCEPTED.value());
        awaitUntil(() -> tile(project.id(), AiDevAgent.NAME).tokens() == 0, 20_000,
                "Devs Kachel zählt null");

        assertThat(member(runtime, AiDevAgent.NAME).getMemory().getCopy())
                .as("the clicked member is empty")
                .isEmpty();
        assertThat(runtime.jon().getTeam())
                .filteredOn(agent -> !AiDevAgent.NAME.equals(agent.getName()))
                .as("and nobody else lost a line — one Clear is one agent (Jon's cascade is his own rule)")
                .allSatisfy(agent -> assertThat(agent.getMemory().getCopy())
                        .as("%s kept its own conversation", agent.getName())
                        .isNotEmpty());
    }

    /**
     * Internal, no own id — the guard UC-WP-12 rests on: a Clear that lands in the middle of a turn would
     * write over the memory that turn is still filling, so it is refused (409) and not queued.
     */
    @Test
    @Timeout(60)
    void actionWhileATurnRunsIsRefused() throws IOException {
        writeFactoryConfig("inc5b-busy");
        Project project = projects.add("https://git.example.com/acme/clear-busy.git").project();
        ProjectRuntime runtime = runtimes.runtime(project.id());
        model.queueResponse(new AiMessage(null, List.of(ToolExecutionRequest.builder()
                .id("call_gate").name("shellRunCommand").arguments(GATE_TOOL).build())));
        model.queueResponse("fertig nach dem werkzeug");
        send(project.id(), SLOW_INPUT);
        awaitUntil(runtime.jon()::isWorking, 20_000, "Jon arbeitet an der Eingabe");

        assertThat(act(project.id(), AiPoAgent.NAME, "clear"))
                .as("a Clear into a running turn is refused — an action is never queued behind a turn")
                .isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(runtime.jon().getMemory().getCopy())
                .as("and the refusal changed nothing: the turn's own input still stands in his memory")
                .anySatisfy(message -> assertThat(textOf(message)).contains(SLOW_INPUT));

        // the refusal is read while the tool still holds the turn; releasing it ends the turn undisturbed
        TurnGate.release(project.localPath());
        awaitUntil(() -> !runtime.jon().isWorking(), 30_000, "der Turn endete ungestört");
        assertThat(messages(project.id()))
                .as("the turn answers as if no action had been asked for")
                .isNotEmpty();
    }

    /**
     * UC-WP-13 over the real edge: Compact on one member leaves <b>that memory</b> as the summary alone.
     * The count is the proof — a memory of one line holding the summary can only be the result of a
     * Compact that ran, because {@code SKIPPED_SMALL} keeps every line where it was and is never a success
     * here. That is also why the seed is above the core's guard instead of at it.
     */
    @Test
    @Timeout(60)
    // UC-WP-13
    void compactTouchesOnlyThatAgent() throws IOException {
        writeFactoryConfig("inc5e-compact");
        Project project = projects.add("https://git.example.com/acme/compact-member.git").project();
        ProjectRuntime runtime = runtimes.runtime(project.id());
        seedEveryAgent(runtime);
        AiAgent dev = member(runtime, AiDevAgent.NAME);
        seedLongConversation(dev);
        long tokensBefore = tile(project.id(), AiDevAgent.NAME).tokens();
        assertThat(dev.getMemory().size())
                .as("under the core's guard nothing would run at all — a skip must never read as a compact")
                .isGreaterThanOrEqualTo(CompactConstants.MIN_COMPACT_MESSAGES);

        model.queueResponse(SUMMARY);
        assertThat(act(project.id(), AiDevAgent.NAME, "compact")).isEqualTo(HttpStatus.ACCEPTED.value());
        // the claim is taken on the request thread, so the tile already holds the action when 202 answers —
        // that is the handshake this wait needs, because the end state is also true before the task starts.
        // The core's own flag could not be it: set inside the task and gone again before the next poll of a
        // mock that answers at once, it would make this test the flaky one it is meant to rule out
        awaitUntil(() -> tile(project.id(), AiDevAgent.NAME).state() == AgentTileState.COMPACTING, 20_000,
                "Devs Kachel hält die Aktion, bevor ihr Ende gezählt wird");
        awaitUntil(() -> tile(project.id(), AiDevAgent.NAME).state() == AgentTileState.IDLE, 20_000,
                "Devs Compact endete — bereit wird die Kachel erst mit der Freigabe des Tasks");

        List<ChatMessage> lines = dev.getMemory().getCopy();
        assertThat(lines)
                .as("a compacted memory is the summary on its own — the four lines are gone, not kept beside it")
                .hasSize(1);
        assertThat(textOf(lines.get(0)))
                .as("and that one line is the summary the compressor answered")
                .contains(SUMMARY);
        assertThat(tile(project.id(), AiDevAgent.NAME).tokens())
                .as("his tile counts less than before the Compact — that is what the user sees")
                .isLessThan(tokensBefore);
        assertThat(runtime.jon().getTeam())
                .filteredOn(agent -> !AiDevAgent.NAME.equals(agent.getName()))
                .as("one Compact is one agent: nobody else gained a summary line or lost their own")
                .allSatisfy(agent -> assertThat(agent.getMemory().getCopy())
                        .as("%s was left alone", agent.getName())
                        .hasSize(2));
    }

    /**
     * Internal, no own id — the wiring BUG-1 asks for: a send during a running action is neither refused
     * (R-WP-10, UC-WP-14) nor lost. The action is a Compact of <b>Jon</b>, because that is the overlap the
     * review measured — the core holds his working flag over the summary call, so a turn started beside it
     * loses the slot claim, answers {@code null} and never reaches the model: no {@code done}, no answer,
     * the input gone. Here the Compact hangs in that very model call, so the wait is observed instead of
     * hoped for.
     */
    @Test
    @Timeout(60)
    // R-WP-65
    // UC-WP-102
    void sendDuringAnActionIsAnsweredNotLost() throws IOException {
        try (HeldModel held = new HeldModel()) {
            writeFactoryConfig("inc5e-held", held.url());
            Project project = projects.add("https://git.example.com/acme/send-during-action.git").project();
            ProjectRuntime runtime = runtimes.runtime(project.id());
            AiAgent jon = runtime.jon();
            seedEveryAgent(runtime);
            seedLongConversation(jon);

            assertThat(act(project.id(), AiPoAgent.NAME, "compact")).isEqualTo(HttpStatus.ACCEPTED.value());
            awaitUntil(jon::isCompacting, 20_000, "Jons Compact hängt in seinem Modell-Call");

            // the next turn reads its config per turn (UC-WP-24): back to the mock, which holds the answer
            writeFactoryConfig("inc5e-answered", model.getUrl());
            model.queueResponse(ANSWER_AFTER_THE_ACTION);
            int requestsBefore = model.getRequestBodies().size();
            send(project.id(), "FRAGE WÄHREND DER AKTION");

            assertThat(jon.isCompacting())
                    .as("the action still holds the project at the moment the input was taken")
                    .isTrue();
            // the stand says nothing is stoppable while the action holds, although a turn waits behind it:
            // an action is not something a turn's stop may cut (R-WP-65, UC-WP-102 GIVEN 1)
            assertThat(tile(project.id(), AiPoAgent.NAME).stoppable())
                    .as("a turn waiting behind a running Compact is not stoppable")
                    .isFalse();
            // letting the Compact go is what lets the turn run — the test never waits for a timeout
            held.close();

            awaitUntil(() -> messages(project.id()).stream()
                    .anyMatch(line -> line.text() != null && line.text().contains(ANSWER_AFTER_THE_ACTION)),
                    30_000, "die Eingabe während der Aktion wurde danach beantwortet");
            assertThat(model.getRequestBodies())
                    .as("exactly one model call answered it — beside the action there was none, and it was never refused")
                    .hasSize(requestsBefore + 1);
            awaitUntil(() -> tile(project.id(), AiPoAgent.NAME).state() == AgentTileState.IDLE, 20_000,
                    "Jons Kachel endet bereit");
        }
    }

    /**
     * UC-WP-13 on the track a stop leaves behind: a project keeps <b>one</b> monitor, so the cancel flag of
     * the turn the user ended would be handed to the next action of the project — and the compressor reads
     * that flag. Until the fix this test passed <em>and</em> failed on the same code: against an endpoint
     * that answers at once, the response and the cancel race in the core, so only an answer that arrives
     * later than the core's own 1500 ms check shows what the flag really did. Same endpoint for both calls,
     * because the runtime reads its model once per turn and an action never re-reads it.
     */
    @Test
    @Timeout(60)
    // UC-WP-13
    void compactAfterAStopStillRuns() throws IOException {
        // 2500 ms > the core's 1500 ms wait-step, < the 20 s timeout of this config
        try (DelayedModel late = new DelayedModel(SUMMARY, 2_500)) {
            writeFactoryConfig("inc5f-late", late.url());
            Project project = projects.add("https://git.example.com/acme/compact-after-stop.git").project();
            ProjectRuntime runtime = runtimes.runtime(project.id());

            send(project.id(), "FRAGE, DEREN TURN DER NUTZER BEENDET");
            awaitUntil(runtime.jon()::isWorking, 20_000, "Jon hängt an seinem Modell-Call");
            assertThat(stop(project.id()))
                    .as("the running turn is stopped, as UC-WP-50 promises")
                    .isEqualTo(HttpStatus.ACCEPTED.value());
            awaitUntil(() -> !runtime.jon().isWorking(), 30_000, "der Turn endete nach dem Stop");

            AiAgent dev = member(runtime, AiDevAgent.NAME);
            seedEveryAgent(runtime);
            seedLongConversation(dev);
            long tokensBefore = tile(project.id(), AiDevAgent.NAME).tokens();
            actUntilAccepted(project.id(), AiDevAgent.NAME, "compact");
            awaitUntil(() -> tile(project.id(), AiDevAgent.NAME).state() == AgentTileState.IDLE, 20_000,
                    "Devs Compact lief zu Ende");

            List<ChatMessage> lines = dev.getMemory().getCopy();
            assertThat(lines)
                    .as("a Compact after a stop is a Compact and not the failure of a flag nobody reset")
                    .hasSize(1);
            assertThat(textOf(lines.get(0)))
                    .as("and its one line is the summary, so the compressor really was answered")
                    .contains(SUMMARY);
            assertThat(tile(project.id(), AiDevAgent.NAME).tokens())
                    .as("his tile counts less than before — the user sees a result, not a stale number")
                    .isLessThan(tokensBefore);
        }
    }

    /**
     * Internal, no own id — the leak §16 measured: the claim is taken on the request thread and released by
     * the task that runs the action, so a task that was never started would hold this project until the
     * factory restarts. The rejection is produced on purpose with an executor that refuses exactly its first
     * task — the smallest honest stand-in for a refused submit, and no timing window involved.
     */
    @Test
    @Timeout(60)
    void anActionWhoseTaskNeverStartedLeavesTheProjectFree() throws IOException {
        writeFactoryConfig("inc5f-rejected-task");
        Project project = projects.add("https://git.example.com/acme/rejected-action.git").project();
        ProjectRuntime runtime = new ProjectRuntime(project.id(), project.localPath(),
                factoryHome.stateDir(project.id()), config::load, config::loadForTurn, streams,
                new ProjectStatusStream(), new RefusesItsFirstTask());
        seedEveryAgent(runtime);

        assertThatThrownBy(() -> runtime.act(AiPoAgent.NAME, AgentAction.CLEAR))
                .as("the edge says what happened instead of answering 202 for a task that never ran")
                .isInstanceOf(RejectedExecutionException.class);
        assertThat(runtime.jon().getMemory().getCopy())
                .as("and the refused attempt changed nothing")
                .isNotEmpty();

        runtime.act(AiPoAgent.NAME, AgentAction.CLEAR);
        awaitUntil(() -> runtime.jon().getMemory().getCopy().isEmpty(), 20_000,
                "der zweite Versuch lief wirklich — ein Projekt darf nicht für immer claimed bleiben");
    }

    // ------------------------------------------------------------------ seeding and the edges

    /**
     * One short conversation per agent of the roster — through the core's own {@code add}, so the token
     * counter behind a tile moves the way a real turn moves it.
     */
    private void seedEveryAgent(ProjectRuntime runtime) {
        for (AiAgent agent : runtime.jon().getTeam()) {
            agent.getMemory().add(UserMessage.from("AUFGABE " + agent.getName()));
            agent.getMemory().add(AiMessage.from("ANTWORT " + agent.getName()));
        }
    }

    /**
     * Two long lines on top of the seed: the Compact's guard counts messages, its tile figure counts
     * tokens, and both have to be worth comparing afterwards.
     */
    private void seedLongConversation(AiAgent agent) {
        agent.getMemory().add(UserMessage.from("VERTIEFTE AUFGABE: "
                + "Der Auftrag lautet, die Kacheln des Chat-Views zu bauen und dabei jede Regel des Plans zu halten. "
                + "Dabei darf der Datei-Bereich nicht angefasst werden. ".repeat(3)));
        agent.getMemory().add(AiMessage.from("VERTIEFTE ANTWORT: "
                + "Die Kacheln lesen den Core und erfinden keine Zahl; Clear und Compact laufen auf dem "
                + "Executor des Projekts und nie auf dem Thread des Aufrufers. ".repeat(3)));
    }

    private AiAgent member(ProjectRuntime runtime, String name) {
        return runtime.jon().getTeam().stream()
                .filter(agent -> name.equals(agent.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no team member named " + name));
    }

    private static String textOf(ChatMessage message) {
        if (message instanceof UserMessage user) {
            // every text part joined: a compacted memory re-seeds its summary among the turn context, so a
            // singleText() read would throw on exactly the line this class wants to look at
            StringBuilder text = new StringBuilder();
            for (Content content : user.contents()) {
                if (content instanceof TextContent txt) {
                    text.append(txt.text());
                }
            }
            return text.toString();
        }
        if (message instanceof AiMessage answer) {
            return String.valueOf(answer.text());
        }
        return String.valueOf(message);
    }

    private int act(String projectId, String name, String action) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity(url(projectId, "agents"),
                new HttpEntity<>(new AgentActionRequest(name, action), headers), String.class)
                .getStatusCode().value();
    }

    /**
     * An action asked for directly after a turn. The turn's own {@code finally} is the last thing to free
     * the project, so a refusal here only says it has not closed yet — the acceptance is the handshake, and
     * a project that never frees again goes red at the deadline instead of being waited past.
     */
    private void actUntilAccepted(String projectId, String name, String action) {
        long until = System.currentTimeMillis() + 20_000;
        int status = act(projectId, name, action);
        while (status == HttpStatus.CONFLICT.value() && System.currentTimeMillis() < until) {
            sleep();
            status = act(projectId, name, action);
        }
        assertThat(status)
                .as("%s auf %s wurde angenommen — ein Projekt, das nicht freigibt, bleibt zu", action, name)
                .isEqualTo(HttpStatus.ACCEPTED.value());
    }

    private int stop(String projectId) {
        return http.postForEntity(url(projectId, "messages/stop"), HttpEntity.EMPTY, String.class)
                .getStatusCode().value();
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
                .orElseThrow(() -> new AssertionError("no tile named " + name + " — got " + tiles(projectId)));
    }

    private List<ChatMessageDto> messages(String projectId) {
        return http.exchange(url(projectId, "messages"), HttpMethod.GET, null,
                new ParameterizedTypeReference<List<ChatMessageDto>>() {
                }).getBody();
    }

    private void send(String projectId, String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> answer = http.postForEntity(url(projectId, "messages"),
                new HttpEntity<>(new SendMessageRequest(text), headers), String.class);
        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    private String url(String projectId, String segment) {
        return "http://127.0.0.1:" + port + "/api/projects/" + projectId + "/" + segment;
    }

    /** The factory's own file — the same one the settings view writes. Never {@code ~/.peon}. */
    private void writeFactoryConfig(String model) throws IOException {
        writeFactoryConfig(model, this.model.getUrl());
    }

    /**
     * The same file aimed at another endpoint — the way to put a Compact's model call somewhere that never
     * answers. The timeout is written too, so a held call ends by itself if a test ever forgets to release
     * it: a mistake here must stay a red test and never become a hanging suite.
     */
    private void writeFactoryConfig(String model, String url) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(LlmConfigKeys.PROVIDER_TYPE, "OPEN_AI");
        properties.setProperty(LlmConfigKeys.MODEL, model);
        properties.setProperty(LlmConfigKeys.URL, url);
        properties.setProperty(LlmConfigKeys.API_KEY, "test-key");
        properties.setProperty(LlmConfigKeys.TIMEOUT, "20");
        try (var out = Files.newOutputStream(HOME.resolve(FactoryHome.CONFIG_FILE))) {
            properties.store(out, "AgentActionTest");
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
            throw new IllegalStateException("interrupted while waiting for the tile", interrupted);
        }
    }

    /** An endpoint of a test is closed, not negotiated with — what it makes of it is its own business. */
    private static void quietly(Closeable toClose) {
        try {
            toClose.close();
        } catch (IOException ignored) {
            // ending the test matters more than what the socket made of it
        }
    }

    /**
     * A model endpoint that accepts and never answers — the only way to hold a Compact inside its model
     * call long enough to send into the same project while it runs. Closing it ends the held call, and
     * that is what lets the waiting turn run: a claim is proven by an end state here, never by waiting out
     * a timeout.
     */
    private static final class HeldModel implements AutoCloseable {

        private final ServerSocket listener;
        private final List<Socket> accepted = new CopyOnWriteArrayList<>();

        HeldModel() throws IOException {
            listener = new ServerSocket(0);
            Thread acceptor = new Thread(() -> {
                while (!listener.isClosed()) {
                    try {
                        accepted.add(listener.accept());
                    } catch (IOException closed) {
                        return;
                    }
                }
            }, "held-model-endpoint");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        /** The shape an OpenAI endpoint is configured with — the same {@code /v1} the mock answers under. */
        String url() {
            return "http://127.0.0.1:" + listener.getLocalPort() + "/v1";
        }

        @Override
        public void close() {
            accepted.forEach(AgentActionTest::quietly);
            quietly(listener);
        }
    }

    /**
     * A model endpoint that answers every request once and <b>late</b>. The pause is what makes a cancel
     * observable: the core gives its wait up every 1500 ms and reads the project's cancel flag there, while
     * an endpoint that answers at once lets the response and the cancel race inside the core (measured
     * 2026-11-01: the same code passed and failed). Written here, in the framing the mock proves against the
     * same client, because the core's mock has no pause and the core is not this increment's to change.
     */
    private static final class DelayedModel implements AutoCloseable {

        private final ServerSocket listener;
        private final String answer;
        private final long pauseMs;
        private final List<Socket> accepted = new CopyOnWriteArrayList<>();

        DelayedModel(String answer, long pauseMs) throws IOException {
            this.answer = answer;
            this.pauseMs = pauseMs;
            listener = new ServerSocket(0);
            Thread acceptor = new Thread(() -> {
                while (!listener.isClosed()) {
                    try {
                        Socket client = listener.accept();
                        accepted.add(client);
                        Thread.ofVirtual().start(() -> answerLate(client));
                    } catch (IOException closed) {
                        return;
                    }
                }
            }, "delayed-model-endpoint");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        /** The shape an OpenAI endpoint is configured with — the same {@code /v1} the mock answers under. */
        String url() {
            return "http://127.0.0.1:" + listener.getLocalPort() + "/v1";
        }

        private void answerLate(Socket client) {
            try (client) {
                readRequest(client.getInputStream());
                Thread.sleep(pauseMs);
                byte[] body = sse().getBytes(StandardCharsets.UTF_8);
                OutputStream out = client.getOutputStream();
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: "
                        + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(body);
                out.flush();
            } catch (Exception ended) {
                // the call was released or the test ended — either way this endpoint has nothing left to say
            }
        }

        /** role, content, finish, {@code [DONE]} — the four chunks the mock sends for a plain answer. */
        private String sse() {
            return chunk("", null) + chunk(answer, null) + chunk("", "stop") + "data: [DONE]\n\n";
        }

        private String chunk(String content, String finishReason) {
            return "data: {\"id\":\"chatcmpl-delayed\",\"object\":\"chat.completion.chunk\",\"choices\":[{"
                    + "\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"" + content + "\"}"
                    + (finishReason == null ? "" : ",\"finish_reason\":\"" + finishReason + "\"")
                    + "}]}\n\n";
        }

        /** The whole request has to be taken, or the client sits in a write this socket never reads. */
        private static void readRequest(InputStream in) throws IOException {
            StringBuilder headers = new StringBuilder();
            int read;
            while ((read = in.read()) != -1) {
                headers.append((char) read);
                if (headers.indexOf("\r\n\r\n") >= 0) {
                    break;
                }
            }
            for (int left = contentLengthOf(headers); left > 0; left--) {
                if (in.read() == -1) {
                    return;
                }
            }
        }

        private static int contentLengthOf(CharSequence headers) {
            for (String line : headers.toString().split("\r\n")) {
                if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                    return Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                }
            }
            return 0;
        }

        @Override
        public void close() {
            accepted.forEach(AgentActionTest::quietly);
            quietly(listener);
        }
    }

    /**
     * An executor that refuses its first task and runs every later one — the honest stand-in for a refused
     * submit, with no timing window of its own. Virtual threads, so it leaves no platform thread behind.
     */
    private static final class RefusesItsFirstTask extends AbstractExecutorService {

        private final ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
        private boolean refused;

        @Override
        public synchronized void execute(Runnable command) {
            if (!refused) {
                refused = true;
                throw new RejectedExecutionException("refused on purpose — the leak this stands in for");
            }
            delegate.execute(command);
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }
    }
}
