package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sterl.open.aifactory.chat.ChatHistoryWaits.awaitAnswers;

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
import dev.langchain4j.data.message.UserMessage;

import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.project.FakeGitConfig;
import org.sterl.open.aifactory.project.ProjectRemoved;
import org.sterl.open.aifactory.project.ProjectService;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The name a send carries is the <b>author of the message itself</b> (R-WP-25, UC-WP-48): it travels into
 * Jon's memory as part of the text, so the model reads who wrote it, the history names that person, and a
 * restart needs no measure of its own — the text is what the core already keeps (R-WP-11). Nothing below
 * {@code AiPoAgent} is stubbed; the double only answers.
 *
 * <p>Two measurements carry that: the raw body of the provider request, because a name the model never saw
 * would be a label for the UI and nothing more, and the read history, because the name has to arrive
 * without any client sending it again. Both are taken on the real edge (HTTP in, HTTP out).</p>
 *
 * <p>The model double is fresh per test and every test waits for its own turn to end: a turn is
 * fire-and-forget, so a red assertion would otherwise leave a turn running on a server the next test has
 * already replaced.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakeGitConfig.class)
@Timeout(120)
class ChatUserMessageTest {

    /** Real tool content that holds a turn open, so a second send has to queue (see {@code ChatQueueTest}). */
    private static final String SLEEP_TOOL = "{\"command\":\"sleep 3\",\"timeout\":20}";

    @TempDir
    static Path home;

    private MockLlmServer model;

    @LocalServerPort
    private int port;

    @Autowired
    private ProjectService projects;

    @Autowired
    private ProjectRuntimeRegistry runtimes;

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
    // UC-WP-48
    void everyMessageToJonCarriesTheSendersName() throws Exception {
        writeFactoryConfig();
        String projectId = createProject("sender-48");
        model.queueResponse("Antwort an Anna");

        send(projectId, "bitte X", "Anna");
        awaitAnswers(() -> history(projectId), 1, "der benannte Turn ist beantwortet");

        assertThat(model.getLastRequestBody())
                .as("das Modell liest den Absender in der Nachricht selbst — ein Label für die UI allein"
                        + " wäre kein Paco und keine Erinnerung")
                .contains("[User Anna]: bitte X");
        assertThat(model.getLastRequestBody())
                .as("und der Prompt erklärt das Format, sonst muss Jon raten, was der Name soll")
                .contains("Messages from people start with")
                .contains("[User <Name>]");
    }

    @Test
    @Timeout(60)
    // UC-WP-48
    void withoutANameThereIsNoPrefix() throws Exception {
        writeFactoryConfig();
        String projectId = createProject("nameless-48");
        model.queueResponse("Antwort ohne Namen");

        send(projectId, "bitte X", null);
        awaitAnswers(() -> history(projectId), 1, "der Turn ohne Namen ist beantwortet");

        assertThat(requestedUserTexts())
                .as("R-WP-17: ohne Namen wird nichts erfunden — auch kein leerer Präfix-Rahmen")
                .noneMatch(text -> text.startsWith("[User "));
        assertThat(history(projectId))
                .as("und der Verlauf erfindet die Herkunft ebenso wenig")
                .contains(new ChatMessageDto("user", "bitte X", null));
    }

    @Test
    @Timeout(60)
    // UC-WP-48
    void aTypedPrefixIsNobodyInTheHistory() throws Exception {
        writeFactoryConfig();
        String projectId = createProject("typed-prefix-48");
        model.queueResponse("Antwort auf die Format-Zeile");
        String typed = "[User Anna]: bitte X";

        send(projectId, typed, null);
        awaitAnswers(() -> history(projectId), 1, "die getippte Format-Zeile ist beantwortet");

        assertThat(history(projectId))
                .as("R-WP-25: das Präfix ist Protokoll, kein Ausweis — eine selbst getippte Format-Zeile"
                        + " wird niemandem zugeschrieben und ihr Text bleibt lesbar")
                .containsExactly(
                        new ChatMessageDto("user", typed, null),
                        new ChatMessageDto("jon", "Antwort auf die Format-Zeile", AiPoAgent.NAME));
        assertThat(capturedText())
                .as("Jon liest die Zeile weiter, die Fabrik zeichnet sie nur als getippt aus")
                .contains("\\[User Anna]: bitte X");
    }

    @Test
    @Timeout(60)
    // UC-WP-48
    void theHistoryNamesTheSender() throws Exception {
        writeFactoryConfig();
        String projectId = createProject("history-48");
        model.queueResponse("Antwort an Anna");

        send(projectId, "bitte X", "Anna");
        awaitAnswers(() -> history(projectId), 1, "der benannte Turn ist beantwortet");

        assertThat(history(projectId))
                .as("der Verlauf nennt den Menschen, ohne dass ein Client den Namen mitschickt")
                .containsExactly(
                        new ChatMessageDto("user", "bitte X", "Anna"),
                        new ChatMessageDto("jon", "Antwort an Anna", AiPoAgent.NAME));
    }

    @Test
    @Timeout(60)
    // UC-WP-48
    void theNameSurvivesARestart() throws Exception {
        writeFactoryConfig();
        String projectId = createProject("restart-48");
        model.queueResponse("Antwort an Anna");

        send(projectId, "bitte X", "Anna");
        awaitAnswers(() -> history(projectId), 1, "der benannte Turn ist beantwortet");

        // dasselbe state-Verzeichnis, keine Runtime mehr — der Neustart der Fabrik
        int runtimesBefore = runtimes.runtimeCount();
        runtimes.onProjectRemoved(new ProjectRemoved(projectId));
        assertThat(runtimes.runtimeCount())
                .as("die Runtime dieses Projekts ist weg, ihr Speicher nicht")
                .isEqualTo(runtimesBefore - 1);

        assertThat(history(projectId))
                .as("der Name steht im Text, den der Core ohnehin hält — ein zweiter Speicher wäre ein"
                        + " zweiter, der schiefgehen kann")
                .anySatisfy(line -> {
                    assertThat(line.role()).isEqualTo("user");
                    assertThat(line.text()).isEqualTo("bitte X");
                    assertThat(line.origin()).isEqualTo("Anna");
                });
    }

    @Test
    @Timeout(60)
    // UC-WP-48
    void twoBrowsersAreTwoSenders() throws Exception {
        writeFactoryConfig();
        String projectId = createProject("two-48");
        model.queueResponse("Antwort an Anna");
        model.queueResponse("Antwort an Paul");

        send(projectId, "frage von anna", "Anna");
        awaitAnswers(() -> history(projectId), 1, "Annas Eingabe ist beantwortet");
        int runtimesBeforeTheSecondSender = runtimes.runtimeCount();
        send(projectId, "frage von paul", "Paul");
        awaitAnswers(() -> history(projectId), 2, "Pauls Eingabe ist beantwortet");

        assertThat(history(projectId))
                .as("ein geteiltes Gespräch, zwei Autoren — und beide Namen stehen für denselben Betrachter da")
                .extracting(ChatMessageDto::role, ChatMessageDto::origin)
                .containsExactly(
                        tuple("user", "Anna"),
                        tuple("jon", AiPoAgent.NAME),
                        tuple("user", "Paul"),
                        tuple("jon", AiPoAgent.NAME));
        assertThat(runtimes.runtimeCount())
                .as("zwei Absender sind zwei Namen in demselben Gespräch, nicht zwei Gespräche")
                .isEqualTo(runtimesBeforeTheSecondSender);
    }

    @Test
    @Timeout(90)
    // UC-WP-48
    void aQueuedInputKeepsItsName() throws Exception {
        writeFactoryConfig();
        String projectId = createProject("queued-48");
        model.queueResponse(toolCall("call_sleep", "shellRunCommand", SLEEP_TOOL));
        model.queueResponse("erste Antwort fertig");
        model.queueResponse("zweite Antwort");

        send(projectId, "erste Frage", null);
        AiPoAgent jon = runtimes.runtime(projectId).jon();
        awaitUntil(jon::isWorking, 2_000, "Jon arbeitet an der ersten Eingabe");
        send(projectId, "zweite Frage", "Anna");

        awaitUntil(() -> history(projectId).stream()
                .anyMatch(line -> "user".equals(line.role()) && line.text().contains("[Queued Message]")),
                40_000, "die gewartete Eingabe liegt mit dem Core-Marker im Verlauf");
        assertThat(history(projectId))
                .as("der Core setzt seine Marke vor den Text, die Fabrik ihren Präfix dahinter — beides"
                        + " bleibt lesbar, der Absender auch")
                .anySatisfy(line -> {
                    assertThat(line.text()).contains("[Queued Message]").contains("zweite Frage");
                    assertThat(line.origin()).isEqualTo("Anna");
                });
        assertThat(capturedText())
                .as("beim Anbieter steht Marker und Name, weil der Core beides in einer Nachricht trägt")
                .contains("[Queued Message]")
                .contains("[User Anna]: zweite Frage");

        awaitUntil(() -> !jon.isWorking(), 30_000, "der Turn ist durch, bevor das Test-Model verschwindet");
    }

    // ------------------------------------------------------------------ edge

    private void send(String projectId, String text, String user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Void> sent = http.exchange(uri(projectId), HttpMethod.POST,
                new HttpEntity<>(new SendMessageRequest(text, user), headers), Void.class);
        assertThat(sent.getStatusCode())
                .as("eine Eingabe mit Namen wird genauso angenommen wie eine ohne")
                .isEqualTo(HttpStatus.ACCEPTED);
    }

    private List<ChatMessageDto> history(String projectId) {
        return http.exchange(uri(projectId), HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<List<ChatMessageDto>>() {
                }).getBody();
    }

    private String uri(String projectId) {
        return "http://127.0.0.1:" + port + "/api/projects/" + projectId + "/messages";
    }

    private String createProject(String slug) {
        return projects.add("https://git.example.com/acme/" + slug + ".git").project().id();
    }

    /**
     * What the model was shown as a request — the system prompt left out on purpose: it names the format
     * ({@code [User <Name>]}) even for an unnamed input, so only a request line could carry a sender.
     */
    private List<String> requestedUserTexts() {
        return model.getCapturedMessages().stream()
                .filter(message -> message instanceof UserMessage)
                .map(message -> ((UserMessage) message).singleText())
                .toList();
    }

    /** Every message the model was ever shown, as one haystack. */
    private String capturedText() {
        return model.getCapturedMessages().toString();
    }

    private static AiMessage toolCall(String id, String toolName, String arguments) {
        return new AiMessage(null,
                List.of(ToolExecutionRequest.builder().id(id).name(toolName).arguments(arguments).build()));
    }

    /** The factory's own file — the same one the settings view writes. Never {@code ~/.peon}. */
    private void writeFactoryConfig() throws IOException {
        Properties properties = new Properties();
        properties.setProperty(LlmConfigKeys.PROVIDER_TYPE, "OPEN_AI");
        properties.setProperty(LlmConfigKeys.MODEL, "sender-48");
        properties.setProperty(LlmConfigKeys.URL, model.getUrl());
        properties.setProperty(LlmConfigKeys.API_KEY, "test-key");
        try (var out = Files.newOutputStream(home.resolve(FactoryHome.CONFIG_FILE))) {
            properties.store(out, "ChatUserMessageTest");
        }
    }

    /** Polls a condition with a deadline that fails as an assertion — a wait is never a proof (Inc 5). */
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
