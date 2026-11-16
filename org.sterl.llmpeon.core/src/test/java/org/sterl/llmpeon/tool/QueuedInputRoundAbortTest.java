package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.FILE_BLOCK;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.NL;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.PIC_A_HEAD;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.PIC_B_HEAD;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.file;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.fixed1432;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.picA;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.picB;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.types;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.agent.AbstractAgent;
import org.sterl.llmpeon.agent.AgentQueueSeam;
import org.sterl.llmpeon.agent.QueuedMessageDelivery;
import org.sterl.llmpeon.agent.model.TextAttachment;
import org.sterl.llmpeon.agent.model.UserInput;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.exception.ExceptionUtil;
import org.sterl.llmpeon.memory.FileAgentHistoryStore;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.queuedmessages.UserMessageQueue;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.streaming.AiCancellationException;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Taken is not delivered (queued-user-messages Regel 13), tool-round side: what a tool took out of
 * the queue stays preserved until the round's results are in the memory. Real agent, real
 * {@link QueuedMessageDelivery}, real loop and history file; the fake tools only play the plugin's
 * role and the user's Stop.
 */
@Timeout(1)
class QueuedInputRoundAbortTest {

    private static final ImageContent IMG_A = ImageContent.from("QUJD", "image/png", ImageContent.DetailLevel.HIGH);
    private static final ImageContent IMG_B = ImageContent.from("REVG", "image/png", ImageContent.DetailLevel.HIGH);
    private static final String FILE_B = "//-- Start file: b.txt" + NL + "world" + NL + "//-- End file: b.txt";
    private static final String BLOCK_A = "//-- Start queued_user_message" + NL
            + "(queued 14:32): schau mal" + NL + FILE_BLOCK + NL
            + "//-- End queued_user_message" + NL + "yes";

    @TempDir
    Path dir;

    private final List<ChatRequest> requests = new ArrayList<>();
    private final List<String> toolLines = new ArrayList<>();
    private final List<String> answers = new ArrayList<>();
    private final List<String> called = new ArrayList<>();
    private final UserMessageQueue queue = new UserMessageQueue(10_000, fixed1432());
    private final ToolService service = new ToolService(false);
    private Path historyFile;
    private ThreadSafeMemory memory;
    private boolean cancelOnFirstReading;

    private final AiMonitor monitor = new AiMonitor() {
        @Override public void onChatResponse(SimpleMessage m) {
            if (m.role() != SimpleMessage.Type.TOOL) return;
            toolLines.add(m.message());
            if (cancelOnFirstReading && m.message().startsWith("Reading queued")) {
                cancelOnFirstReading = false;
                throw new AiCancellationException("stop", null, null);
            }
        }
    };

    class RoundTools implements SmartTool {
        private ToolLoopRequest request;

        @Tool("fake askUser: the answer is yes")
        public String askUser() {
            called.add("askUser");
            var answer = new QueuedMessageDelivery().prependToAnswer(request.getAgent(), request.getMonitor(), "yes",
                    request::appendAfterResults);
            answers.add(answer);
            return answer;
        }

        @Tool("fake second question: enqueues another input, then answers")
        public String second() {
            called.add("second");
            queue.add(new UserInput("zwei", List.of(new TextAttachment("b.txt", "world"), picB())));
            return askUser();
        }

        @Tool("fake echo")
        public String echo() { called.add("echo"); return "out"; }

        @Tool("fake: the user stops the round")
        public String cancelLater() {
            called.add("cancelLater");
            throw new CancellationException("Canceled question");
        }

        @Override public void withToolRequest(ToolLoopRequest request) { this.request = request; }
    }

    /** Request 1 answers with these tool calls, request 2 runs {@code onSecond} and ends the turn. */
    private AbstractAgent agent(Runnable onFirst, Runnable onSecond, String... tools) {
        historyFile = dir.resolve("history.jsonl");
        memory = new ThreadSafeMemory(new FileAgentHistoryStore(historyFile));
        var model = new StreamMock().buildMock(r -> {
            requests.add(r);
            if (requests.size() > 1) {
                onSecond.run();
                return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
            }
            onFirst.run();
            var calls = new ArrayList<ToolExecutionRequest>();
            for (int i = 0; i < tools.length; i++) {
                calls.add(ToolExecutionRequest.builder().id(String.valueOf(i + 1)).name(tools[i]).arguments("{}").build());
            }
            return ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(calls).build()).build();
        });
        service.addTool(new RoundTools());
        var agent = new AbstractAgent(new ConfiguredChatModel(LlmConfig.builder().model("mock").build(), model),
                service, memory) {
            @Override public String getName() { return "test"; }
            @Override public String getSystemPrompt() { return "test"; }
        };
        AgentQueueSeam.setMessageQueue(agent, queue);
        return agent;
    }

    private List<ChatMessage> restored() {
        return new ThreadSafeMemory(new FileAgentHistoryStore(historyFile)).getCopy();
    }

    private static List<Content> contentsOf(ChatMessage m) {
        return ((UserMessage) m).contents();
    }

    private List<ChatMessage> secondRequest() {
        return requests.get(1).messages().stream().filter(m -> !(m instanceof SystemMessage)).toList();
    }

    private static UserInput mixed() {
        return new UserInput("schau mal", List.of(file(), picA()));
    }

    // queued-user-messages Regel 4
    // queued-user-messages Regel 12
    // queued-user-messages Regel 13
    // R-TR-11
    // R-TR-12
    @Test
    void askUserDeliveredThenLaterToolCanceledPreservesTextFileAndPicture() {
        var agent = agent(() -> queue.add(mixed()), () -> {}, "askUser", "cancelLater");

        assertThatThrownBy(() -> agent.call("start", monitor)).matches(ExceptionUtil::isCanceled);

        assertThat(called).containsExactly("askUser", "cancelLater");
        assertThat(answers).containsExactly(BLOCK_A);
        assertThat(requests).hasSize(1);
        var all = memory.getCopy();
        assertThat(types(all)).containsExactly("USER");
        var expected = List.of(TextContent.from("start"), TextContent.from("schau mal"),
                TextContent.from(FILE_BLOCK), TextContent.from(PIC_A_HEAD), IMG_A);
        assertThat(contentsOf(all.getLast())).isEqualTo(expected);
        assertThat(toolLines).containsExactly("Reading queued User message: schau mal (queued 14:32)",
                "1 queued message(s) preserved for your next request.");
        assertThat(agent.getQueuedMessageCount()).isZero();
        assertThat(agent.isWorking()).isFalse();
        assertThat(types(restored())).isEqualTo(types(all));
        assertThat(contentsOf(restored().getLast())).isEqualTo(expected);
    }

    // queued-user-messages Regel 4
    // queued-user-messages Regel 12
    // queued-user-messages Regel 13
    // R-TR-11
    // R-TR-12
    @Test
    void twoSuccessfulDeliveriesBeforeLaterCancelArePreservedInConsumptionOrder() {
        var agent = agent(() -> queue.add(mixed()), () -> {}, "askUser", "second", "cancelLater");

        assertThatThrownBy(() -> agent.call("start", monitor)).matches(ExceptionUtil::isCanceled);

        assertThat(called).containsExactly("askUser", "second", "askUser", "cancelLater");
        assertThat(answers).containsExactly(BLOCK_A,
                "//-- Start queued_user_message" + NL
                + "(queued 14:32): zwei" + NL + FILE_B + NL
                + "//-- End queued_user_message" + NL + "yes");
        assertThat(requests).hasSize(1);
        var all = memory.getCopy();
        assertThat(types(all)).containsExactly("USER");
        var expected = List.of(TextContent.from("start"), TextContent.from("schau mal" + NL + "zwei"),
                TextContent.from(FILE_BLOCK), TextContent.from(PIC_A_HEAD), IMG_A,
                TextContent.from(FILE_B), TextContent.from(PIC_B_HEAD), IMG_B);
        assertThat(contentsOf(all.getLast())).isEqualTo(expected);
        assertThat(toolLines).containsExactly(
                "Reading queued User message: schau mal (queued 14:32)",
                "Reading queued User message: zwei (queued 14:32)",
                "2 queued message(s) preserved for your next request.");
        assertThat(agent.getQueuedMessageCount()).isZero();
        assertThat(contentsOf(restored().getLast())).isEqualTo(expected);
    }

    // queued-user-messages Regel 4
    // queued-user-messages Regel 12
    // queued-user-messages Regel 13
    // R-TR-11
    // R-TR-12
    // R-RP-1
    // UC-RP-2
    @Test
    void abortAfterRoundMemoryAddDoesNotPreserveCommittedDeliveryTwice() {
        var agent = agent(() -> queue.add(mixed()), () -> {
            queue.add("later");
            throw new AiCancellationException("stop", null, null);
        }, "askUser", "echo");

        assertThatThrownBy(() -> agent.call("start", monitor)).matches(ExceptionUtil::isCanceled);

        assertThat(called).containsExactly("askUser", "echo");
        assertThat(requests).hasSize(2);
        var sent = secondRequest();
        assertThat(types(sent)).containsExactly("USER", "AI", "TOOL_EXECUTION_RESULT", "TOOL_EXECUTION_RESULT",
                "AI", "USER");
        assertThat(((ToolExecutionResultMessage) sent.get(2)).text()).isEqualTo(BLOCK_A);
        assertThat(contentsOf(sent.get(5))).containsExactly(TextContent.from(PIC_A_HEAD), IMG_A);
        var all = memory.getCopy();
        assertThat(types(all)).isEqualTo(types(sent));
        assertThat(((ToolExecutionResultMessage) all.get(2)).text()).isEqualTo(BLOCK_A);
        assertThat(contentsOf(all.getFirst())).containsExactly(TextContent.from("start"));
        var expectedLast = List.of(TextContent.from(PIC_A_HEAD), IMG_A, TextContent.from("later"));
        assertThat(contentsOf(all.getLast())).isEqualTo(expectedLast);
        assertThat(toolLines).containsExactly("Reading queued User message: schau mal (queued 14:32)",
                "1 queued message(s) preserved for your next request.");
        assertThat(agent.getQueuedMessageCount()).isZero();
        assertThat(contentsOf(restored().getLast())).isEqualTo(expectedLast);
    }

    // queued-user-messages Regel 4
    // queued-user-messages Regel 8
    // queued-user-messages Regel 11
    // queued-user-messages Regel 13
    // R-TR-11
    // R-TR-12
    @Test
    void urgentDisclosureCanceledAfterTakePreservesTheTakenEntry() {
        cancelOnFirstReading = true;
        var agent = agent(() -> {
            queue.addUrgent("urgent text");
            queue.add(mixed());
        }, () -> {}, "echo");

        assertThatThrownBy(() -> agent.call("start", monitor)).matches(ExceptionUtil::isCanceled);

        assertThat(called).containsExactly("echo");
        assertThat(requests).hasSize(1);
        var all = memory.getCopy();
        assertThat(types(all)).containsExactly("USER");
        var expected = List.of(TextContent.from("start"), TextContent.from("urgent text" + NL + "schau mal"),
                TextContent.from(FILE_BLOCK), TextContent.from(PIC_A_HEAD), IMG_A);
        assertThat(contentsOf(all.getLast())).isEqualTo(expected);
        assertThat(toolLines).containsExactly("Reading queued User message: urgent text (queued 14:32)",
                "2 queued message(s) preserved for your next request.");
        assertThat(agent.getQueuedMessageCount()).isZero();
        assertThat(contentsOf(restored().getLast())).isEqualTo(expected);
    }
}
