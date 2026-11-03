package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
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
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.ai.LlmConfigKeys;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.open.aifactory.FactorySpringTest;
import org.sterl.open.aifactory.chat.dto.AgentTranscriptDto;
import org.sterl.open.aifactory.chat.dto.ChatMessageDto;
import org.sterl.open.aifactory.chat.dto.SendMessageRequest;
import org.sterl.open.aifactory.chat.dto.TileCapability;
import org.sterl.open.aifactory.chat.dto.TranscriptLineDto;
import org.sterl.open.aifactory.project.ProjectRemoved;
import org.sterl.open.aifactory.shared.FactoryHome;

/**
 * The insight into a member's own chat (R-WP-26, UC-WP-49), over the real edge and a real delegation: Jon
 * calls {@code askDev} on the double, Dev runs a shell command of his own and answers, and only then does
 * this test read {@code GET …/agents/Dev}. Nothing is stubbed below {@code AiPoAgent}, so every line the
 * insight shows is a line that demonstrably happened in the member's memory.
 *
 * <p>The two halves of R-WP-26 are asserted together on purpose: the member's own work <b>is</b> visible in
 * his insight and <b>is not</b> in Jon's chat (UC-WP-40). Reading only one of the two would let a fix that
 * moves the member's text into the conversation pass as green.</p>
 *
 * <p>Waits are deadlines on an end state, never a fixed sleep: the anchor is the member's own memory
 * holding what his turn wrote, which is also what the assertion below needs.</p>
 */
@Timeout(120)
class AgentInsightTest extends FactorySpringTest {

    /** The task Jon hands over — a member's {@code user} line, spoken by nobody else. */
    private static final String MEMBER_PROMPT = "MITGLIEDS-AUFGABE: nenne genau eine Frucht";
    /** Only Dev's own shell run can carry this; it is his intermediate work, never Jon's conversation. */
    private static final String MEMBER_MARKER = "insight-mitglieds-kommando";
    private static final String MEMBER_ANSWER = "MITGLIEDS-ANTWORT: Birne";
    private static final String JON_ANSWER = "JONS-ANTWORT: die Birne";
    /** The core's own text — a line the user never spoke, so never a line of the insight. */
    private static final String SYSTEM_MARKER = "SYSTEM-PROMPT-ZEILE: darf keine Zeile sein";

    @Autowired
    private ProjectRuntimeRegistry runtimes;

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
    // UC-WP-49
    void insightReadsTheMembersOwnConversation() throws IOException {
        writeFactoryConfig("inc5c-own-chat");
        String projectId = createProject("own-chat");
        ProjectRuntime runtime = runtimes.runtime(projectId);
        scriptOneDelegation();
        send(projectId, "frag Dev nach einer Frucht");
        awaitUntil(() -> memoryText(member(runtime, AiDevAgent.NAME)).contains(MEMBER_ANSWER), 30_000,
                "Dev hat seine eigene Aufgabe zu Ende gebracht");
        // the core's identity line, straight into the member's memory: what the user never spoke cannot be
        // a line he is shown
        member(runtime, AiDevAgent.NAME).getMemory().add(SystemMessage.from(SYSTEM_MARKER));

        AgentTranscriptDto insight = transcript(projectId, AiDevAgent.NAME);

        assertThat(insight.name())
                .as("the insight names the agent whose memory it is")
                .isEqualTo(AiDevAgent.NAME);
        assertThat(kinds(insight))
                .as("the three kinds a member's own memory is made of")
                .contains("user", "tool", "agent");
        assertThat(insight.lines())
                .as("his task, his own tool call, its result and his own answer — the intermediate steps"
                        + " Jon's chat deliberately hides")
                .anySatisfy(line -> assertThat(line.kind()).isEqualTo("user"))
                .anySatisfy(line -> assertThat(line.text()).contains(MEMBER_PROMPT))
                .anySatisfy(line -> assertThat(line.text()).contains("shellRunCommand("))
                .anySatisfy(line -> assertThat(line.text()).contains(MEMBER_MARKER))
                .anySatisfy(line -> assertThat(line.text()).contains(MEMBER_ANSWER));
        assertThat(joined(insight))
                .as("no system prompt in here, although the memory holds one")
                .doesNotContain(SYSTEM_MARKER);
        assertThat(insight.truncated())
                .as("a short conversation is handed out whole")
                .isFalse();

        // write-protected the way the file area is: the edge simply has no method that writes
        assertThat(status(HttpMethod.POST, transcriptUrl(projectId, AiDevAgent.NAME)))
                .as("no POST can write into a member's memory")
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
        assertThat(status(HttpMethod.PUT, transcriptUrl(projectId, AiDevAgent.NAME)))
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
        assertThat(status(HttpMethod.DELETE, transcriptUrl(projectId, AiDevAgent.NAME)))
                .as("and nothing deletes a line of his conversation either")
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
    }

    @Test
    @Timeout(60)
    // UC-WP-49
    void membersWorkStaysOutOfTheJonChat() throws IOException {
        writeFactoryConfig("inc5c-two-places");
        String projectId = createProject("two-places");
        ProjectRuntime runtime = runtimes.runtime(projectId);
        scriptOneDelegation();
        send(projectId, "frag Dev nach einer Frucht");
        awaitUntil(() -> !runtime.jon().isWorking() && historyOf(projectId).stream()
                .anyMatch(line -> line.text().contains(JON_ANSWER)), 30_000, "Jons Turn ist zu Ende");

        String insight = joined(transcript(projectId, AiDevAgent.NAME));
        List<ChatMessageDto> chat = historyOf(projectId);
        String chatText = chat.stream().map(ChatMessageDto::text).reduce("", (left, right) -> left + "|" + right);

        assertThat(insight)
                .as("the member's intermediate work is readable where it belongs")
                .contains(MEMBER_MARKER, "shellRunCommand(");
        assertThat(chatText)
                .as("and it stays out of Jon's chat — UC-WP-40 is what the insight exists for")
                .doesNotContain(MEMBER_MARKER)
                .doesNotContain("shellRunCommand");
        assertThat(chat)
                .as("the one thing of the member that does reach the conversation is his answer, under his"
                        + " own name (UC-WP-39) — the insight took nothing away from it")
                .anySatisfy(line -> {
                    assertThat(line.origin()).isEqualTo(AiDevAgent.NAME);
                    assertThat(line.text()).contains(MEMBER_ANSWER);
                });
    }

    @Test
    @Timeout(60)
    // UC-WP-49
    void insightIsEmptyAfterARestart() {
        String projectId = createProject("restart");
        ProjectRuntime runtime = runtimes.runtime(projectId);
        member(runtime, AiDevAgent.NAME).getMemory().add(UserMessage.from("AUFGABE VOR DEM NEUSTART"));
        assertThat(transcript(projectId, AiDevAgent.NAME).lines())
                .as("before: the member's own chat is there to read")
                .isNotEmpty();

        dropRuntimeLikeARestart(projectId);

        AgentTranscriptDto afterRestart = transcript(projectId, AiDevAgent.NAME);
        assertThat(afterRestart.name())
                .as("the edge still answers the same agent")
                .isEqualTo(AiDevAgent.NAME);
        assertThat(afterRestart.lines())
                .as("a member's memory lives in the RAM of one run — the factory keeps no second store,"
                        + " so a restart answers this empty (R-WP-11)")
                .isEmpty();
        assertThat(runtimes.runtimeCount())
                .as("reading built one runtime on the same state dir, as any first read of a project does")
                .isEqualTo(1);
    }

    @Test
    @Timeout(60)
    // UC-WP-49
    void jonAndSearchHaveNoInsight() {
        String projectId = createProject("no-insight");

        assertThat(transcriptStatus(projectId, AiPoAgent.NAME))
                .as("Jon's conversation IS the chat — a second view of it would be a second conversation")
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(transcriptStatus(projectId, "Search"))
                .as("the search sub-loop owns no memory between its calls")
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(transcriptStatus(projectId, "Fantom"))
                .as("and a name that is no tile of this project never opens anything")
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(transcriptStatus("does-not-exist", AiDevAgent.NAME))
                .as("an unknown project stays a 404 — the insight adds no second project space")
                .isEqualTo(HttpStatus.NOT_FOUND.value());

        assertThat(runtimes.agents(projectId))
                .filteredOn(tile -> AiPoAgent.NAME.equals(tile.name()) || "Search".equals(tile.name()))
                .as("the refusal reads the same capability set the browser renders — one rule, one source")
                .allSatisfy(tile -> assertThat(tile.capabilities()).doesNotContain(TileCapability.TRANSCRIPT));
        assertThat(status(HttpMethod.GET, "http://127.0.0.1:" + port + "/api/projects/" + projectId
                + "/messages"))
                .as("Jon's own chat stays readable over the conversation edge, which is unchanged")
                .isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @Timeout(60)
    // UC-WP-49
    void transcriptIsBounded() {
        String projectId = createProject("bounded");
        ProjectRuntime runtime = runtimes.runtime(projectId);
        String longLine = "LANG-TEXT-ANFANG" + "x".repeat(AgentTranscript.MAX_LINE_CHARS) + "-ENDE-VERLOREN";
        // alternating roles on purpose: the core's memory folds messages of the same role into one, so
        // seeding a role twice would seed one line and prove nothing about the caps
        seedMessages(member(runtime, AiDevAgent.NAME), AgentTranscript.MAX_TRANSCRIPT_LINES - 1, "ZEILE-KURZ-");
        member(runtime, AiDevAgent.NAME).getMemory().add(AiMessage.from(longLine));
        seedMessages(member(runtime, AiPlanAgent.NAME), AgentTranscript.MAX_TRANSCRIPT_LINES + 1, "PLAN-ZEILE-");

        AgentTranscriptDto devInsight = transcript(projectId, AiDevAgent.NAME);
        assertThat(devInsight.truncated())
                .as("a long line is not the same cap as a long conversation")
                .isFalse();
        assertThat(devInsight.lines())
                .as("every message of his is a line, and the long one is cut where the cap is")
                .hasSize(AgentTranscript.MAX_TRANSCRIPT_LINES);
        TranscriptLineDto cut = devInsight.lines().get(devInsight.lines().size() - 1);
        assertThat(cut.text())
                .as("the cut is marked, so the reader never reads a truncated line as a complete one")
                .hasSize(AgentTranscript.MAX_LINE_CHARS + 1)
                .startsWith("LANG-TEXT-ANFANG")
                .doesNotContain("-ENDE-VERLOREN");

        AgentTranscriptDto planInsight = transcript(projectId, AiPlanAgent.NAME);
        List<String> planLines = planInsight.lines().stream().map(TranscriptLineDto::text).toList();
        assertThat(planInsight.truncated())
                .as("one line over the limit says so on the wire")
                .isTrue();
        assertThat(planLines)
                .as("the last lines are kept — the question a member's chat answers is about its end")
                .hasSize(AgentTranscript.MAX_TRANSCRIPT_LINES);
        assertThat(planLines.get(0))
                .as("the first of them is the second message, the oldest one fell out")
                .isEqualTo("PLAN-ZEILE-2");
        assertThat(planLines.get(planLines.size() - 1))
                .as("and the newest is there")
                .isEqualTo("PLAN-ZEILE-501");
    }

    @Test
    void anEmptyLineIsNamedInEnglish() {
        // a tool result the core cannot read as one text — the placeholder is the only word the browser
        // gets for it, so it is a UI text riding the wire (R-WP-27)
        ToolExecutionResultMessage multiPart = ToolExecutionResultMessage.builder()
                .id("call_multi")
                .toolName("shellRunCommand")
                .contents(List.of(TextContent.from("first part"), TextContent.from("second part")))
                .build();
        assertThat(multiPart.hasSingleText())
                .as("the seeded payload is the one case that needs a placeholder")
                .isFalse();

        AgentTranscriptDto insight = AgentTranscript.of(AiDevAgent.NAME, List.<ChatMessage>of(multiPart));

        assertThat(insight.lines())
                .as("the line stays a line — the reader learns there was a result")
                .hasSize(1);
        assertThat(insight.lines().get(0).text())
                .as("and it is named in English, like every other text of this product")
                .isEqualTo("shellRunCommand: [no text]");
    }

    // ------------------------------------------------------------------ the scripted delegation

    /** Jon delegates to Dev, Dev runs a shell command and answers, Jon answers the user. */
    private void scriptOneDelegation() {
        model.queueResponse(toolCall("call_dev", "askDev", "{\"prompt\":\"" + MEMBER_PROMPT + "\"}"));
        model.queueResponse(toolCall("call_dev_shell", "shellRunCommand",
                "{\"command\":\"echo " + MEMBER_MARKER + "\",\"timeout\":20}"));
        model.queueResponse(MEMBER_ANSWER);
        model.queueResponse(JON_ANSWER);
    }

    private static AiMessage toolCall(String id, String toolName, String arguments) {
        return new AiMessage(null,
                List.of(ToolExecutionRequest.builder().id(id).name(toolName).arguments(arguments).build()));
    }

    // ------------------------------------------------------------------ the edge

    private AgentTranscriptDto transcript(String projectId, String name) {
        ResponseEntity<AgentTranscriptDto> answer = http.exchange(transcriptUrl(projectId, name),
                HttpMethod.GET, null, AgentTranscriptDto.class);
        assertThat(answer.getStatusCode())
                .as("GET %s answers with the insight", transcriptUrl(projectId, name))
                .isEqualTo(HttpStatus.OK);
        return answer.getBody();
    }

    private int transcriptStatus(String projectId, String name) {
        return status(HttpMethod.GET, transcriptUrl(projectId, name));
    }

    private int status(HttpMethod method, String url) {
        return http.exchange(url, method, null, String.class).getStatusCode().value();
    }

    private String transcriptUrl(String projectId, String name) {
        return "http://127.0.0.1:" + port + "/api/projects/" + projectId + "/agents/" + name;
    }

    private List<ChatMessageDto> historyOf(String projectId) {
        return http.exchange("http://127.0.0.1:" + port + "/api/projects/" + projectId + "/messages",
                HttpMethod.GET, null, new ParameterizedTypeReference<List<ChatMessageDto>>() {
                }).getBody();
    }

    private void send(String projectId, String text) {
        ResponseEntity<String> answer = http.postForEntity(
                "http://127.0.0.1:" + port + "/api/projects/" + projectId + "/messages",
                jsonEntity(new SendMessageRequest(text)), String.class);
        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    private String createProject(String slug) {
        return projects.add("https://git.example.com/acme/" + slug + ".git").project().id();
    }

    /**
     * A restart is a new JVM with no runtime and the same {@code state/<id>}. In-process that is exactly
     * what letting go of the runtime is: the memory goes with it, the directory stays.
     */
    private void dropRuntimeLikeARestart(String projectId) {
        runtimes.onProjectRemoved(new ProjectRemoved(projectId));
        assertThat(runtimes.runtimeCount())
                .as("the runtime is gone before the read rebuilds one")
                .isZero();
    }

    private HttpEntity<Object> jsonEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private static AiAgent member(ProjectRuntime runtime, String name) {
        return runtime.jon().getTeam().stream()
                .filter(agent -> name.equals(agent.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no team member named " + name));
    }

    /**
     * A member's memory, seeded the way a conversation reads: the roles alternate, because the core's
     * memory folds messages of one role into one — two seeds of the same role are one line, and a cap
     * measured on one line would prove nothing.
     */
    private static void seedMessages(AiAgent agent, int count, String marker) {
        for (int line = 1; line <= count; line++) {
            String text = marker + line;
            agent.getMemory().add(line % 2 == 1 ? UserMessage.from(text) : AiMessage.from(text));
        }
    }

    private static String memoryText(AiAgent agent) {
        List<String> texts = new ArrayList<>();
        for (ChatMessage message : agent.getMemory().getCopy()) {
            texts.add(String.valueOf(message));
        }
        return String.join("|", texts);
    }

    private static List<String> kinds(AgentTranscriptDto insight) {
        return insight.lines().stream().map(TranscriptLineDto::kind).toList();
    }

    private static String joined(AgentTranscriptDto insight) {
        return insight.lines().stream().map(TranscriptLineDto::text).reduce("", (left, r) -> left + "|" + r);
    }

    /** The factory's own file — the same one the settings view writes. Never {@code ~/.peon}. */
    private void writeFactoryConfig(String model) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(LlmConfigKeys.PROVIDER_TYPE, "OPEN_AI");
        properties.setProperty(LlmConfigKeys.MODEL, model);
        properties.setProperty(LlmConfigKeys.URL, this.model.getUrl());
        properties.setProperty(LlmConfigKeys.API_KEY, "test-key");
        try (var out = Files.newOutputStream(HOME.resolve(FactoryHome.CONFIG_FILE))) {
            properties.store(out, "AgentInsightTest");
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
            throw new IllegalStateException("interrupted while waiting for the insight", interrupted);
        }
    }
}
