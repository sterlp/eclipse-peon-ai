package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.FILE_BLOCK;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.NL;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.PIC_A_HEAD;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.PIC_B_HEAD;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.file;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.picA;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.picB;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.steppingMinutes;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.types;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.userMessages;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.agent.AgentQueueSeam;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.QueuedMessageDelivery;
import org.sterl.llmpeon.agent.model.UserInput;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.exception.ExceptionUtil;
import org.sterl.llmpeon.queuedmessages.UserMessageQueue;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * askUser's queue delivery through the real agent, the real {@link QueuedMessageDelivery} and the real
 * loop: the fake tool only plays the plugin's role (queued-user-messages Regel 12/13, R-RP-1). Every
 * assertion reads what the loop sent to the model.
 */
@Timeout(1)
class QueuedInputDeliveryLoopTest {

    private static final String LN = NL;
    private static final ImageContent IMG_A = ImageContent.from("QUJD", "image/png", ImageContent.DetailLevel.HIGH);
    private static final ImageContent IMG_B = ImageContent.from("REVG", "image/png", ImageContent.DetailLevel.HIGH);

    private final List<ChatRequest> requests = new ArrayList<>();
    private final List<String> toolLines = new ArrayList<>();
    private final AiMonitor monitor = new AiMonitor() {
        @Override public void onChatResponse(SimpleMessage m) {
            if (m.role() == SimpleMessage.Type.TOOL) toolLines.add(m.message());
        }
    };
    private final ToolService service = new ToolService(false);
    private final UserMessageQueue queue = new UserMessageQueue(10_000, steppingMinutes());

    /** The plugin's askUser role: answer "yes", delegating to the production delivery; or cancel. */
    static class AskUserFake implements SmartTool {
        private final boolean cancel;
        private ToolLoopRequest request;

        AskUserFake(boolean cancel) { this.cancel = cancel; }

        @Tool("fake askUser: the answer is yes")
        public String askUser() {
            if (cancel) throw new CancellationException("Canceled question");
            return new QueuedMessageDelivery().prependToAnswer(request.getAgent(), request.getMonitor(), "yes",
                    request::appendAfterResults);
        }

        @Tool("fake echo")
        public String echo() { return "out"; }

        @Override public void withToolRequest(ToolLoopRequest request) { this.request = request; }
    }

    /** Round 1 (queue filled just before) answers with these tool calls, round 2 ends the turn. */
    private AiDevAgent agent(Consumer<UserMessageQueue> fillQueue, String... tools) {
        var model = new StreamMock().buildMock(r -> {
            requests.add(r);
            if (requests.size() > 1) return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
            fillQueue.accept(queue);
            var calls = new ArrayList<ToolExecutionRequest>();
            for (int i = 0; i < tools.length; i++) {
                calls.add(ToolExecutionRequest.builder().id(String.valueOf(i + 1)).name(tools[i]).arguments("{}").build());
            }
            return ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(calls).build()).build();
        });
        var agent = new AiDevAgent(new ConfiguredChatModel(LlmConfig.builder().model("mock").build(), model), service);
        AgentQueueSeam.setMessageQueue(agent, queue);
        return agent;
    }

    private List<ChatMessage> secondRequestWithoutSystem() {
        return requests.get(1).messages().stream().filter(m -> !(m instanceof dev.langchain4j.data.message.SystemMessage)).toList();
    }

    // queued-user-messages Regel 12
    // queued-user-messages Regel 13
    // R-TR-10
    // R-TR-12
    // R-RP-1
    // UC-RP-2
    @Test
    void askUserPrependsTextFilesAndAppendsPicturesAfterEveryResult() {
        service.addTool(new AskUserFake(false));
        var agent = agent(q -> {
            q.add(new UserInput("n1", List.of(file())));                  // 14:32
            q.addUrgent("u1");                                           // 14:33
            q.add(new UserInput("look", List.of(picA())));               // 14:34
            q.add(new UserInput(null, List.of(picB())));                 // 14:35
        }, "askUser", "echo");

        agent.call("start", monitor);

        var sent = secondRequestWithoutSystem();
        assertThat(types(sent)).containsExactly("USER", "AI", "TOOL_EXECUTION_RESULT", "TOOL_EXECUTION_RESULT",
                "AI", "USER", "AI", "USER");
        assertThat(((ToolExecutionResultMessage) sent.get(2)).text()).isEqualTo(
                "//-- Start queued_user_message" + LN
                + "(queued 14:32): n1" + LN + FILE_BLOCK + LN
                + "(queued 14:33): u1" + LN
                + "(queued 14:34): look" + LN
                + "(queued 14:35): (attachment only)" + LN
                + "//-- End queued_user_message" + LN
                + "yes");
        assertThat(((ToolExecutionResultMessage) sent.get(3)).text()).isEqualTo("out");
        assertThat(((AiMessage) sent.get(4)).text()).isEqualTo("ok");
        assertThat(((UserMessage) sent.get(5)).contents())
                .containsExactly(TextContent.from(PIC_A_HEAD), IMG_A);
        assertThat(((AiMessage) sent.get(6)).text()).isEqualTo("ok");
        assertThat(((UserMessage) sent.get(7)).contents())
                .containsExactly(TextContent.from(PIC_B_HEAD), IMG_B);
        assertThat(toolLines).containsExactly(
                "Reading queued User message: n1 (queued 14:32)",
                "Reading queued User message: u1 (queued 14:33)",
                "Reading queued User message: look (queued 14:34)",
                "Reading queued User message: (attachment only) (queued 14:35)");
        assertThat(agent.getQueuedMessageCount()).isZero();
        assertThat(requests).as("consumption is final: no follow-up turn").hasSize(2);
    }

    // queued-user-messages Regel 4
    // queued-user-messages Regel 12
    // queued-user-messages Regel 13
    @Test
    void canceledAskUserKeepsAttachmentsForTheNormalAbortDrain() {
        service.addTool(new AskUserFake(true));
        var agent = agent(q -> {
            q.add(new UserInput("see", List.of(file(), picA())));
        }, "askUser");

        assertThatThrownBy(() -> agent.call("start", monitor))
                .satisfies(e -> assertThat(ExceptionUtil.isCanceled(e)).as("a cancel, wrapped by the tool layer").isTrue());

        assertThat(requests).hasSize(1);
        var users = userMessages(agent);
        assertThat(users).hasSize(1); // the memory joins "start" and the preserved input
        assertThat(users.getFirst().contents()).containsExactly(
                TextContent.from("start"),
                TextContent.from("see"),
                TextContent.from(FILE_BLOCK),
                TextContent.from(PIC_A_HEAD),
                IMG_A);
        assertThat(toolLines).containsExactly("1 queued message(s) preserved for your next request.");
        assertThat(agent.getQueuedMessageCount()).isZero();
    }

    // queued-user-messages Regel 8
    // queued-user-messages Regel 11
    // queued-user-messages Regel 12
    // queued-user-messages Regel 13
    @Test
    void emptyDeliveryAndUrgentOnlyDeliveryPreserveExistingResults() {
        service.addTool(new AskUserFake(false));

        // empty queue: the answer stays exactly as it was, nothing queued behind the results
        var empty = agent(q -> {}, "askUser");
        empty.call("start", monitor);
        var sent = secondRequestWithoutSystem();
        assertThat(types(sent)).containsExactly("USER", "AI", "TOOL_EXECUTION_RESULT");
        assertThat(((ToolExecutionResultMessage) sent.getLast()).text()).isEqualTo("yes");
        assertThat(toolLines).isEmpty();

        // urgent only: ONE result carries the block (Regel 11), no [Queued Message] afterwards
        requests.clear();
        var urgent = agent(q -> {
            q.addUrgent("u1");
            q.addUrgent("u2");
        }, "echo", "echo");
        urgent.call("start", monitor);
        sent = secondRequestWithoutSystem();
        assertThat(types(sent)).containsExactly("USER", "AI", "TOOL_EXECUTION_RESULT", "TOOL_EXECUTION_RESULT");
        assertThat(((ToolExecutionResultMessage) sent.get(2)).text()).isEqualTo("out" + LN + LN
                + "//-- Start queued_user_message" + LN
                + "(queued 14:32): u1" + LN
                + "(queued 14:33): u2" + LN
                + "//-- End queued_user_message");
        assertThat(((ToolExecutionResultMessage) sent.get(3)).text()).isEqualTo("out");
        assertThat(toolLines).containsExactly(
                "Reading queued User message: u1 (queued 14:32)",
                "Reading queued User message: u2 (queued 14:33)");
        assertThat(requests).hasSize(2);
        assertThat(urgent.getQueuedMessageCount()).isZero();
    }
}
