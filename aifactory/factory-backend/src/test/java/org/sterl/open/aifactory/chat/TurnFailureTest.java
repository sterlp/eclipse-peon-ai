package org.sterl.open.aifactory.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.poagent.AiPoAgent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;

import org.sterl.open.aifactory.chat.dto.AgentAction;
import org.sterl.open.aifactory.config.TurnNotConfiguredException;
import org.sterl.open.aifactory.project.ProjectStatusStream;

/**
 * A turn that could not run leaves two lines behind (R-WP-34, UC-WP-64): what was typed and why nothing
 * answered. Both are written into the memory the conversation is already kept in, so a reload, a second
 * browser and the next model call all read the same two lines — there is no second store to forget them.
 *
 * <p>The failing half of a turn needs no model: reading the config is what throws, and that is the seam a
 * plain {@link ProjectRuntime} can be built on. Only the stop needs a real turn, and for it a real shell
 * tool that is slow enough to be stopped — the pattern of {@code AgentStopTest}.</p>
 */
@Timeout(90)
class TurnFailureTest {

    /** The reason the missing config names — the whole of what the failure line shows. */
    private static final String REASON = "no model is configured for this project";
    private static final String PROJECT = "project-1";
    private static final String INPUT = "RICHTUNG: Wohin mit der Datei?";
    private static final String SLEEP_TOOL = "{\"command\":\"sleep 3\",\"timeout\":20}";
    private static final String SLOW_INPUT = "LAUF: Schlaf drei Sekunden";
    private static final String ANSWER_AFTER_THE_STOP = "DIESE-ANTWORT-DARF-NICHT-MEHR-KOMMEN";

    @TempDir
    Path home;

    @TempDir
    Path checkout;

    private ExecutorService turns;

    @BeforeEach
    void createExecutor() {
        this.turns = Executors.newVirtualThreadPerTaskExecutor();
    }

    @AfterEach
    void closeExecutor() {
        turns.shutdown();
    }

    @Test
    // UC-WP-64
    void failedTurnStaysInTheHistory() {
        ProjectRuntime runtime = runtimeThatCannotBeConfigured();

        runtime.submit(INPUT, "Paul");

        awaitUntil(() -> runtime.history().size() == 2, 20_000, "the failed turn wrote its two lines");
        assertThat(runtime.history()).containsExactly(
                ChatLine.user(INPUT, "Paul"),
                ChatLine.error(REASON));
    }

    @Test
    // UC-WP-64
    void theErrorLineCarriesNoAuthorAndNoJonRole() {
        ProjectRuntime runtime = runtimeThatCannotBeConfigured();

        runtime.submit(INPUT, "Paul");

        awaitUntil(() -> runtime.history().size() == 2, 20_000, "the failed turn wrote its two lines");
        ChatLine failure = runtime.history().get(1);
        assertThat(failure.role())
                .as("a failure is nobody's speech, so it is not Jon's line")
                .isEqualTo(ChatLine.ROLE_ERROR);
        assertThat(failure.origin())
                .as("and no agent is named for it either (R-WP-4)")
                .isNull();
        assertThat(failure.text())
                .as("the line is the reason alone — the marker is the server's bookkeeping")
                .isEqualTo(REASON);
        assertThat(runtime.history())
                .extracting(ChatLine::role)
                .doesNotContain(ChatLine.ROLE_JON);
    }

    @Test
    // UC-WP-64
    void thePayloadIsWrittenOnlyOnce() {
        ProjectRuntime runtime = runtimeThatCannotBeConfigured();

        runtime.submit(INPUT, "Paul");
        awaitUntil(() -> runtime.history().size() == 2, 20_000, "the first turn failed");
        runtime.submit(INPUT, "Paul");
        awaitUntil(() -> runtime.history().size() == 3, 20_000, "the second turn failed");

        // two failures are two failure lines; the input is one line because the memory holds it once —
        // the same way the core merges two inputs that arrive one behind the other
        assertThat(runtime.history()).containsExactly(
                ChatLine.user(INPUT, "Paul"),
                ChatLine.error(REASON),
                ChatLine.error(REASON));
    }

    @Test
    // UC-WP-64
    void failedTurnReleasesTheProject() {
        ProjectRuntime runtime = runtimeThatCannotBeConfigured();
        runtime.submit(INPUT, "Paul");
        awaitUntil(() -> runtime.history().size() == 2, 20_000, "the failed turn wrote its two lines");

        assertThatThrownBy(runtime::stop)
                .as("the turn that failed is over — a stop after it addresses nothing")
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> runtime.act(AiPoAgent.NAME, AgentAction.CLEAR))
                .as("and a tile action is taken again: the failed turn gave the project back")
                .doesNotThrowAnyException();
    }

    @Test
    @Timeout(60)
    // UC-WP-64
    void stopIsNoFailure() {
        MockLlmServer model = new MockLlmServer(0);
        model.start();
        try {
            ChatStreamRegistry streams = new ChatStreamRegistry();
            RecordedWire wire = new RecordedWire();
            streams.subscribe(PROJECT, wire);
            ProjectRuntime runtime = runtime(() -> openAiAt(model.getUrl()), streams);
            model.queueResponse(toolCall("call_sleep", "shellRunCommand", SLEEP_TOOL));
            model.queueResponse(ANSWER_AFTER_THE_STOP);

            runtime.submit(SLOW_INPUT, "Paul");
            awaitUntil(wire::reportsToolLine, 20_000, "the running shell reported its command");

            runtime.stop();
            awaitUntil(() -> !runtime.jon().isWorking(), 30_000, "the turn ended on the stop");

            assertThat(runtime.history())
                    .as("his own end is not a failure — no line of it enters the conversation")
                    .noneMatch(line -> ChatLine.ROLE_ERROR.equals(line.role()));
            assertThat(runtime.history())
                    .as("what he typed stays, and the answer he canceled is never asked for again")
                    .containsExactly(ChatLine.user(SLOW_INPUT, "Paul"));
        } finally {
            model.stop();
        }
    }

    // ------------------------------------------------------------------ fixtures

    /** The runtime of a project whose config cannot serve a turn — the failure that needs no model. */
    private ProjectRuntime runtimeThatCannotBeConfigured() {
        return runtime(() -> {
            throw new TurnNotConfiguredException(REASON);
        });
    }

    private ProjectRuntime runtime(Supplier<LlmConfig> configForTurn) {
        return runtime(configForTurn, new ChatStreamRegistry());
    }

    private ProjectRuntime runtime(Supplier<LlmConfig> configForTurn, ChatStreamRegistry streams) {
        createDirectories(checkout);
        return new ProjectRuntime(PROJECT, checkout, home.resolve("state").resolve(PROJECT),
                () -> openAiAt("http://127.0.0.1:1/v1"), configForTurn,
                streams, new ProjectStatusStream(), turns);
    }

    /** The live wire of this test's project: the tool line is what tells a running shell from a turn that never got there. */
    private static final class RecordedWire extends SseEmitter {

        private final List<String> events = new CopyOnWriteArrayList<>();

        RecordedWire() {
            super(0L);
        }

        @Override
        public void send(SseEventBuilder event) {
            StringBuilder text = new StringBuilder();
            event.build().forEach(piece -> text.append(piece.getData()));
            this.events.add(text.toString());
        }

        boolean reportsToolLine() {
            return events.stream().anyMatch(event -> event.contains("Running:"));
        }
    }

    private static LlmConfig openAiAt(String url) {
        return LlmConfig.newOpenAi("failure-model", url).toBuilder().apiKey("test-key").build();
    }

    private static AiMessage toolCall(String id, String toolName, String arguments) {
        return new AiMessage(null,
                List.of(ToolExecutionRequest.builder().id(id).name(toolName).arguments(arguments).build()));
    }

    private static void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void awaitUntil(BooleanSupplier condition, long timeoutMs, String what) {
        long until = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis())
                    .as("%s happened within %d ms", what, timeoutMs)
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
