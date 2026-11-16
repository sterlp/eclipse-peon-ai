package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.FILE_BLOCK;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.NL;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.PIC_A_HEAD;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.file;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.fixed1432;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.picA;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.steppingMinutes;
import static org.sterl.llmpeon.agent.QueuedInputFixtures.userMessages;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.agent.model.UserInput;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.queuedmessages.UserMessageQueue;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.streaming.AiCancellationException;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * The turn consumers of the typed queue — follow-up, turn start, abort drain — build their one user
 * message from the WHOLE queued input (queued-user-messages Regel 13). Inputs arrive from inside the
 * first model call, i.e. provably while the turn works; the stub model runs on the caller's thread.
 */
@Timeout(1)
class QueuedInputTurnTest {

    private static final ImageContent IMG_A = ImageContent.from("QUJD", "image/png", ImageContent.DetailLevel.HIGH);

    private final List<ChatRequest> requests = new ArrayList<>();
    private final List<String> toolLines = new ArrayList<>();
    private final AiMonitor monitor = new AiMonitor() {
        @Override public void onChatResponse(SimpleMessage m) {
            if (m.role() == SimpleMessage.Type.TOOL) toolLines.add(m.message());
        }
    };

    /** An agent whose FIRST model call runs {@code duringFirstCall}; every call answers OK unless {@code failure} says otherwise. */
    private AiDevAgent agent(UserMessageQueue queue, Consumer<Integer> duringCall) {
        var model = new StreamMock().buildMock(r -> {
            requests.add(r);
            duringCall.accept(requests.size());
            return ChatResponse.builder().aiMessage(AiMessage.from("OK")).build();
        });
        var agent = new AiDevAgent(new ConfiguredChatModel(LlmConfig.builder().model("mock").build(), model),
                new ToolService());
        agent.setMessageQueue(queue);
        return agent;
    }

    // queued-user-messages Regel 2
    // queued-user-messages Regel 9
    // queued-user-messages Regel 13
    // R-TR-11
    @Test
    void followUpUsesTheWholeQueuedInput() {
        var queue = new UserMessageQueue(10_000, fixed1432());
        var agent = agent(queue, call -> {
            if (call == 1) queue.add(new UserInput("schau mal", List.of(file(), picA())));
        });

        agent.call("start", monitor);

        assertThat(requests).hasSize(2);
        var users = userMessages(agent);
        assertThat(users).hasSize(2);
        assertThat(users.get(1).contents()).containsExactly(
                TextContent.from("[Queued Message] (14:32): schau mal"),
                TextContent.from(FILE_BLOCK),
                TextContent.from(PIC_A_HEAD),
                IMG_A);
        assertThat(toolLines).containsExactly("Reading queued User message: schau mal (queued 14:32)");
        assertThat(agent.getQueuedMessageCount()).isZero();
    }

    // queued-user-messages Regel 13
    // R-TR-11
    @Test
    void queuedStartWithOrWithoutInitialTextKeepsAllAttachments() {
        var q1 = new UserMessageQueue(10_000, fixed1432());
        var withText = agent(q1, call -> {});
        queueBeforeTurn(q1);
        withText.call("initial", monitor);

        assertThat(userMessages(withText)).hasSize(1);
        assertThat(userMessages(withText).getFirst().contents()).containsExactly(
                TextContent.from("q1" + NL + "q2" + NL + "initial"),
                TextContent.from(FILE_BLOCK),
                TextContent.from(PIC_A_HEAD),
                IMG_A);

        var q2 = new UserMessageQueue(10_000, fixed1432());
        var withoutText = agent(q2, call -> {});
        queueBeforeTurn(q2);
        withoutText.call((String) null, monitor);

        assertThat(userMessages(withoutText)).hasSize(1);
        assertThat(userMessages(withoutText).getFirst().contents()).containsExactly(
                TextContent.from("[Queued Message] (14:32): q1" + NL + "q2"),
                TextContent.from(FILE_BLOCK),
                TextContent.from(PIC_A_HEAD),
                IMG_A);
        assertThat(requests).hasSize(2); // one model call per turn, nothing queued is answered twice
    }

    private static void queueBeforeTurn(UserMessageQueue queue) {
        queue.add("q1");
        queue.add(new UserInput("q2", List.of(file(), picA())));
    }

    static Stream<RuntimeException> aborts() {
        return Stream.of(new AiCancellationException("stop", null, null), new IllegalStateException("boom"),
                new RateLimitException("limit"));
    }

    // queued-user-messages Regel 4
    // queued-user-messages Regel 13
    @ParameterizedTest
    @MethodSource("aborts")
    void abortPreservesAllInputPartsWithoutStartingAnotherCall(RuntimeException failure) {
        var queue = new UserMessageQueue(10_000, fixed1432());
        var model = new StreamMock().buildMock(r -> {
            requests.add(r);
            queue.add("q1");
            queue.add(new UserInput("q2", List.of(file(), picA())));
            throw failure;
        });
        var agent = new AiDevAgent(new ConfiguredChatModel(LlmConfig.builder().model("mock").build(), model),
                new ToolService());
        agent.setMessageQueue(queue);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> agent.call("start", monitor)).isSameAs(failure);

        // memory joins consecutive user messages: the failed turn's "start" and the preserved input are one
        var users = userMessages(agent);
        assertThat(users).hasSize(1);
        assertThat(users.getFirst().contents()).containsExactly(
                TextContent.from("start"),
                TextContent.from("q1" + NL + "q2"),
                TextContent.from(FILE_BLOCK),
                TextContent.from(PIC_A_HEAD),
                IMG_A);
        assertThat(toolLines).containsExactly("2 queued message(s) preserved for your next request.");
        assertThat(requests).hasSize(1);
        assertThat(agent.getQueuedMessageCount()).isZero();
        assertThat(agent.isWorking()).isFalse();
    }

    // queued-user-messages Regel 4
    // queued-user-messages Regel 13
    @Test
    void cancellationBetweenPollingAndFollowUpDoesNotLoseThePolledInput() {
        var queue = new UserMessageQueue(10_000, steppingMinutes());
        var canceled = new boolean[1];
        var cancelOnDisclosure = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {
                if (m.role() != SimpleMessage.Type.TOOL) return;
                toolLines.add(m.message());
                if (m.message().startsWith("Reading queued User message:")) canceled[0] = true;
            }
            @Override public boolean isCanceled() { return canceled[0]; }
        };
        var agent = agent(queue, call -> {
            if (call == 1) {
                queue.add(new UserInput("see", List.of(file(), picA())));
                queue.add("later");
            }
        });

        agent.call("start", cancelOnDisclosure);

        assertThat(requests).as("no further LLM call after the cancel").hasSize(1);
        var users = userMessages(agent);
        assertThat(users).hasSize(2); // "start" was answered (AI message between), the preserved input follows
        assertThat(users.get(1).contents()).containsExactly(
                TextContent.from("see" + NL + "later"),
                TextContent.from(FILE_BLOCK),
                TextContent.from(PIC_A_HEAD),
                IMG_A);
        assertThat(toolLines).containsExactly(
                "Reading queued User message: see (queued 14:32)",
                "2 queued message(s) preserved for your next request.");
        assertThat(agent.getQueuedMessageCount()).isZero();
    }

    // queued-user-messages Regel 5
    // queued-user-messages Regel 13
    @Test
    void clearResetsTypedQueueAndBatchBarrier() {
        var queue = new UserMessageQueue(10_000, fixed1432());
        var agent = agent(queue, call -> {});
        queue.add(new UserInput("stale", List.of(file(), picA())));
        assertThat(agent.getQueuedMessageCount()).isEqualTo(1);

        agent.clear();

        assertThat(agent.getQueuedMessageCount()).isZero();
        assertThat(agent.queueMessage("a")).isTrue();
        assertThat(agent.queueMessage("b")).as("a fresh burst batches again").isFalse();
        agent.clear();
        agent.call("fresh", monitor);

        assertThat(requests).hasSize(1);
        assertThat(userMessages(agent)).hasSize(1);
        assertThat(userMessages(agent).getFirst().contents()).containsExactly(TextContent.from("fresh"));
    }
}
