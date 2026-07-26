package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.sterl.llmpeon.shared.ChatMessageUtil;

class AbstractAgentTest {

    private StreamMock streamMock;

    @BeforeEach
    void beforeEach() {
        streamMock = new StreamMock();
    }

    /**
     * Rule 2: FIFO Queue Chaining — queued messages are consumed sequentially through call().
     */
    @Test
    void testQueuedMessagesChainedFifo() throws InterruptedException {
        // GIVEN — agent with mock model; latch to queue messages during first doCall
        CountDownLatch callStarted = new CountDownLatch(1);
        CountDownLatch canProceed = new CountDownLatch(1);

        var config = LlmConfig.builder().model("mock").build();
        var mockModel = streamMock.buildMock(r -> {
            try {
                callStarted.countDown();
                canProceed.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ChatResponse.builder().aiMessage(AiMessage.aiMessage("OK")).build();
        });

        var agent = new AiDevAgent(new ConfiguredChatModel(config, mockModel), new ToolService());

        // WHEN — start call on background thread, queue msg2+msg3 during execution
        Thread callerThread = new Thread(() -> agent.call("msg1", monitor -> { }));
        callerThread.start();

        callStarted.await(5, TimeUnit.SECONDS);
        agent.queueMessage("msg2");
        agent.queueMessage("msg3");
        canProceed.countDown();

        callerThread.join(10_000);

        // THEN — all messages processed sequentially; msg2+msg3 merge per batch window (Rule 1),
        // but each queue entry triggers its own doCall in FIFO order
        var memory = agent.getMemory().getCopy();
        List<String> userTexts = extractUserTexts(memory);
        assertThat(userTexts).hasSize(2);
        assertThat(userTexts.get(0)).contains("msg1");
        assertThat(userTexts.get(1)).contains("msg2").contains("msg3");
    }

    /**
     * Rule 4: Abort — doCall adds message to memory before LLM call, so the failing message
     * is already in memory. handleAbortAndDrain only drains messages still sitting in queue.
     */
    @Test
    void testAbortAddsMessageBeforeThrowing() throws InterruptedException {
        // GIVEN — mock succeeds on first call, throws on second; latch for synchronization
        AtomicInteger callCount = new AtomicInteger(0);
        CountDownLatch callStarted = new CountDownLatch(1);
        CountDownLatch canProceed = new CountDownLatch(1);

        var config = LlmConfig.builder().model("mock").build();
        var mockModel = streamMock.buildMock(r -> {
            int count = callCount.incrementAndGet();
            if (count == 1) {
                try {
                    callStarted.countDown();
                    canProceed.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                throw new RuntimeException("simulated abort");
            }
            return ChatResponse.builder().aiMessage(AiMessage.aiMessage("OK")).build();
        });

        var agent = new AiDevAgent(new ConfiguredChatModel(config, mockModel), new ToolService());
        AtomicReference<String> toolMessage = new AtomicReference<>();
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(org.sterl.llmpeon.tool.model.SimpleMessage m) {}
            @Override public void onTool(String message) { toolMessage.set(message); }
        };

        // WHEN — queue msg2+msg3 during first doCall, second call aborts
        Thread callerThread = new Thread(() -> agent.call("msg1", monitor));
        callerThread.start();

        callStarted.await(5, TimeUnit.SECONDS);
        agent.queueMessage("msg2");
        agent.queueMessage("msg3"); // merged into one entry by batch window
        canProceed.countDown();

        callerThread.join(10_000);

        // THEN — msg1 processed normally, msg2+msg3 added to memory before abort (doCall adds first)
        var memory = agent.getMemory().getCopy();
        List<String> userTexts = extractUserTexts(memory);
        assertThat(userTexts).hasSize(2);
        assertThat(userTexts.get(0)).isEqualTo("msg1");
        assertThat(userTexts.get(1)).contains("msg2").contains("msg3");

        // AND — no drain TOOL message (queue was empty when abort hit — pollNext already consumed)
        assertThat(toolMessage.get()).isNull();
        // AND — working flag cleared by finally block
        assertThat(agent.isWorking()).isFalse();
    }

    /**
     * Rule 4: Abort drains messages still in queue AFTER the failing message was consumed.
     * Uses latch on second doCall to queue additional messages between pollNext and doCall throw.
     */
    @Test
    void testAbortDrainsRemainingQueue() throws InterruptedException {
        // GIVEN — mock succeeds once, then waits before throwing; lets us queue during 2nd iteration
        AtomicInteger callCount = new AtomicInteger(0);
        CountDownLatch firstDoCallDone = new CountDownLatch(1);
        CountDownLatch secondDoCallStarted = new CountDownLatch(1);
        CountDownLatch canProceedFirst = new CountDownLatch(1);
        CountDownLatch canProceedSecond = new CountDownLatch(1);

        var config = LlmConfig.builder().model("mock").build();
        var mockModel = streamMock.buildMock(r -> {
            int count = callCount.incrementAndGet();
            if (count == 1) {
                // first doCall: signal completion, wait for release
                try {
                    canProceedFirst.await(5, TimeUnit.SECONDS);
                    firstDoCallDone.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else if (count == 2) {
                // second doCall: signal start, wait for extra queue, then throw
                try {
                    secondDoCallStarted.countDown();
                    canProceedSecond.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new RuntimeException("simulated abort");
            }
            return ChatResponse.builder().aiMessage(AiMessage.aiMessage("OK")).build();
        });

        var agent = new AiDevAgent(new ConfiguredChatModel(config, mockModel), new ToolService());
        AtomicReference<String> toolMessage = new AtomicReference<>();
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(org.sterl.llmpeon.tool.model.SimpleMessage m) {}
            @Override public void onTool(String message) { toolMessage.set(message); }
        };

        // Pre-queue msg2 — will be consumed by pollNext after first doCall succeeds
        agent.queueMessage("msg2");

        Thread callerThread = new Thread(() -> agent.call("msg1", monitor));
        callerThread.start();

        // Release first doCall → it succeeds, pollNext picks up "msg2"
        canProceedFirst.countDown();
        firstDoCallDone.await(5, TimeUnit.SECONDS);

        // Wait for second doCall to start (message already in memory), then queue msg3+msg4
        secondDoCallStarted.await(5, TimeUnit.SECONDS);
        agent.queueMessage("msg3");
        agent.queueMessage("msg4"); // merged into one entry by batch window
        canProceedSecond.countDown(); // trigger abort

        callerThread.join(10_000);

        // THEN — msg1 processed, msg2 added before abort, msg3+msg4 drained from queue.
        // ThreadSafeMemory merges consecutive UserMessages (msg2 + drained msg3+msg4).
        var memory = agent.getMemory().getCopy();
        List<String> userTexts = extractUserTexts(memory);
        assertThat(userTexts).hasSize(2);
        assertThat(userTexts.get(0)).contains("msg1");

        // msg2 + drained msg3+msg4 merged by ThreadSafeMemory (consecutive UserMessages)
        String combined = userTexts.get(1);
        assertThat(combined).contains("msg2").contains("msg3").contains("msg4");

        // AND — TOOL message shows 1 preserved queue entry (msg3+msg4 merged)
        assertThat(toolMessage.get()).isEqualTo("1 queued message(s) preserved for your next request.");
    }

    private List<String> extractUserTexts(List<ChatMessage> messages) {
        return messages.stream()
                .filter(m -> m instanceof UserMessage)
                .map(UserMessage.class::cast)
                .map(u -> ChatMessageUtil.toString(u.contents()).stripTrailing())
                .toList();
    }
}
