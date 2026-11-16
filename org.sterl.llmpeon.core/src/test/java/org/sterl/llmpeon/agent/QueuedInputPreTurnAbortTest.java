package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.FILE_BLOCK;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.NL;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.PIC_A_HEAD;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.file;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.fixed1432;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.picA;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.agent.model.TextAttachment;
import org.sterl.llmpeon.agent.model.UserInput;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.exception.ExceptionUtil;
import org.sterl.llmpeon.memory.FileAgentHistoryStore;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.queuedmessages.UserMessageQueue;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.streaming.AiCancellationException;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Taken is not delivered (queued-user-messages Regel 13): an input the turn took out of the queue is
 * kept until it really is in the memory — a Stop inside the pre-turn compact must not lose it.
 */
@Timeout(1)
class QueuedInputPreTurnAbortTest {

    private static final ImageContent IMG_A = ImageContent.from("QUJD", "image/png", ImageContent.DetailLevel.HIGH);
    private static final String PRESERVED_2 = "2 queued message(s) preserved for your next request.";
    private static final String INITIAL_FILE =
            "//-- Start file: initial.txt" + NL + "init" + NL + "//-- End file: initial.txt";

    @TempDir
    Path dir;

    private final List<ChatRequest> requests = new ArrayList<>();
    private final List<String> toolLines = new ArrayList<>();
    private Path historyFile;
    private ThreadSafeMemory memory;

    private final AiMonitor monitor = new AiMonitor() {
        @Override public void onChatResponse(SimpleMessage m) {
            if (m.role() == SimpleMessage.Type.TOOL) toolLines.add(m.message());
        }
    };

    private AbstractAgent agent(UserMessageQueue queue, boolean cancelCompact, int firstMainInputTokens,
            Runnable duringFirstMainCall) {
        historyFile = dir.resolve("history.jsonl");
        memory = new ThreadSafeMemory(new FileAgentHistoryStore(historyFile));
        memory.add(UserMessage.from("m1")).add(AiMessage.from("m2")).add(UserMessage.from("m3"));
        memory.addResult(ChatResponse.builder().aiMessage(AiMessage.from("m4"))
                .tokenUsage(new TokenUsage(cancelCompact ? 90_000 : 10, 0, 90_010)).build());
        var model = new StreamMock().buildMock(r -> {
            requests.add(r);
            if ("compact-model".equals(r.modelName())) throw new AiCancellationException("stop", null, null);
            if (requests.size() == 1) duringFirstMainCall.run();
            return ChatResponse.builder().aiMessage(AiMessage.from("OK"))
                    .tokenUsage(new TokenUsage(firstMainInputTokens, 1, firstMainInputTokens + 1)).build();
        });
        var config = LlmConfig.builder().model("mock").autoCompactAfter(80_000)
                .modelConfigs(Map.of(AgentModelConfig.COMPACT,
                        new AgentModelConfig(null, null, "compact-model", null, null, null)))
                .build();
        var agent = new AbstractAgent(new ConfiguredChatModel(config, model), new ToolService(), memory) {
            @Override public String getName() { return "test"; }
            @Override public String getSystemPrompt() { return "test"; }
        };
        agent.setMessageQueue(queue);
        return agent;
    }

    private static List<Content> contentsOf(ChatMessage m) {
        return ((UserMessage) m).contents();
    }

    private static ChatMessage lastUser(List<ChatMessage> all) {
        return all.stream().filter(UserMessage.class::isInstance).reduce((a, b) -> b).orElseThrow();
    }

    private List<ChatMessage> restored() {
        return new ThreadSafeMemory(new FileAgentHistoryStore(historyFile)).getCopy();
    }

    private static List<String> types(List<ChatMessage> messages) {
        return messages.stream().map(m -> m.type().name()).toList();
    }

    // queued-user-messages Regel 4
    // queued-user-messages Regel 13
    // R-TR-11
    // R-TR-12
    // R-CC-20
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void queueStartCanceledDuringPreTurnCompactPreservesEveryInput(boolean withInitial) {
        var queue = new UserMessageQueue(10_000, fixed1432());
        var agent = agent(queue, true, 0, () -> {});
        queue.add(new UserInput("schau mal", List.of(file(), picA())));
        queue.add("danach");
        var initial = withInitial
                ? new UserInput("initial", List.of(new TextAttachment("initial.txt", "init")))
                : UserInput.of(null);

        assertThatThrownBy(() -> agent.call(initial, monitor)).matches(ExceptionUtil::isCanceled);

        assertThat(requests.stream().map(ChatRequest::modelName)).containsExactly("compact-model");
        assertThat(agent.getQueuedMessageCount()).isZero();
        assertThat(agent.isWorking()).isFalse();
        var all = memory.getCopy();
        var expected = new ArrayList<Content>(List.of(
                TextContent.from("schau mal" + NL + "danach" + (withInitial ? NL + "initial" : "")),
                TextContent.from(FILE_BLOCK), TextContent.from(PIC_A_HEAD), IMG_A));
        if (withInitial) expected.add(TextContent.from(INITIAL_FILE));
        assertThat(contentsOf(lastUser(all))).isEqualTo(expected);
        assertThat(types(all)).containsExactly("USER", "AI", "USER", "AI", "USER");
        assertThat(toolLines.stream().filter(l -> l.contains("preserved"))).containsExactly(PRESERVED_2);
        var back = restored();
        assertThat(types(back)).isEqualTo(types(all));
        assertThat(contentsOf(back.getLast())).isEqualTo(expected);
    }

    // queued-user-messages Regel 2
    // queued-user-messages Regel 4
    // queued-user-messages Regel 13
    // R-TR-11
    // R-TR-12
    // R-CC-20
    @Test
    void polledFollowUpCanceledDuringPreTurnCompactPreservesItAndTheWaitingRemainder() {
        var queue = new UserMessageQueue(10_000, fixed1432());
        var agent = agent(queue, false, 85_000, () -> {
            queue.add(new UserInput("schau mal", List.of(file(), picA())));
            queue.add("danach");
        });

        assertThatThrownBy(() -> agent.call("start", monitor)).matches(ExceptionUtil::isCanceled);

        assertThat(requests.stream().map(ChatRequest::modelName)).containsExactly("mock", "compact-model");
        assertThat(agent.getQueuedMessageCount()).isZero();
        assertThat(agent.isWorking()).isFalse();
        var all = memory.getCopy();
        assertThat(types(all)).containsExactly("USER", "AI", "USER", "AI", "USER", "AI", "USER");
        assertThat(((AiMessage) all.get(5)).text()).isEqualTo("OK");
        var expected = List.of(
                TextContent.from("schau mal" + NL + "danach"),
                TextContent.from(FILE_BLOCK), TextContent.from(PIC_A_HEAD), IMG_A);
        assertThat(contentsOf(all.getLast())).isEqualTo(expected);
        assertThat(toolLines.stream().filter(l -> l.contains("queued"))).containsExactly(
                "Reading queued User message: schau mal (queued 14:32)", PRESERVED_2);
        assertThat(contentsOf(restored().getLast())).isEqualTo(expected);
    }

    // queued-user-messages Regel 4
    // queued-user-messages Regel 13
    // R-TR-11
    // R-TR-12
    @Test
    void abortAfterTurnMemoryAddDoesNotPreserveTheWrittenInputTwice() {
        var queue = new UserMessageQueue(10_000, fixed1432());
        var agent = agent(queue, false, 0, () -> {});
        queue.add(new UserInput("schau mal", List.of(file(), picA())));
        var afterAdd = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {
                if (m.role() == SimpleMessage.Type.TOOL) toolLines.add(m.message());
            }
            @Override public void onCallStart(String text) {
                queue.add("later");
                throw new AiCancellationException("stop", null, null);
            }
        };

        assertThatThrownBy(() -> agent.call((String) null, afterAdd)).matches(ExceptionUtil::isCanceled);

        assertThat(requests).isEmpty();
        var all = memory.getCopy();
        assertThat(types(all)).containsExactly("USER", "AI", "USER", "AI", "USER");
        var expected = List.of(
                TextContent.from("[Queued Message] (14:32): schau mal"),
                TextContent.from(FILE_BLOCK), TextContent.from(PIC_A_HEAD), IMG_A,
                TextContent.from("later"));
        assertThat(contentsOf(all.getLast())).isEqualTo(expected);
        assertThat(toolLines).containsExactly("1 queued message(s) preserved for your next request.");
        assertThat(agent.getQueuedMessageCount()).isZero();
        assertThat(contentsOf(restored().getLast())).isEqualTo(expected);
    }
}
