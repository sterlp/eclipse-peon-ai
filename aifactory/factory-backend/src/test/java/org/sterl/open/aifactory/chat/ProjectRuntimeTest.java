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
import org.sterl.llmpeon.agent.NamedAgent;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.poagent.AiPoAgent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * The runtime on its own, without a server and without a model: what building it costs, and what the
 * conversation is made of. Both are R-WP-11 statements — one chat per project, no second store — so the
 * empty-team half is here as well, where it is cheap to see.
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
                .extracting(NamedAgent::uiName)
                .containsExactly("Da Boss", "Da Thinka", "Da Mek", "Da Dok");
        assertThat(runtime.jon().getTeam())
                .allSatisfy(member -> assertThat(member.agent().getMemory().size())
                        .as("%s starts empty", member.uiName())
                        .isZero());
    }

    @Test
    // UC-WP-40
    void historyIsTheMemoryFilteredToUserAndJonLines() {
        ProjectRuntime runtime = runtime(home.resolve("state").resolve("project-3"));
        var memory = runtime.jon().getMemory();
        String newline = System.lineSeparator();
        String answered = "ANTWORT" + newline + "Da Mek done. Context: 120 token - 3% used.";
        String withoutAnswer = "Da Mek returned no result Da Mek done. Context: 120 token - 3% used.";
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
                ChatLine.user("first question"),
                // the member line is its tool result verbatim, statistics line included: shortening it
                // would be the factory's guess about what matters (R-WP-11)
                ChatLine.jon(answered, "Da Mek"),
                // a member that answered nothing and a member whose tool threw still have a result of
                // their own — an author is a fact, never a filter over the text (UC-WP-40)
                ChatLine.jon(withoutAnswer, "Da Mek"),
                ChatLine.jon(failure, "Da Mek"),
                ChatLine.jon("the answer", AiPoAgent.NAME),
                ChatLine.user("[Queued Message] (10:15): later question"),
                ChatLine.jon("second answer", AiPoAgent.NAME));
    }

    /** What the core hands back for one tool call — the only thing a history line is read out of. */
    private static ToolExecutionResultMessage result(String id, String toolName, String text) {
        return ToolExecutionResultMessage.from(id, toolName, text);
    }

    private ProjectRuntime runtime(Path stateDir) {
        createDirectories(checkout);
        return new ProjectRuntime("project-1", checkout, stateDir,
                ProjectRuntimeTest::config, ProjectRuntimeTest::config,
                new ChatStreamRegistry(), Executors.newVirtualThreadPerTaskExecutor());
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
