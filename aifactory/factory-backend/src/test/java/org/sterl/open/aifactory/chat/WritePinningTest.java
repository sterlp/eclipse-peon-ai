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
import java.util.Comparator;
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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.NamedAgent;
import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.WriteValidator;
import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.project.FakeGitConfig;
import org.sterl.open.aifactory.shared.FactoryHome;
import org.sterl.open.aifactory.shared.TmpRoots;

/**
 * R-WP-6 und R-WP-13 an der laufenden Fabrik: Jon <b>und</b> jedes Team-Mitglied schreiben nur in die
 * Projekt-Root und in den System-TMP dieser Instanz — {@code disk*}-Werkzeuge, kein zweiter Pfad.
 *
 * <p><b>Bekannte Luecke, bewusst nicht gebaut (A2, Inc-4-Beschluss):</b> {@code ShellTool} ruft
 * {@code validateWrite} niemals auf. Gepinnt und getestet ist der {@code disk*}-Pfad; die Shell bleibt
 * eine eigene Geschichte mit eigener Doc-Regel.</p>
 *
 * <p><b>Zwei Ebenen einer Ablehnung (UC-WP-9, R-WP-4):</b> Jons Ablehnung ist eine live {@code tool}-
 * Zeile, die eines Mitglieds endet beim Modell — seine Zeile darf den Filter nie erreichen. Die
 * Modell-Kopie ist ungekürzt, die Monitor-Kopie wird vom Core auf 180 Zeichen gekuerzt
 * ({@code SmartToolExecutor#reportProblem}), deshalb wird der volle Text am naechsten Anbieter-Payload
 * belegt und auf dem Draht nur das Praefix behauptet.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakeGitConfig.class)
@Timeout(60)
class WritePinningTest {

    /**
     * Festes Home unter {@code target/}, kein {@code @TempDir}: auf macOS <em>ist</em>
     * {@code java.io.tmpdir} die zweite Schreibwurzel — ein Home darin liesse jedes {@code ..} innerhalb
     * einer erlaubten Wurzel landen, UC-WP-10 waere nicht beweisbar.
     */
    private static final Path HOME = Path.of("target", "inc4-home").toAbsolutePath().normalize();

    /** Dritte Wurzel, nachweislich auesserhalb beider Schreibwurzeln — ebenfalls nicht unter TMP. */
    private static final Path OUTSIDE = Path.of("target", "inc4-outside").toAbsolutePath().normalize();

    /** Das einzige, was auf dem Draht stabil ist: die Monitor-Kopie wird bei 180 Zeichen gekuerzt. */
    private static final String DENIAL_PREFIX = "diskWriteFile: Write denied:";

    private static final String MEMBER_ANSWER = "MITGLIEDS-ANTWORT: erledigt";

    private static final HttpClient STREAMS = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    static {
        // vor jedem Kontext-Aufbau: ein Home aus einem frueheren Lauf wuerde Projekte eines anderen
        // Mock-Ports kennen
        deleteRecursively(HOME);
        deleteRecursively(OUTSIDE);
        createDirectories(HOME);
        createDirectories(OUTSIDE);
    }

    private MockLlmServer model;

    @LocalServerPort
    private int port;

    @Autowired
    private ProjectRuntimeRegistry runtimes;

    private RestTemplate http;

    @BeforeEach
    void startModelAndClient() {
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

    @AfterAll
    static void stopStreams() {
        STREAMS.close();
    }

    @DynamicPropertySource
    static void isolateHome(DynamicPropertyRegistry registry) {
        registry.add("aifactory.home", HOME::toString);
    }

    @Test
    // UC-WP-8
    void writeInsideProjectRootAllowed() throws Exception {
        writeFactoryConfig("inc4-inside");
        CreatedProject project = createProject("pin8");

        try (LiveStream stream = new LiveStream(project.id())) {
            model.queueResponse(toolCall("jon_note", "diskWriteFile",
                    "{\"filePath\":\"notes/jon.txt\",\"content\":\"jons-text\"}"));
            model.queueResponse("JON: notiz geschrieben");
            send(project.id(), "schreib notes/jon.txt");
            stream.awaitEvent("done", 1);

            Path jonsFile = project.localPath().resolve("notes/jon.txt");
            assertThat(Files.readString(jonsFile)).isEqualTo("jons-text");
            List<String> jonLines = toolLineTexts(stream);
            assertThat(jonLines)
                    .as("UC-WP-8: Jons Write ist eine Werkzeug-Zeile ueber der Projekt-Root")
                    .anySatisfy(line -> assertThat(line)
                            .startsWith("Created file:")
                            .endsWith(jonsFile.toString()));
            assertThat(jonLines).noneMatch(line -> line.contains("Write denied"));
            assertThat(toolResults())
                    .as("UC-WP-8: der naechste Anbieter-Payload kennt keine Ablehnung")
                    .noneMatch(text -> text.contains("Write denied"));

            // die Team-Haelfte: dasselbe Verlangen, aber durch eine Delegation
            int linesBeforeTeam = jonLines.size();
            model.queueResponse(toolCall("jon_ask", "askDev",
                    "{\"prompt\":\"schreibe die Datei, die ich dir genannt habe\"}"));
            model.queueResponse(toolCall("dev_note", "diskWriteFile",
                    "{\"filePath\":\"notes/team.txt\",\"content\":\"team-text\"}"));

            model.queueResponse(MEMBER_ANSWER);
            model.queueResponse("JON: team ist fertig");
            send(project.id(), "lass da mek schreiben");
            stream.awaitEvent("done", 2);

            Path teamFile = project.localPath().resolve("notes/team.txt");
            assertThat(Files.readString(teamFile))
                    .as("UC-WP-8: ein Team-Mitglied schreibt innerhalb der Projekt-Root wie im Plugin")
                    .isEqualTo("team-text");
            assertThat(messages(project.id()))
                    .as("die Delegation antwortet Jon, das ist ihre eigene Zeile — im Verlauf, nicht auf dem Draht")
                    .filteredOn(line -> "Dev".equals(line.origin()))
                    .singleElement()
                    .satisfies(line -> assertThat(line.text())
                            .startsWith(MEMBER_ANSWER + System.lineSeparator()));
            assertThat(toolLineTexts(stream).subList(linesBeforeTeam, toolLineTexts(stream).size()))
                    .as("vom Mitglied kommt nur seine Antwort an, seine Werkzeug-Zeile bleibt sein Internum (R-WP-4)")
                    .allSatisfy(line -> assertThat(line).doesNotContain("Created file:"));
        }
    }

    @Test
    // UC-WP-9
    void writeOutsideBothRootsRejected() throws Exception {
        writeFactoryConfig("inc4-outside");
        CreatedProject project = createProject("pin9");
        Path escape = OUTSIDE.resolve("escape.txt");

        try (LiveStream stream = new LiveStream(project.id())) {
            model.queueResponse(toolCall("jon_escape", "diskWriteFile",
                    "{\"filePath\":\"" + escape + "\",\"content\":\"escape\"}"));
            model.queueResponse("JON: das geht nicht");
            send(project.id(), "schreib " + escape);
            stream.awaitEvent("done", 1);

            assertThat(toolLineTexts(stream))
                    .as("UC-WP-9: Jons Ablehnung steht als Werkzeug-Zeile im Chat")
                    .anyMatch(line -> line.startsWith(DENIAL_PREFIX));

            String fullDenial = toolResults().stream()
                    .filter(text -> text.startsWith("Write denied:") && text.contains("allowed roots"))
                    .findFirst().orElse(null);
            assertThat(fullDenial)
                    .as("UC-WP-9: dieselbe Ablehnung erreicht das Modell — mit beiden Wurzeln")
                    .isNotNull()
                    .contains(project.localPath().toString())
                    .contains(TmpRoots.systemTmp().toString());
            assertThat(fullDenial.length())
                    .as("UC-WP-9: die Modell-Kopie ist die ungekuerzte Fassung (Monitor-Kopie: 180 Zeichen)")
                    .isGreaterThan(180);

            assertThat(Files.exists(escape)).as("UC-WP-9: die Datei entsteht nicht").isFalse();

            assertThat(messages(project.id()))
                    .as("UC-WP-9/R-WP-11: eine Werkzeug-Zeile ist live — nach dem Reload steht sie nicht im Verlauf")
                    .noneMatch(message -> message.text() != null && message.text().contains("Write denied"));
        }
    }

    @Test
    // UC-WP-9
    void memberRejectionStopsAtTheModel() throws Exception {
        writeFactoryConfig("inc4-member-denied");
        CreatedProject project = createProject("pin9m");
        Path escape = OUTSIDE.resolve("member-escape.txt");

        try (LiveStream stream = new LiveStream(project.id())) {
            model.queueResponse(toolCall("jon_ask", "askDev",
                    "{\"prompt\":\"schreibe die Datei ausserhalb beider Wurzeln\"}"));
            model.queueResponse(toolCall("dev_escape", "diskWriteFile",
                    "{\"filePath\":\"" + escape + "\",\"content\":\"member-escape\"}"));
            model.queueResponse(MEMBER_ANSWER);
            model.queueResponse("JON: weiter");
            send(project.id(), "frag da mek");
            stream.awaitEvent("done", 1);

            assertThat(stream.wireText())
                    .as("UC-WP-9 zweiter GIVEN: eine Mitglieds-Ablehnung ist ein Internum — keine Zeile auf dem Draht")
                    .doesNotContain("Write denied");

            String fullDenial = toolResults().stream()
                    .filter(text -> text.startsWith("Write denied:") && text.contains("allowed roots"))
                    .findFirst().orElse(null);
            assertThat(fullDenial)
                    .as("UC-WP-9 zweiter GIVEN: die Ablehnung erreicht als Werkzeug-Ergebnis das Modell")
                    .isNotNull()
                    .contains(TmpRoots.systemTmp().toString());
            assertThat(fullDenial.length())
                    .as("UC-WP-9 zweiter GIVEN: ungekuerzt, obwohl dieselbe Zeile auf dem Draht gekuerzt waere")
                    .isGreaterThan(180);

            assertThat(Files.exists(escape)).as("UC-WP-9 zweiter GIVEN: die Datei entsteht nicht").isFalse();

            assertThat(messages(project.id()))
                    .as("die Delegation selbst laeuft weiter — ihr Ergebnis ist ihre eigene Zeile im"
                            + " Verlauf, nicht ihre Ablehnung")
                    .filteredOn(line -> "Dev".equals(line.origin()))
                    .singleElement()
                    .satisfies(line -> assertThat(line.text())
                            .startsWith(MEMBER_ANSWER + System.lineSeparator()));

            assertThat(messages(project.id()))
                    .as("UC-WP-9 zweiter GIVEN/R-WP-11: auch im wiederhergestellten Verlauf steht keine Ablehnung")
                    .noneMatch(message -> message.text() != null && message.text().contains("Write denied"));
        }
    }

    @Test
    // UC-WP-10
    void traversalDoesNotEscapePinning() throws Exception {
        writeFactoryConfig("inc4-traversal");
        CreatedProject project = createProject("pin10");
        Path absoluteEscape = OUTSIDE.resolve("absolute.txt");

        try (LiveStream stream = new LiveStream(project.id())) {
            model.queueResponse(new AiMessage(null, List.of(
                    ToolExecutionRequest.builder().id("dotdot").name("diskWriteFile")
                            .arguments("{\"filePath\":\"../../dotdot.txt\",\"content\":\"dotdot\"}").build(),
                    ToolExecutionRequest.builder().id("absolute").name("diskWriteFile")
                            .arguments("{\"filePath\":\"" + absoluteEscape + "\",\"content\":\"absolute\"}")
                            .build())));
            model.queueResponse("JON: beides abgelehnt");
            send(project.id(), "zwei Ausbruchsversuche");
            stream.awaitEvent("done", 1);

            List<String> denials = toolLineTexts(stream).stream()
                    .filter(line -> line.startsWith(DENIAL_PREFIX)).toList();
            assertThat(denials)
                    .as("UC-WP-10: beide Pfade werden abgelehnt, einer pro Werkzeug-Zeile")
                    .hasSize(2);

            // "../../" von <home>/projects/<name> liegt in <home> — einer dritten Wurzel, weil das Home
            // bewusst nicht unter java.io.tmpdir liegt
            assertThat(Files.exists(HOME.resolve("dotdot.txt")))
                    .as("UC-WP-10: der ..-Pfad schreibt nicht in die Home-Ebene")
                    .isFalse();
            assertThat(Files.exists(project.localPath().resolve("dotdot.txt")))
                    .as("UC-WP-10: er schreibt auch nicht zurueck in die Projekt-Root")
                    .isFalse();
            assertThat(Files.exists(absoluteEscape))
                    .as("UC-WP-10: der absolute Pfad unter einer dritten Wurzel schreibt nicht")
                    .isFalse();

            List<String> results = toolResults();
            assertThat(results)
                    .as("UC-WP-10: beidem sagt das Modell denselben Grund — die Wurzeln, nicht nur ein Nein")
                    .anySatisfy(text -> assertThat(text)
                            .contains("dotdot.txt").contains("allowed roots")
                            .contains(project.localPath().toString()))
                    .anySatisfy(text -> assertThat(text)
                            .contains(absoluteEscape.toString()).contains("allowed roots")
                            .contains(TmpRoots.systemTmp().toString()));
        }
    }

    @Test
    // UC-WP-18
    void tmpWritesLandInSystemTempDir() throws Exception {
        writeFactoryConfig("inc4-tmp");
        CreatedProject project = createProject("pin18");
        Path tmpFile = TmpRoots.systemTmp().resolve("inc4-tmp-" + System.nanoTime() + ".txt");

        try (LiveStream stream = new LiveStream(project.id())) {
            model.queueResponse(toolCall("jon_tmp", "diskWriteFile",
                    "{\"filePath\":\"" + tmpFile + "\",\"content\":\"tmp-marker\"}"));
            model.queueResponse("JON: in tmp geschrieben");
            send(project.id(), "schreib in das temporaere Verzeichnis");
            stream.awaitEvent("done", 1);

            assertThat(Files.exists(tmpFile)).as("UC-WP-18: die Datei liegt im System-TMP").isTrue();
            assertThat(tmpFile.getParent())
                    .as("UC-WP-18: direkt unterhalb des System-TMP, nicht in einem Unterordner je Projekt")
                    .isEqualTo(TmpRoots.systemTmp());
            assertThat(Files.exists(project.localPath().resolve(tmpFile.getFileName().toString())))
                    .as("UC-WP-18: die Projekt-Root bleibt die andere Wurzel, keine Kopie dort")
                    .isFalse();
            assertThat(Files.exists(HOME.resolve("state").resolve(project.id()).resolve("tmp")))
                    .as("UC-WP-18: der Chat-Zustand ist keine dritte Wurzel und kein TMP-Ersatz")
                    .isFalse();
            assertThat(toolLineTexts(stream)).noneMatch(line -> line.contains("Write denied"));
        } finally {
            Files.deleteIfExists(tmpFile);
            assertThat(Files.exists(tmpFile)).as("UC-WP-18: der System-TMP wird aufgeraeumt").isFalse();
        }
    }

    /**
     * Dev wird in {@code ProjectRuntime} mit demselben Validator gebaut wie Jon. Dieser Test geht
     * <b>ohne</b> Delegation direkt an ihn — sonst bewiese eine Delegation nur den Filter, nie den Nagel.
     */
    @Test
    void memberWritesArePinnedToo() throws Exception {
        writeFactoryConfig("inc4-dev-pin");
        CreatedProject project = createProject("pindev");
        AiAgent devMek = devMemberOf(runtimes.runtime(project.id()));
        Path escape = OUTSIDE.resolve("dev-escape.txt");
        Path inside = project.localPath().resolve("notes/dev.txt");

        try {
            model.queueResponse(toolCall("dev_escape", "diskWriteFile",
                    "{\"filePath\":\"" + escape + "\",\"content\":\"dev-escape\"}"));
            model.queueResponse("MITGLIED: abgelehnt");
            devMek.call("schreib " + escape, AiMonitor.NULL_MONITOR);

            assertThat(Files.exists(escape))
                    .as("Dev ist festgenagelt: dasselbe Verlangen wie bei Jon, dieselbe Ablehnung")
                    .isFalse();
            assertThat(toolResults())
                    .as("und er liest denselben Grund wie Jon")
                    .anySatisfy(text -> assertThat(text)
                            .startsWith("Write denied:")
                            .contains("allowed roots")
                            .contains(project.localPath().toString())
                            .contains(TmpRoots.systemTmp().toString()));

            model.queueResponse(toolCall("dev_inside", "diskWriteFile",
                    "{\"filePath\":\"notes/dev.txt\",\"content\":\"dev-text\"}"));
            model.queueResponse("MITGLIED: geschrieben");
            devMek.call("schreib notes/dev.txt", AiMonitor.NULL_MONITOR);

            assertThat(Files.readString(inside))
                    .as("der Nagel ist ein Scope, keine Aussperrung — innerhalb der Wurzel schreibt er")
                    .isEqualTo("dev-text");
        } finally {
            Files.deleteIfExists(escape);
        }
    }

    /**
     * R6 in der Fabrik: Jons Prompt zitiert den Validator, der ihn gleich festnaegelt — beide Wurzeln,
     * nie den {@code docs/}-Satz des Core-Defaults, und nach einer Konfigurationsaenderung frisch.
     */
    @Test
    void jonsPromptNamesBothRootsNotTheDocsSentence() throws Exception {
        writeFactoryConfig("inc4-prompt");
        CreatedProject project = createProject("pinr6");
        AiPoAgent jon = runtimes.runtime(project.id()).jon();

        String prompt = jon.getSystemPrompt();
        assertThat(prompt)
                .as("der Prompt nennt genau die zwei Wurzeln, die Jon wirklich geschrieben bekommen")
                .contains(project.localPath().toString())
                .contains(TmpRoots.systemTmp().toString());
        assertThat(prompt)
                .as("der Core-Default-Text ist nicht mehr Jons Scope")
                .doesNotContain(WriteValidator.JON_PATH_SCOPE_PROMPT);

        // Die TMP-Wurzel wird je Aufruf gelesen — eine andere Konfigurationsaenderung darf einen
        // geaenderten Prompt ergeben, sonst waere der Prompt eine zweite, altbackene Wahrheit.
        // Vergleichswert ist die normalisierte Form: java.io.tmpdir kommt mitanhaenglichem "/" daher,
        // TmpRoots.systemTmp() liefert ihn ohne.
        String rawTmp = System.getProperty("java.io.tmpdir");
        String originalTmp = TmpRoots.systemTmp().toString();
        Path otherTmp = OUTSIDE.resolve("other-tmp");
        createDirectories(otherTmp);
        try {
            System.setProperty("java.io.tmpdir", otherTmp.toString());
            assertThat(jon.getSystemPrompt())
                    .as("der Prompt antwortet auf die Konfigurationsaenderung, ohne neuen Runtime")
                    .contains(otherTmp.toString())
                    .doesNotContain(originalTmp);
        } finally {
            System.setProperty("java.io.tmpdir", rawTmp);
        }
        assertThat(jon.getSystemPrompt()).contains(originalTmp);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Dev, wie Jon ihn kennt — derselbe Runtime, dieselbe Instanz, kein zweiter Agent. */
    private static AiAgent devMemberOf(ProjectRuntime runtime) {
        return runtime.jon().getTeam().stream()
                .filter(member -> AiDevAgent.NAME.equals(member.agent().getName()))
                .map(NamedAgent::agent)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Jons Team traegt keinen Dev: "
                        + runtime.jon().getTeam()));
    }

    private static AiMessage toolCall(String id, String toolName, String arguments) {
        return new AiMessage(null,
                List.of(ToolExecutionRequest.builder().id(id).name(toolName).arguments(arguments).build()));
    }

    /** Die Werkzeug-Zeilen, wie der Browser sie liest — den {@code text} der {@code tool}-Events. */
    private static List<String> toolLineTexts(LiveStream stream) {
        return stream.payloads("tool").stream()
                .map(payload -> (String) JsonPath.read(payload, "$.text"))
                .toList();
    }

    /** Jeder Text, den ein Agent als Werkzeug-Ergebnis zurueckbekam — die ungekuerzte Fassung. */
    private List<String> toolResults() {
        List<String> texts = new ArrayList<>();
        model.getCapturedMessages().stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(message -> ((ToolExecutionResultMessage) message).text())
                .forEach(texts::add);
        return texts;
    }

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

    private CreatedProject createProject(String name) {
        ResponseEntity<String> created = http.postForEntity("http://127.0.0.1:" + port + "/api/projects",
                jsonEntity(Map.of("gitUrl", "https://git.example.com/a/" + name + ".git")), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String localPath = JsonPath.read(created.getBody(), "$.localPath");
        return new CreatedProject(JsonPath.read(created.getBody(), "$.id"), Path.of(localPath));
    }

    /** Die Fabrik-Datei selbst — nie {@code ~/.peon}. */
    private void writeFactoryConfig(String modelName) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(LlmConfigKeys.PROVIDER_TYPE, "OPEN_AI");
        properties.setProperty(LlmConfigKeys.MODEL, modelName);
        properties.setProperty(LlmConfigKeys.URL, model.getUrl());
        properties.setProperty(LlmConfigKeys.API_KEY, "test-key");
        try (var out = Files.newOutputStream(HOME.resolve(FactoryHome.CONFIG_FILE))) {
            properties.store(out, "WritePinningTest");
        }
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(entry -> {
                try {
                    Files.deleteIfExists(entry);
                } catch (IOException ignored) {
                    // ein zurueckgelassenes target/-Verzeichnis nimmt der naechste mvn clean mit
                }
            });
        } catch (IOException broken) {
            throw new IllegalStateException("test home " + root + " is not clearable", broken);
        }
    }

    private static void createDirectories(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException broken) {
            throw new IllegalStateException("test home " + dir + " is not creatable", broken);
        }
    }

    private record CreatedProject(String id, Path localPath) {
    }

    /** Eine live Verbindung in Ankunftsreihenfolge (JDK-Client, Deadline-Schleife — {@code TeamInternalsTest}). */
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
        }

        void awaitEvent(String name, int count) {
            long until = System.currentTimeMillis() + 10_000;
            while (countOf(name) < count) {
                assertThat(System.currentTimeMillis())
                        .as("event %s x%d arrived (connection: %s, events so far: %s)", name, count, failure,
                                names)
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

        String wireText() {
            return String.join("|", payloads);
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
