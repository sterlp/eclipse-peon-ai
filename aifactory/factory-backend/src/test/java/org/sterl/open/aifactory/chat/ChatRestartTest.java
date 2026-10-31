package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.JsonPath;

import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.open.aifactory.AifactoryApplication;
import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.project.FakeGitConfig;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * A real restart, not a restarted bean: two application contexts one after the other over the same home
 * dir and on different ports. No {@code @SpringBootTest} in this class on purpose — the second context
 * may know nothing beyond what the disk knows, which is exactly what a restarted process knows (R-WP-11).
 *
 * <p>The home is a fixed path below {@code target/}, cleared at the start, and not a {@code @TempDir}:
 * two contexts of different lifetime cannot share a temp dir, and the whole point is that the second one
 * finds the first one's files.</p>
 */
@Timeout(180)
class ChatRestartTest {

    private static final Path HOME = Path.of("target", "inc3d-home").toAbsolutePath().normalize();
    private static final String GIT_URL = "https://git.example.com/acme/survive-restart.git";
    private static final String QUESTION = "die Frage vor dem Neustart";
    private static final String ANSWER = "die Antwort vor dem Neustart";

    private static MockLlmServer model;
    private static Running beforeRestart;
    private static Running afterRestart;
    private static String projectId;
    private static List<ChatMessageDto> historyBeforeRestart;

    /** Everything the provider had been asked when the first context went away. */
    private static int providerRequestsAtShutdown;

    @BeforeAll
    static void talkToJonThenRestartTheServer() throws IOException {
        clearHome();
        model = new MockLlmServer(0);
        model.start();
        writeFactoryConfig("inc3d-restart");

        beforeRestart = boot();
        projectId = createProject(beforeRestart);
        model.queueResponse(ANSWER);
        send(beforeRestart, QUESTION);
        awaitUntil(() -> messages(beforeRestart).stream().anyMatch(line -> ANSWER.equals(line.text())),
                30_000, "der Turn sitzt im Verlauf, bevor der Server stirbt");

        historyBeforeRestart = messages(beforeRestart);
        providerRequestsAtShutdown = model.getCapturedMessages().size();

        beforeRestart.context().close(); // from here on the factory knows only its files

        afterRestart = boot();
    }

    @AfterAll
    static void stopSecondContextAndModel() {
        if (afterRestart != null) {
            afterRestart.context().close();
        }
        if (model != null) {
            model.stop();
        }
    }

    @Test
    // UC-WP-15
    void registryAndChatSurviveRestart() {
        assertThat(projectIds(afterRestart))
                .as("ein Projekt in der Liste, genau wie vor dem Neustart")
                .containsExactly(projectId);
        assertThat(messages(afterRestart))
                .as("derselbe Chat, dieselbe Reihenfolge, derselbe Text")
                .isEqualTo(historyBeforeRestart);
        assertThat(model.getCapturedMessages())
                .as("dafür wurde kein Modell befragt — die History-Datei des Cores war die Wahrheit")
                .hasSize(providerRequestsAtShutdown);
        assertThat(runtime(afterRestart).jon().getMemory().historyFile().orElseThrow().getParent().toString())
                .as("zurückgekommen ist er aus state/<id> — dem Bestand genau dieses Projekts")
                .isEqualTo(HOME.resolve(FactoryHome.STATE_DIR).resolve(projectId).toString());

        // the same rule from the other side: the conversation is back, the team is not
        ProjectRuntime jon = runtime(afterRestart);
        assertThat(jon.jon().getTeam())
                .as("Jon führt wieder sein Team")
                .extracting(AiAgent::getName)
                .containsExactly("Jon", "Plan", "Dev", "Review");
        assertThat(jon.jon().getTeam())
                .filteredOn(member -> !"Jon".equals(member.getName()))
                .allSatisfy(member -> assertThat(member.getMemory().size())
                        .as("%s beginnt leer (R-WP-11, ADR-0024)", member.getName())
                        .isZero());
        assertThat(jon.jon().getMemory().size())
                .as("und nur Jons eigenes Gespräch kam aus dem Bestand zurück")
                .isPositive();
    }

    @Test
    void restartNeedsNoSecondStore() throws IOException {
        Path historyFile = runtime(afterRestart).jon().getMemory().historyFile().orElseThrow();

        assertThat(historyFile)
                .as("der Chat liegt im History-Bestand des Cores")
                .exists();
        assertThat(historyFile.getParent().toString())
                .as("und zwar in state/<id> — nicht in einem Verzeichnis, das sich die Fabrik ausdenkt")
                .isEqualTo(HOME.resolve(FactoryHome.STATE_DIR).resolve(projectId).toString());
        assertThat(filesUnder(HOME.resolve(FactoryHome.STATE_DIR)))
                .as("genau diese eine Datei — keine Sidecar, kein zweiter Chat-Speicher")
                .containsExactly(historyFile);
        assertThat(factoryFilesOutsideTheCheckout())
                .as("die Fabrik hält weiter genau drei Dinge: Registry, Config, History")
                .containsExactlyInAnyOrder(
                        HOME.resolve(FactoryHome.CONFIG_FILE),
                        historyFile,
                        HOME.resolve(FactoryHome.PROJECTS_FILE));
    }

    // ------------------------------------------------------------------ the two contexts

    private record Running(ConfigurableApplicationContext context, int port) {
    }

    /**
     * A fresh process would start the same application; this starts the same application with a fresh
     * context, with git still faked (a clone is not the subject here) and with the log quiet enough to
     * read a failure when one happens.
     */
    private static Running boot() {
        ConfigurableApplicationContext context =
                new SpringApplicationBuilder(AifactoryApplication.class, FakeGitConfig.class)
                        .properties(
                                FactoryHome.HOME_PROPERTY + "=" + HOME,
                                "server.port=0",
                                "spring.main.banner-mode=off",
                                "logging.level.root=WARN")
                        .run();
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        return new Running(context, port);
    }

    private static ProjectRuntime runtime(Running running) {
        return running.context().getBean(ProjectRuntimeRegistry.class).runtime(projectId);
    }

    // ------------------------------------------------------------------ the three edges + projects

    private static String createProject(Running running) {
        ResponseEntity<String> created = client().postForEntity(url(running, "/api/projects"),
                jsonEntity(Map.of("gitUrl", GIT_URL)), String.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return JsonPath.read(created.getBody(), "$.id");
    }

    private static List<String> projectIds(Running running) {
        List<Map<String, Object>> projects = client().exchange(url(running, "/api/projects"), HttpMethod.GET,
                HttpEntity.EMPTY, new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }).getBody();
        return projects.stream().map(project -> (String) project.get("id")).toList();
    }

    private static void send(Running running, String text) {
        ResponseEntity<String> accepted = client().postForEntity(
                url(running, "/api/projects/" + projectId + "/messages"),
                jsonEntity(new SendMessageRequest(text)), String.class);

        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    private static List<ChatMessageDto> messages(Running running) {
        return client().exchange(url(running, "/api/projects/" + projectId + "/messages"), HttpMethod.GET,
                HttpEntity.EMPTY, new ParameterizedTypeReference<List<ChatMessageDto>>() {
                }).getBody();
    }

    private static String url(Running running, String path) {
        return "http://127.0.0.1:" + running.port() + path;
    }

    private static RestTemplate client() {
        RestTemplate client = new RestTemplate();
        // the status IS the assertion — a throwing client would hide what the app answers
        client.setErrorHandler(new NoOpResponseErrorHandler());
        return client;
    }

    private static HttpEntity<Object> jsonEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    // ------------------------------------------------------------------ home

    /** The factory's own file — the same one the settings view writes. Never {@code ~/.peon}. */
    private static void writeFactoryConfig(String model) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(LlmConfigKeys.PROVIDER_TYPE, "OPEN_AI");
        properties.setProperty(LlmConfigKeys.MODEL, model);
        properties.setProperty(LlmConfigKeys.URL, ChatRestartTest.model.getUrl());
        properties.setProperty(LlmConfigKeys.API_KEY, "test-key");
        try (var out = Files.newOutputStream(HOME.resolve(FactoryHome.CONFIG_FILE))) {
            properties.store(out, "ChatRestartTest");
        }
    }

    private static void clearHome() throws IOException {
        assertThat(HOME.getFileName().toString())
                .as("this test deletes its home — only the scratch dir below target/ may ever be meant")
                .isEqualTo("inc3d-home");
        if (Files.exists(HOME)) {
            deleteTree(HOME);
        }
        Files.createDirectories(HOME);
    }

    private static void deleteTree(Path directory) throws IOException {
        try (Stream<Path> tree = Files.walk(directory)) {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /** Sorted so a failure names the file that appeared, instead of shuffling the whole list. */
    private static List<Path> filesUnder(Path directory) throws IOException {
        try (Stream<Path> tree = Files.walk(directory)) {
            return tree.filter(Files::isRegularFile).sorted().toList();
        }
    }

    /**
     * Everything the factory itself keeps. The checkout under {@code projects/} is excluded because it
     * legitimately holds a repository — the point is that <em>beside</em> it nothing new appeared.
     */
    private static List<Path> factoryFilesOutsideTheCheckout() throws IOException {
        Path checkout = HOME.resolve(FactoryHome.PROJECTS_DIR);
        try (Stream<Path> tree = Files.walk(HOME)) {
            return tree.filter(Files::isRegularFile)
                    .filter(path -> !path.startsWith(checkout))
                    .sorted()
                    .toList();
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
}
