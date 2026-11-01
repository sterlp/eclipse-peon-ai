package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;

import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.open.aifactory.chat.dto.AgentActionRequest;
import org.sterl.open.aifactory.chat.dto.AgentTileDto;
import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.project.FakeGitConfig;
import org.sterl.open.aifactory.project.Project;
import org.sterl.open.aifactory.project.ProjectService;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * What an action on a tile does to the team, over the real edge (UC-WP-12): Clear on Jon takes his whole
 * team with him — plugin parity, because {@code AiPoAgent.clear()} is the cascade — while Clear on a
 * member touches exactly that one member.
 *
 * <p>The memories are seeded through the core's own {@code add}, so the numbers a tile shows are real
 * before the action and really gone after it; nothing here stubs an agent. Only the busy case needs a
 * turn, and it is slowed by a real {@code sleep 3} shell tool (the pattern of {@code ChatQueueTest}),
 * never by a wait that could pass by accident.</p>
 *
 * <p>Every wait is a deadline on the <b>end</b> state. The {@code COMPACTING} window is deliberately not
 * proven by polling — it lasts milliseconds and would make the suite flaky; its rule is proven purely in
 * {@code AgentTilesTest}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakeGitConfig.class)
@Timeout(120)
class AgentActionTest {

    /** Real tool content that keeps one turn busy long enough for an action to hit a running project. */
    private static final String SLEEP_TOOL = "{\"command\":\"sleep 3\",\"timeout\":20}";
    private static final String SLOW_INPUT = "MITGLIEDS-LAUF: Schlaf drei Sekunden";

    @TempDir
    static Path home;

    @Autowired
    private ProjectRuntimeRegistry runtimes;

    @Autowired
    private ProjectService projects;

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

    @DynamicPropertySource
    static void isolateHome(DynamicPropertyRegistry registry) {
        registry.add("aifactory.home", home::toString);
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
                .id("call_sleep").name("shellRunCommand").arguments(SLEEP_TOOL).build())));
        model.queueResponse("fertig nach dem schlaf");
        send(project.id(), SLOW_INPUT);
        awaitUntil(runtime.jon()::isWorking, 20_000, "Jon arbeitet an der Eingabe");

        assertThat(act(project.id(), AiPoAgent.NAME, "clear"))
                .as("a Clear into a running turn is refused — an action is never queued behind a turn")
                .isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(runtime.jon().getMemory().getCopy())
                .as("and the refusal changed nothing: the turn's own input still stands in his memory")
                .anySatisfy(message -> assertThat(textOf(message)).contains(SLOW_INPUT));

        awaitUntil(() -> !runtime.jon().isWorking(), 30_000, "der Turn endete ungestört");
        assertThat(messages(project.id()))
                .as("the turn answers as if no action had been asked for")
                .isNotEmpty();
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

    private AiAgent member(ProjectRuntime runtime, String name) {
        return runtime.jon().getTeam().stream()
                .filter(agent -> name.equals(agent.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no team member named " + name));
    }

    private static String textOf(ChatMessage message) {
        if (message instanceof UserMessage user) {
            return user.singleText();
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
        Properties properties = new Properties();
        properties.setProperty(LlmConfigKeys.PROVIDER_TYPE, "OPEN_AI");
        properties.setProperty(LlmConfigKeys.MODEL, model);
        properties.setProperty(LlmConfigKeys.URL, this.model.getUrl());
        properties.setProperty(LlmConfigKeys.API_KEY, "test-key");
        try (var out = Files.newOutputStream(home.resolve(FactoryHome.CONFIG_FILE))) {
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
}
