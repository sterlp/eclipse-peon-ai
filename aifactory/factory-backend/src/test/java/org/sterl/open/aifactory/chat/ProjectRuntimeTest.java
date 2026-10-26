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
    void historyIsTheMemoryFilteredToUserAndJonLines() {
        ProjectRuntime runtime = runtime(home.resolve("state").resolve("project-3"));
        var memory = runtime.jon().getMemory();
        memory.add(UserMessage.from("first question"));
        memory.add(AiMessage.from("I will run that", List.of(ToolExecutionRequest.builder()
                .id("1").name("shellRunCommand").arguments("{\"command\":\"pwd\"}").build())));
        memory.add(AiMessage.from("the answer"));
        memory.add(SystemMessage.from("instructions Jon never sees in the chat"));
        // the core's own marker for a waited input — shown as it is (Q6)
        memory.add(UserMessage.from("[Queued Message] (10:15): later question"));
        memory.add(AiMessage.from("second answer"));

        assertThat(runtime.history()).containsExactly(
                ChatLine.user("first question"),
                ChatLine.jon("the answer", "Jon"),
                ChatLine.user("[Queued Message] (10:15): later question"),
                ChatLine.jon("second answer", "Jon"));
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
