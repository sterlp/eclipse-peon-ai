package org.sterl.open.aifactory.chat;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.JsonPath;

import dev.langchain4j.data.message.UserMessage;

import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.project.FakeGitConfig;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The first contact with a project is the one moment in which the registry could split a chat in two:
 * two requests that arrive together both find no runtime and both may build one. "Exactly one chat per
 * project" (R-WP-11, UC-WP-16) therefore has to hold <b>at the same time</b>, not only one after the
 * other — which is what no sequential test of {@link OneChatPerProjectTest} can show.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakeGitConfig.class)
@Timeout(120)
class ChatFirstTouchTest {

    @TempDir
    static Path home;

    private static final MockLlmServer MODEL = new MockLlmServer(0);

    @LocalServerPort
    private int port;

    @Autowired
    private ProjectRuntimeRegistry runtimes;

    private RestTemplate http;

    @BeforeAll
    static void startModel() {
        MODEL.start();
    }

    @AfterAll
    static void stopModel() {
        MODEL.stop();
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
    void concurrentFirstLookBuildsExactlyOneJon() throws Exception {
        writeFactoryConfig("inc3h2-one-jon");
        CreatedProject project = createProject("first-look");
        int runtimesBefore = runtimes.runtimeCount();

        int racers = 8;
        CyclicBarrier allAtOnce = new CyclicBarrier(racers);
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        List<ProjectRuntime> seen = new ArrayList<>();
        try {
            List<Future<ProjectRuntime>> built = new ArrayList<>();
            for (int i = 0; i < racers; i++) {
                built.add(pool.submit(() -> {
                    allAtOnce.await(10, SECONDS);
                    return runtimes.runtime(project.id());
                }));
            }
            for (Future<ProjectRuntime> one : built) {
                seen.add(one.get(30, SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        ProjectRuntime winner = seen.get(0);
        assertThat(seen).as("eight callers, one runtime — nobody gets a Jon of his own")
                .allSatisfy(one -> assertThat(one).isSameAs(winner));
        assertThat(runtimes.runtimeCount()).as("and the loser of the race leaves nothing behind")
                .isEqualTo(runtimesBefore + 1);
        assertThat(winner.jon().getName()).isEqualTo(AiPoAgent.NAME);
    }

    @Test
    void twoFirstTurnsAtOnceLeaveOneChat() throws Exception {
        writeFactoryConfig("inc3h2-two-turns");
        CreatedProject project = createProject("two-first-turns");
        int runtimesBefore = runtimes.runtimeCount();
        MODEL.queueResponse("erste Antwort");
        MODEL.queueResponse("zweite Antwort");

        CyclicBarrier allAtOnce = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> answers;
        try {
            List<Future<Integer>> posts = new ArrayList<>();
            for (String text : List.of("ERSTE-EINGABE", "ZWEITE-EINGABE")) {
                posts.add(pool.submit(() -> {
                    allAtOnce.await(10, SECONDS);
                    return send(project.id(), text);
                }));
            }
            answers = new ArrayList<>();
            for (Future<Integer> post : posts) {
                answers.add(post.get(30, SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(answers).as("both inputs are accepted").containsOnly(HttpStatus.ACCEPTED.value());
        assertThat(runtimes.runtimeCount()).isEqualTo(runtimesBefore + 1);

        // Then both belong to the same conversation: one history, both texts in it, one file behind it
        awaitUntil("both inputs in the history of that one project", () -> {
            List<String> history = textsOf(messages(project.id()));
            return history.stream().anyMatch(text -> text.contains("ERSTE-EINGABE"))
                    && history.stream().anyMatch(text -> text.contains("ZWEITE-EINGABE"));
        });
        assertThat(modelUserTexts())
                .as("and one Jon answered them — the provider saw both on one conversation")
                .anySatisfy(text -> assertThat(text).contains("ERSTE-EINGABE"))
                .anySatisfy(text -> assertThat(text).contains("ZWEITE-EINGABE"));

        List<Path> stored = Files.list(home.resolve(FactoryHome.STATE_DIR).resolve(project.id())).toList();
        assertThat(stored).as("one chat, one history file — no sidecar from the losing branch")
                .singleElement()
                .satisfies(file -> assertThat(file.getFileName().toString()).endsWith("-history.jsonl"));
    }

    // ------------------------------------------------------------------
    // the two edges this class needs
    // ------------------------------------------------------------------

    private int send(String projectId, String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity("http://127.0.0.1:" + port + "/api/projects/" + projectId + "/messages",
                new HttpEntity<>(new SendMessageRequest(text), headers), String.class).getStatusCode().value();
    }

    private List<ChatMessageDto> messages(String projectId) {
        return http.exchange("http://127.0.0.1:" + port + "/api/projects/" + projectId + "/messages", HttpMethod.GET,
                HttpEntity.EMPTY, new ParameterizedTypeReference<List<ChatMessageDto>>() {
                }).getBody();
    }

    private CreatedProject createProject(String slug) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> created = http.postForEntity("http://127.0.0.1:" + port + "/api/projects",
                new HttpEntity<>(Map.of("gitUrl", "https://git.example.com/acme/" + slug + ".git"), headers),
                String.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String localPath = JsonPath.read(created.getBody(), "$.localPath");
        return new CreatedProject(JsonPath.read(created.getBody(), "$.id"), Path.of(localPath));
    }

    private static List<String> modelUserTexts() {
        return MODEL.getCapturedMessages().stream()
                .filter(UserMessage.class::isInstance)
                .map(message -> ((UserMessage) message).singleText())
                .toList();
    }

    private static List<String> textsOf(List<ChatMessageDto> messages) {
        return messages == null ? List.of() : messages.stream().map(ChatMessageDto::text).toList();
    }

    private static void writeFactoryConfig(String model) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(LlmConfigKeys.PROVIDER_TYPE, "OPEN_AI");
        properties.setProperty(LlmConfigKeys.MODEL, model);
        properties.setProperty(LlmConfigKeys.URL, MODEL.getUrl());
        properties.setProperty(LlmConfigKeys.API_KEY, "test-key");
        try (var out = Files.newOutputStream(home.resolve(FactoryHome.CONFIG_FILE))) {
            properties.store(out, "ChatFirstTouchTest");
        }
    }

    /** Deadline instead of a fixed wait: the turn of a queued input arrives when the core is done. */
    private static void awaitUntil(String what, java.util.function.BooleanSupplier condition) {
        long until = System.currentTimeMillis() + 60_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > until) {
                fail("never reached within 60 s: " + what);
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail("interrupted while waiting: " + what);
            }
        }
    }

    private record CreatedProject(String id, Path localPath) {
    }
}
