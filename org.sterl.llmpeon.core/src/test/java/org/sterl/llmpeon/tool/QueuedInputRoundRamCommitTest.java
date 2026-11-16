package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.FILE_BLOCK;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.NL;
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
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.agent.AbstractAgent;
import org.sterl.llmpeon.agent.AgentQueueSeam;
import org.sterl.llmpeon.agent.QueuedMessageDelivery;
import org.sterl.llmpeon.agent.model.UserInput;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.FileAgentHistoryStore;
import org.sterl.llmpeon.memory.HistoryFailureFixture;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
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

/** queued-user-messages Regel 13, see {@code ThreadSafeMemory.add(ChatMessage, Runnable)}. */
@Timeout(1)
class QueuedInputRoundRamCommitTest {

    private static final ImageContent IMG_A = ImageContent.from("QUJD", "image/png", ImageContent.DetailLevel.HIGH);
    private static final String ANSWER = "//-- Start queued_user_message" + NL
            + "(queued 14:32): schau mal" + NL + FILE_BLOCK + NL
            + "//-- End queued_user_message" + NL + "yes";

    @TempDir
    Path dir;

    private final List<ChatRequest> requests = new ArrayList<>();
    private final List<String> toolLines = new ArrayList<>();
    private final List<String> called = new ArrayList<>();
    private final List<String> answers = new ArrayList<>();
    private final UserMessageQueue queue = new UserMessageQueue(10_000, fixed1432());
    private HistoryFailureFixture store;

    class Tools implements SmartTool {
        private ToolLoopRequest request;

        @Tool("fake askUser: the answer is yes")
        public String askUser() {
            called.add("askUser");
            var answer = new QueuedMessageDelivery().prependToAnswer(request.getAgent(), request.getMonitor(), "yes",
                    request::appendAfterResults);
            answers.add(answer);
            store.arm(); // the next write — the round's append — fails for real
            return answer;
        }

        @Override public void withToolRequest(ToolLoopRequest request) { this.request = request; }
    }

    // queued-user-messages Regel 12
    // queued-user-messages Regel 13
    // R-TR-11
    // R-TR-12
    // R-RP-1
    // UC-RP-2
    @Test
    void historyFailureAfterCompleteRoundRamAddDoesNotRepeatAskUserDelivery() throws IOException {
        var historyFile = dir.resolve("history.jsonl");
        store = new HistoryFailureFixture(historyFile, dir);
        var memory = new ThreadSafeMemory(store);
        store.observeRam(memory::getCopy);
        var model = new StreamMock().buildMock(r -> {
            requests.add(r);
            queue.add(new UserInput("schau mal", List.of(file(), picA())));
            return ChatResponse.builder().aiMessage(AiMessage.builder().toolExecutionRequests(List.of(
                    ToolExecutionRequest.builder().id("id1").name("askUser").arguments("{}").build())).build()).build();
        });
        var service = new ToolService(false);
        service.addTool(new Tools());
        var agent = new AbstractAgent(new ConfiguredChatModel(LlmConfig.builder().model("mock").build(), model),
                service, memory) {
            @Override public String getName() { return "test"; }
            @Override public String getSystemPrompt() { return "test"; }
        };
        AgentQueueSeam.setMessageQueue(agent, queue);
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {
                if (m.role() == SimpleMessage.Type.TOOL) toolLines.add(m.message());
            }
        };

        assertThatThrownBy(() -> agent.call("start", monitor))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to append chat history")
                .hasCauseInstanceOf(IOException.class);

        var call = AiMessage.builder().toolExecutionRequests(List.of(
                ToolExecutionRequest.builder().id("id1").name("askUser").arguments("{}").build())).build();
        List<ChatMessage> expected = List.of(
                UserMessage.from("start"),
                call,
                ToolExecutionResultMessage.from("id1", "askUser", ANSWER),
                AiMessage.from("ok"),
                UserMessage.from(List.of(TextContent.from(PIC_A_HEAD), IMG_A)));
        assertThat(memory.getCopy()).isEqualTo(expected);
        assertThat(called).containsExactly("askUser");
        assertThat(answers).containsExactly(ANSWER);
        assertThat(requests).hasSize(1);
        assertThat(store.attempts).hasSize(1);
        assertThat(store.attempts.getFirst().type()).isEqualTo("APPEND");
        assertThat(store.attempts.getFirst().written()).isEqualTo(expected.subList(1, 5));
        assertThat(store.attempts.getFirst().ramAtAttempt()).isEqualTo(expected);
        assertThat(toolLines).containsExactly("Reading queued User message: schau mal (queued 14:32)");
        assertThat(agent.getQueuedMessageCount()).isZero();
        assertThat(agent.isWorking()).isFalse();
        assertThat(memory.isPersistent()).isFalse();
        assertThat(new ThreadSafeMemory(new FileAgentHistoryStore(historyFile)).getCopy())
                .isEqualTo(List.of(UserMessage.from("start")));
    }
}
