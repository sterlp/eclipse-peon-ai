package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.FILE_BLOCK;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.PIC_A_HEAD;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.file;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.fixed1432;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.picA;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.agent.model.UserInput;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.memory.FileAgentHistoryStore;
import org.sterl.llmpeon.memory.HistoryFailureFixture;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.queuedmessages.UserMessageQueue;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/** queued-user-messages Regel 13, see {@code ThreadSafeMemory.add(ChatMessage, Runnable)}. */
@Timeout(1)
class QueuedInputRamCommitTest {

    private static final ImageContent IMG_A = ImageContent.from("QUJD", "image/png", ImageContent.DetailLevel.HIGH);
    private static final UserMessage MIXED = UserMessage.from(List.of(
            TextContent.from("[Queued Message] (14:32): schau mal"),
            TextContent.from(FILE_BLOCK), TextContent.from(PIC_A_HEAD), IMG_A));
    private static final AiMessage SEED_CALL = AiMessage.builder().toolExecutionRequests(List.of(
            ToolExecutionRequest.builder().id("seed").name("echo").arguments("{}").build())).build();
    private static final ToolExecutionResultMessage SEED_RESULT =
            ToolExecutionResultMessage.from("seed", "echo", "out");

    enum Branch { APPEND, USER_JOIN, AFTER_TOOL_RESULT }

    @TempDir
    Path dir;

    private final List<ChatRequest> requests = new ArrayList<>();
    private final List<String> toolLines = new ArrayList<>();
    private final List<String> callStarts = new ArrayList<>();
    private final UserMessageQueue queue = new UserMessageQueue(10_000, fixed1432());
    private Path historyFile;
    private HistoryFailureFixture store;
    private ThreadSafeMemory memory;
    private List<RuntimeException> contextFailures = new ArrayList<>();
    private int contextCalls;

    private final AiMonitor monitor = new AiMonitor() {
        @Override public void onChatResponse(SimpleMessage m) {
            if (m.role() == SimpleMessage.Type.TOOL) toolLines.add(m.message());
        }
        @Override public void onCallStart(String text) { callStarts.add(text); }
    };

    private AbstractAgent agent() throws IOException {
        historyFile = dir.resolve("history.jsonl");
        store = new HistoryFailureFixture(historyFile, dir);
        memory = new ThreadSafeMemory(store);
        store.observeRam(memory::getCopy);
        var model = new StreamMock().buildMock(r -> {
            requests.add(r);
            return ChatResponse.builder().aiMessage(AiMessage.from("never")).build();
        });
        var agent = new AbstractAgent(new ConfiguredChatModel(LlmConfig.builder().model("mock").build(), model),
                new ToolService(), memory) {
            @Override public String getName() { return "test"; }
            @Override public String getSystemPrompt() { return "test"; }
        };
        agent.setMessageQueue(queue);
        agent.setTurnContextSupplier(() -> {
            if (contextCalls < contextFailures.size()) throw contextFailures.get(contextCalls++);
            return List.<ContextItem>of();
        });
        return agent;
    }

    private List<ChatMessage> restored() {
        return new ThreadSafeMemory(new FileAgentHistoryStore(historyFile)).getCopy();
    }

    // queued-user-messages Regel 13
    // R-TR-11
    // R-TR-12
    @ParameterizedTest
    @EnumSource(Branch.class)
    void historyFailureAfterTurnRamAddNeverPreservesTheInputAgain(Branch branch) throws IOException {
        var agent = agent();
        List<ChatMessage> seeds = switch (branch) {
            case APPEND -> List.of();
            case USER_JOIN -> {
                memory.add(UserMessage.from("seed"));
                yield List.of(UserMessage.from("seed"));
            }
            case AFTER_TOOL_RESULT -> {
                memory.addResult(ChatResponse.builder().aiMessage(SEED_CALL).build(), List.of(SEED_RESULT), List.of());
                yield List.of(SEED_CALL, SEED_RESULT);
            }
        };
        queue.add(new UserInput("schau mal", List.of(file(), picA())));
        store.arm();

        assertThatThrownBy(() -> agent.call(UserInput.of(null), monitor))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(branch == Branch.USER_JOIN ? "Failed to persist chat history" : "Failed to append chat history")
                .hasCauseInstanceOf(IOException.class);

        List<ChatMessage> expected = switch (branch) {
            case APPEND -> List.of(MIXED);
            case USER_JOIN -> List.of(UserMessage.from(List.of(TextContent.from("seed"),
                    TextContent.from("[Queued Message] (14:32): schau mal"),
                    TextContent.from(FILE_BLOCK), TextContent.from(PIC_A_HEAD), IMG_A)));
            case AFTER_TOOL_RESULT -> List.of(SEED_CALL, SEED_RESULT, AiMessage.from("ok"), MIXED);
        };
        assertThat(memory.getCopy()).isEqualTo(expected);
        assertThat(store.attempts).hasSize(1);
        assertThat(store.attempts.getFirst().type()).isEqualTo(branch == Branch.USER_JOIN ? "PERSIST" : "APPEND");
        assertThat(store.attempts.getFirst().ramAtAttempt()).isEqualTo(expected);
        assertThat(requests).isEmpty();
        assertThat(callStarts).isEmpty();
        assertThat(toolLines).isEmpty();
        assertThat(agent.getQueuedMessageCount()).isZero();
        assertThat(agent.isWorking()).isFalse();
        assertThat(memory.isPersistent()).isFalse();
        assertThat(restored()).isEqualTo(seeds);
    }

    // queued-user-messages Regel 4
    // queued-user-messages Regel 13
    // R-TR-11
    // R-TR-12
    @Test
    void contextFailureBeforeRamAddKeepsTheWholeTakenInput() throws IOException {
        var failure = new IllegalStateException("context failed");
        contextFailures.add(failure);
        var agent = agent();
        queue.add(new UserInput("schau mal", List.of(file(), picA())));

        assertThatThrownBy(() -> agent.call(UserInput.of(null), monitor)).isSameAs(failure);

        var expected = UserMessage.from(List.of(TextContent.from("schau mal"),
                TextContent.from(FILE_BLOCK), TextContent.from(PIC_A_HEAD), IMG_A));
        assertThat(memory.getCopy()).isEqualTo(List.of(expected));
        assertThat(requests).isEmpty();
        assertThat(store.attempts).hasSize(1);
        assertThat(store.attempts.getFirst().type()).isEqualTo("APPEND");
        assertThat(store.attempts.getFirst().written()).isEqualTo(List.of(expected));
        assertThat(toolLines).containsExactly("1 queued message(s) preserved for your next request.");
        assertThat(agent.getQueuedMessageCount()).isZero();
        assertThat(agent.isWorking()).isFalse();
        assertThat(restored()).isEqualTo(List.of(expected));
    }

    // queued-user-messages Regel 4
    // queued-user-messages Regel 13
    // R-TR-11
    // R-TR-12
    @Test
    void historyFailureAfterAbortRamAddCannotLeakPendingIntoTheNextFailedTurn() throws IOException {
        var first = new IllegalStateException("context failed 1");
        var second = new IllegalStateException("context failed 2");
        contextFailures.add(first);
        contextFailures.add(second);
        var agent = agent();
        queue.add(new UserInput("schau mal", List.of(file(), picA())));
        store.arm();

        assertThatThrownBy(() -> agent.call(UserInput.of(null), monitor))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to append chat history")
                .hasCauseInstanceOf(IOException.class);
        assertThatThrownBy(() -> agent.call("next", monitor)).isSameAs(second);

        var expected = UserMessage.from(List.of(TextContent.from("schau mal"),
                TextContent.from(FILE_BLOCK), TextContent.from(PIC_A_HEAD), IMG_A));
        assertThat(memory.getCopy()).isEqualTo(List.of(expected));
        assertThat(store.attempts).hasSize(1);
        assertThat(store.attempts.getFirst().type()).isEqualTo("APPEND");
        assertThat(store.attempts.getFirst().ramAtAttempt()).isEqualTo(List.of(expected));
        assertThat(memory.isPersistent()).isFalse();
        assertThat(requests).isEmpty();
        assertThat(toolLines).isEmpty();
        assertThat(agent.getQueuedMessageCount()).isZero();
        assertThat(agent.isWorking()).isFalse();
        assertThat(restored()).isEmpty();
    }
}
