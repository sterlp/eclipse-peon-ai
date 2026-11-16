package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.FILE_BLOCK;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.NL;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.PIC_A_HEAD;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.file;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.fixed1432;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.picA;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.userMessages;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.agent.model.UserInput;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.context.SimpleContextItem;
import org.sterl.llmpeon.queuedmessages.UserMessageQueue;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/** The public typed entrance of an agent turn: one message from context, text and attachments (R-TR-11). */
@Timeout(1)
class UserInputCallTest {

    private static final ImageContent IMG_A = ImageContent.from("QUJD", "image/png", ImageContent.DetailLevel.HIGH);
    private static final String CONTEXT_BLOCK =
            "//-- Start context: lbl" + NL + "ctx" + NL + "//-- End context: lbl";

    /** An agent over its own stub model; {@code duringCall} sees the 1-based number of every model call. */
    private static AiDevAgent agent(List<ChatRequest> requests, Consumer<Integer> duringCall, UserMessageQueue queue) {
        var model = new StreamMock().buildMock(r -> {
            requests.add(r);
            duringCall.accept(requests.size());
            return ChatResponse.builder().aiMessage(AiMessage.from("OK")).build();
        });
        var agent = new AiDevAgent(new ConfiguredChatModel(LlmConfig.builder().model("mock").build(), model),
                new ToolService());
        if (queue != null) agent.setMessageQueue(queue);
        return agent;
    }

    // R-TR-11
    @Test
    void publicCallBuildsOneMessageWithContextTextAndAttachments() {
        var requests = new ArrayList<ChatRequest>();
        var started = new ArrayList<String>();
        var agent = agent(requests, c -> {}, null);
        agent.setTurnContextSupplier(() -> List.of(new SimpleContextItem("lbl", "ctx")));

        agent.call(new UserInput("question", List.of(file(), picA())), new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onCallStart(String userMessage) { started.add(userMessage); }
        });

        assertThat(userMessages(agent)).hasSize(1);
        assertThat(userMessages(agent).getFirst().contents()).containsExactly(
                TextContent.from(CONTEXT_BLOCK),
                TextContent.from("question"),
                TextContent.from(FILE_BLOCK),
                TextContent.from(PIC_A_HEAD),
                IMG_A);
        assertThat(requests).hasSize(1);
        assertThat(started).containsExactly("question");
    }

    // R-TR-11
    @Test
    void stringCallAndUserInputOfProduceIdenticalRequests() {
        for (String text : new String[] { "hello", null }) {
            var viaString = new ArrayList<ChatRequest>();
            var viaInput = new ArrayList<ChatRequest>();
            var startedString = new ArrayList<String>();
            var startedInput = new ArrayList<String>();
            var a = agent(viaString, c -> {}, null);
            var b = agent(viaInput, c -> {}, null);
            a.setTurnContextSupplier(() -> List.of(new SimpleContextItem("lbl", "ctx")));
            b.setTurnContextSupplier(() -> List.of(new SimpleContextItem("lbl", "ctx")));

            a.call(text, new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
                @Override public void onCallStart(String m) { startedString.add(m); }
            });
            b.call(UserInput.of(text), new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
                @Override public void onCallStart(String m) { startedInput.add(m); }
            });

            assertThat(viaInput).as("requests for %s", text).hasSize(1);
            assertThat(viaInput.getFirst().messages()).isEqualTo(viaString.getFirst().messages());
            assertThat(b.getMemory().getCopy()).isEqualTo(a.getMemory().getCopy());
            assertThat(startedInput).isEqualTo(startedString);
            var contents = userMessages(b).getFirst().contents();
            assertThat(contents).isEqualTo(text == null
                    ? List.of(TextContent.from(CONTEXT_BLOCK))
                    : List.of(TextContent.from(CONTEXT_BLOCK), TextContent.from("hello")));
        }
    }

    // queued-user-messages Regel 10
    // queued-user-messages Regel 13
    // R-TR-11
    @Test
    void busyTypedCallReturnsNullAndItsAttachmentsAreAnsweredByTheOwnerTurn() {
        var requests = new ArrayList<ChatRequest>();
        var queue = new UserMessageQueue(10_000, fixed1432());
        var results = new ArrayList<ChatResponse>();
        var workingDuring = new ArrayList<Boolean>();
        AiAgent[] self = new AiAgent[1];
        var agent = agent(requests, call -> {
            if (call == 1) {
                results.add(self[0].call(new UserInput("busy", List.of(file(), picA())), m -> {}));
                workingDuring.add(self[0].isWorking());
            }
        }, queue);
        self[0] = agent;

        agent.call("start", m -> {});

        assertThat(results).containsExactly((ChatResponse) null);
        assertThat(workingDuring).containsExactly(true);
        assertThat(requests).hasSize(2);
        assertThat(userMessages(agent)).hasSize(2);
        assertThat(userMessages(agent).get(1).contents()).containsExactly(
                TextContent.from("[Queued Message] (14:32): busy"),
                TextContent.from(FILE_BLOCK),
                TextContent.from(PIC_A_HEAD),
                IMG_A);
        assertThat(agent.getQueuedMessageCount()).isZero();
        assertThat(agent.isWorking()).isFalse();
    }
}
