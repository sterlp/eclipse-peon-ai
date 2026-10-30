package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;

import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.open.aifactory.AifactoryApplication;
import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.project.FakeGitConfig;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * Der Neustart-Leseteil von UC-WP-39 und UC-WP-40: eine Delegation läuft, der Server wird wirklich
 * weggeworfen, und danach stehen dieselben Zeilen mit demselben Urheber wieder im Verlauf. Zwei
 * Kontexte hintereinander über demselben Home und auf je eigenem Port — kein
 * <code>@SpringBootTest</code>, denn der zweite Kontext darf nichts wissen außer dem, was auf der
 * Platte steht (R-WP-11).
 *
 * <p>Das ist zugleich die Antwort auf den offenen Punkt in
 * <code>aifactory/docs/open-points.md:6-13</code>: der Urheber braucht <b>keinen zweiten Speicher</b>.
 * Die Zuordnung liesst die Fabrik aus dem Werkzeugnamen, der an Jons Werkzeug-Ergebnis in <em>einer</em>
 * Datei steht, die der Core ohnehin schreibt — dieser Lauf ist der Beweis, dass dieser Name den
 * JSONL-Roundtrip übersteht. Der zweite Kontext liest sie, ohne das Modell ein zweites Mal zu fragen,
 * und die Home-Aufzählung bleibt, wie sie war.</p>
 *
 * <p>Der Urheber wird durchgehend als Feld (<code>origin</code>) geprüft, nie als Text in einer Zeile:
 * ein Name im Text wäre ein geratener Name (R-WP-4).</p>
 */
@Timeout(120)
class DelegatedAnswerRestartTest {

    private static final Path HOME = Path.of("target", "inc3f-home").toAbsolutePath().normalize();
    private static final String GIT_URL = "https://git.example.com/acme/delegated-restart.git";
    private static final String MEMBER_PROMPT = "MITGLIEDS-AUFGABE: nenne genau eine Frucht";
    private static final String MEMBER_ANSWER = "MITGLIEDS-ANTWORT: Birne";
    private static final String JON_ANSWER = "JONS-FOLGE-ANTWORT: die Birne";
    private static final String QUESTION = "frag Da Mek nach einer Frucht";

    private static MockLlmServer model;
    private static Running beforeRestart;
    private static Running afterRestart;
    private static String projectId;

    /** Der Chat, so wie der laufende Server ihn zuletzt geantwortet hat — der Maßstab für den zweiten. */
    private static List<ChatMessageDto> historyBeforeRestart;

    /** Die Dateiauflistung der Fabrik vor dem Neustart — sie darf durch den Neustart nicht wachsen. */
    private static List<Path> factoryFilesBeforeRestart;

    /** Der eine Chat-Speicher, den der laufende Server benutzt hat. */
    private static Path historyFile;

    /** Alles, was das Modell bis zum Wegwerfen des ersten Kontexts beantworten mußte. */
    private static int providerMessagesAtShutdown;

    @BeforeAll
    static void delegateThenRestartTheServer() throws IOException {
        clearHome();
        model = new MockLlmServer(0);
        model.start();
        writeFactoryConfig("inc3f3-restart");

        beforeRestart = boot();
        projectId = createProject(beforeRestart);
        scriptDelegation();
        send(beforeRestart, QUESTION);
        awaitUntil(() -> messages(beforeRestart).stream().anyMatch(line -> JON_ANSWER.equals(line.text())),
                30_000, "der Delegations-Turn sitzt im Verlauf, bevor der Server stirbt");

        historyBeforeRestart = messages(beforeRestart);
        factoryFilesBeforeRestart = factoryFilesOutsideTheCheckout();
        historyFile = runtime(beforeRestart).jon().getMemory().historyFile().orElseThrow();
        providerMessagesAtShutdown = model.getCapturedMessages().size();

        beforeRestart.context().close(); // von hier an kennt die Fabrik nur noch ihre Dateien

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
    // UC-WP-39
    void everyLineCarriesItsAuthor() throws IOException {
        assertThat(historyBeforeRestart)
                .as("vor dem Neustart trägt jede Zeile ihren Urheber: die Team-Antwort nennt Da Mek, "
                        + "Jons eigene Antwort nennt Jon, die getippte Eingabe niemanden")
                .extracting(ChatMessageDto::role, ChatMessageDto::origin)
                .containsExactly(
                        tuple("user", null),
                        tuple("jon", "Da Mek"),
                        tuple("jon", AiPoAgent.NAME));
        assertThat(historyBeforeRestart.get(0).origin())
                .as("die Eingabe bleibt namenlos — kein System, kein ersonnener Nutzer (R-WP-4, R-WP-17)")
                .isNull();

        assertThat(messages(afterRestart))
                .as("nach dem Neustart dieselbe Folge aus Rolle, Text und Herkunft — derselbe Name, "
                        + "nicht ein neu erfundener")
                .isEqualTo(historyBeforeRestart);
        assertThat(model.getCapturedMessages())
                .as("dafür wurde kein Modell befragt — die History-Datei des Cores war die Wahrheit")
                .hasSize(providerMessagesAtShutdown);

        assertThat(factoryFilesOutsideTheCheckout())
                .as("Herkunft braucht kein drittes Speicherziel: dieselben drei Dateien wie vor dem "
                        + "Neustart, dieselbe eine Chat-Datei")
                .isEqualTo(factoryFilesBeforeRestart)
                .containsExactlyInAnyOrder(
                        HOME.resolve(FactoryHome.CONFIG_FILE),
                        historyFile,
                        HOME.resolve(FactoryHome.PROJECTS_FILE));
        assertThat(filesUnder(HOME.resolve(FactoryHome.STATE_DIR)))
                .as("und in state/ liegt genau diese eine Datei — keine Sidecar für Urheber")
                .containsExactly(historyFile);
    }

    @Test
    // UC-WP-40
    void onlyFinalAnswersFromTheTeam() {
        List<ChatMessageDto> historyAfterRestart = messages(afterRestart);

        assertThat(historyAfterRestart.stream().filter(line -> "Da Mek".equals(line.origin())).toList())
                .as("GIVEN 3: die Antwort des Mitglieds steht immer noch im Verlauf — genau einmal, ihr"
                        + " Werkzeug-Ergebnis wörtlich, derselbe Name")
                .singleElement()
                .satisfies(line -> assertThat(line.text())
                        .startsWith(MEMBER_ANSWER + System.lineSeparator())
                        .contains("Da Mek done."));
        assertThat(historyAfterRestart)
                .as("keine Zwischenzeile des Mitglieds ist darin — gespeichert wurde nur, was Jon als"
                        + " Ergebnis seines Werkzeuges zurückbekam")
                .hasSize(3);
        assertThat(historyAfterRestart.stream().map(ChatMessageDto::text).toList())
                .as("und die Zeile ist das Ergebnis, nicht eine gekürzte Fassung: die Statistik-Zeile,"
                        + " die zusätzlich ins Modell geht, gehört zum Werkzeug-Ergebnis")
                .anyMatch(text -> text.contains("done. Context:"));
    }

    // ------------------------------------------------------------------ the scripted delegation

    /** Jon ruft <code>askDev</code>, das Mitglied antwortet, Jon antwortet dem Nutzer — alles an dasselbe Double. */
    private static void scriptDelegation() {
        model.queueResponse(new AiMessage(null, List.of(ToolExecutionRequest.builder()
                .id("call_dev")
                .name("askDev")
                .arguments("{\"prompt\":\"" + MEMBER_PROMPT + "\"}")
                .build())));
        model.queueResponse(MEMBER_ANSWER);
        model.queueResponse(JON_ANSWER);
    }

    // ------------------------------------------------------------------ the two contexts

    private record Running(ConfigurableApplicationContext context, int port) {
    }

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
        properties.setProperty(LlmConfigKeys.URL, DelegatedAnswerRestartTest.model.getUrl());
        properties.setProperty(LlmConfigKeys.API_KEY, "test-key");
        try (var out = Files.newOutputStream(HOME.resolve(FactoryHome.CONFIG_FILE))) {
            properties.store(out, "DelegatedAnswerRestartTest");
        }
    }

    private static void clearHome() throws IOException {
        assertThat(HOME.getFileName().toString())
                .as("this test deletes its home — only the scratch dir below target/ may ever be meant")
                .isEqualTo("inc3f-home");
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
