package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.poagent.AiPoAgent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import org.sterl.open.aifactory.project.ProjectStatusStream;

/**
 * The runtime on its own, without a server and without a model: what building it costs, and what the
 * conversation is made of. Both are R-WP-11 statements — one chat per project, no second store — so the
 * empty-team half is here as well, where it is cheap to see. The {@code !}-decision belongs here for the
 * same reason: a pure function of the typed text, needing neither a server nor a model.
 */
class ProjectRuntimeTest {

    @TempDir
    Path home;

    @TempDir
    Path checkout;

    @Test
    void buildingARuntimeWritesNothing() {
        Path stateDir = home.resolve("state").resolve("project-1");

        ProjectRuntime runtime = runtime(stateDir);

        assertThat(stateDir).doesNotExist(); // the history file is created by the core, on the first save
        assertThat(home).isEmptyDirectory();
        assertThat(runtime.jon().getName()).isEqualTo(AiPoAgent.NAME);
        assertThat(runtime.history()).isEmpty();
    }

    @Test
    void jonLeadsHisTeamAndEverySlaveStartsEmpty() {
        ProjectRuntime runtime = runtime(home.resolve("state").resolve("project-2"));

        assertThat(runtime.jon().getTeam())
                .extracting(AiAgent::getName)
                .containsExactly("Jon", "Plan", "Dev", "Review");
        assertThat(runtime.jon().getTeam())
                .allSatisfy(member -> assertThat(member.getMemory().size())
                        .as("%s starts empty", member.getName())
                        .isZero());
    }

    @Test
    // UC-WP-75
    void urgentTextIsTheCoresDecision() {
        // R-WP-42 is all of passing the text to the core's resolver and taking its answer: the `!` goes off,
        // the rest stays exactly as typed — trim of the input only, never of the body
        assertThat(ProjectRuntime.urgentTextOf("! prio", true)).contains(" prio");
        assertThat(ProjectRuntime.urgentTextOf("!", true)).contains(""); // consumed, nothing queued (R8.6)
        // a rest that opens a slash is looked up — the factory has neither commands nor skills, and an unknown
        // name still lands as raw text: two empty services, `null` there is the NullPointerException. Only the
        // `!` goes off, never the space behind it (contains of an Optional is equality, never a substring)
        assertThat(ProjectRuntime.urgentTextOf("! /unbekannt", true)).contains(" /unbekannt");
        assertThat(ProjectRuntime.urgentTextOf("!/unbekannt", true)).contains("/unbekannt");
        assertThat(ProjectRuntime.urgentTextOf("! prio", false)).isEmpty(); // R8.1: idle means `!` is text
        assertThat(ProjectRuntime.urgentTextOf("hallo", true)).isEmpty();
    }

    @Test
    // UC-WP-40, UC-WP-82
    void historyCarriesTheConversationAndItsToolSteps() {
        ProjectRuntime runtime = runtime(home.resolve("state").resolve("project-3"));
        var memory = runtime.jon().getMemory();
        String newline = System.lineSeparator();
        String answered = "ANTWORT" + newline + "Dev done. Context: 120 token - 3% used.";
        String withoutAnswer = "Dev returned no result Dev done. Context: 120 token - 3% used.";
        String failure = "Tool failed - check why and inform user java.lang.IllegalStateException: no such agent"
                + newline + "\tat org.sterl.llmpeon.example.Caller.run(Caller.java:1)";
        memory.add(UserMessage.from("first question"));
        memory.add(AiMessage.from("I will run that", List.of(ToolExecutionRequest.builder()
                .id("1").name("shellRunCommand").arguments("{\"command\":\"pwd\"}").build())));
        memory.add(result("1", "shellRunCommand", "/home/me/checkout"));
        memory.add(result("2", "askDev", answered));
        memory.add(result("3", "askDev", withoutAnswer));
        memory.add(result("4", "askDev", failure));
        memory.add(AiMessage.from("the answer"));
        memory.add(SystemMessage.from("instructions Jon never sees in the chat"));
        // the core's own marker for a waited input — shown as it is (Q6)
        memory.add(UserMessage.from("[Queued Message] (10:15): later question"));
        memory.add(AiMessage.from("second answer"));

        assertThat(runtime.history()).containsExactly(
                ChatLine.user("first question", null),
                // his own tool, standing where he asked for it: the step names the tool and shows the
                // excerpt of what came back (R-WP-47) — the AI text in front of the call is no answer
                ChatLine.tool("/home/me/checkout", "shellRunCommand"),
                // the member line is its tool result verbatim, statistics line included: shortening it
                // would be the factory's guess about what matters (R-WP-11)
                ChatLine.jon(answered, "Dev"),
                // a member that answered nothing and a member whose tool threw still have a result of
                // their own — an author is a fact, never a filter over the text (UC-WP-40)
                ChatLine.jon(withoutAnswer, "Dev"),
                ChatLine.jon(failure, "Dev"),
                ChatLine.jon("the answer", AiPoAgent.NAME),
                ChatLine.user("[Queued Message] (10:15): later question", null),
                ChatLine.jon("second answer", AiPoAgent.NAME));
    }

    @Test
    // UC-WP-82
    void aToolCallWithoutItsResultStillShowsItsArrival() {
        ProjectRuntime runtime = runtime(home.resolve("state").resolve("project-4"));
        var memory = runtime.jon().getMemory();
        memory.add(UserMessage.from("read both, fetch one"));
        memory.add(AiMessage.from("I will", List.of(
                toolRequest("1", "shellRunCommand"), toolRequest("2", "webGet"))));
        memory.add(result("1", "shellRunCommand", "/home/me/checkout"));
        // a result whose call the memory does not carry: no step is swallowed in either direction
        memory.add(result("9", "diskReadFile", "the file"));

        assertThat(runtime.history()).containsExactly(
                ChatLine.user("read both, fetch one", null),
                ChatLine.tool("/home/me/checkout", "shellRunCommand"),
                ChatLine.tool("", "webGet"), // arrival only — the excerpt is what is missing, not the step
                ChatLine.tool("the file", "diskReadFile"));
    }

    @Test
    // UC-WP-82, UC-WP-40
    void aDelegationStepNamesItsMemberAndNotItsTool() {
        ProjectRuntime runtime = runtime(home.resolve("state").resolve("project-5"));
        String answer = "the work is done" + System.lineSeparator() + "Dev done. Context: 120 token - 3% used.";
        var memory = runtime.jon().getMemory();
        memory.add(UserMessage.from("ask Dev"));
        memory.add(toolCall("1", "askDev", "{\"prompt\":\"build it\"}"));
        memory.add(result("1", "askDev", answer));
        memory.add(AiMessage.from("the answer"));

        // the step names the member and not the core's tool name (R-WP-47), and it stays an arrival: her
        // answer is one line of the conversation — the same words twice would be the factory's invention
        assertThat(runtime.history()).containsExactly(
                ChatLine.user("ask Dev", null),
                ChatLine.tool("", "Dev"),
                ChatLine.jon(answer, "Dev"),
                ChatLine.jon("the answer", AiPoAgent.NAME));
    }

    @Test
    // UC-WP-82
    void aConversationWithoutToolsGainsNoSteps() {
        ProjectRuntime runtime = runtime(home.resolve("state").resolve("project-6"));
        var memory = runtime.jon().getMemory();
        memory.add(UserMessage.from("a question"));
        memory.add(AiMessage.from("an answer"));

        assertThat(runtime.history()).extracting(ChatLine::role).containsExactly("user", "jon");
    }

    @Test
    // UC-WP-82
    void aStepExcerptIsCutToTheInsightsMeasure() {
        ProjectRuntime runtime = runtime(home.resolve("state").resolve("project-7"));
        var memory = runtime.jon().getMemory();
        memory.add(UserMessage.from("read the big file"));
        memory.add(toolCall("1", "diskReadFile", "{\"filePath\":\"/big\"}"));
        memory.add(result("1", "diskReadFile", "x".repeat(AgentTranscript.MAX_LINE_CHARS + 1)));

        // the insight's cap and no other: one measure, two readers (D-10)
        assertThat(runtime.history()).last()
                .extracting(ChatLine::text)
                .asString()
                .hasSize(AgentTranscript.MAX_LINE_CHARS + 1)
                .endsWith("…");
    }

    /** What the core hands back for one tool call — the only thing a history line is read out of. */
    private static ToolExecutionResultMessage result(String id, String toolName, String text) {
        return ToolExecutionResultMessage.from(id, toolName, text);
    }

    /** One wish of a model — the core writes the calls of one answer into a single AiMessage. */
    private static ToolExecutionRequest toolRequest(String id, String toolName) {
        return ToolExecutionRequest.builder().id(id).name(toolName).arguments("{}").build();
    }

    private static AiMessage toolCall(String id, String toolName, String arguments) {
        return new AiMessage(null, List.of(toolRequest(id, toolName).toBuilder().arguments(arguments).build()));
    }

    private ProjectRuntime runtime(Path stateDir) {
        createDirectories(checkout);
        return new ProjectRuntime("project-1", checkout, stateDir,
                ProjectRuntimeTest::config, ProjectRuntimeTest::config,
                new ChatStreamRegistry(), new ProjectStatusStream(),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    /** A config that would be accepted for a turn — no request ever goes out in this class. */
    private static LlmConfig config() {
        return LlmConfig.newOpenAi("test-model", "http://127.0.0.1:1/v1");
    }

    private static void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
