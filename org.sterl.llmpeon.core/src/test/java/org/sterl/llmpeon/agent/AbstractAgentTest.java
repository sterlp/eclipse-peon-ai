package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.FileAgentHistoryStore;
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
        assertThat(userTexts.get(1)).contains("msg2", "msg3");
        
    }

    /**
     * Rule 4: Abort — doCall adds message to memory before LLM call, so the failing message
     * is already in memory. handleAbortAndDrain only drains messages still sitting in queue.
     */
    @Test
    void testAbortAddsMessageBeforeThrowing() throws InterruptedException {
        // GIVEN — mock succeeds on first call, throws on second; latch for synchronization
        CountDownLatch callStarted = new CountDownLatch(1);
        CountDownLatch canProceed = new CountDownLatch(1);

        List<ChatMessage> sendMsg = new ArrayList<>();
        var config = LlmConfig.builder().model("mock").build();
        var mockModel = streamMock.buildMock(r -> {
            callStarted.countDown();
            sendMsg.addAll(r.messages());
            try {
                canProceed.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new CancellationException("Abbort");
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

        // AND
        agent.queueMessage("msg2");
        agent.call("msg3", monitor); // merged into one entry by batch window
        // AND go
        canProceed.countDown();

        callerThread.join(999910_000);

        // THEN
        assertThat(sendMsg).hasSize(2);
        assertThat(ChatMessageUtil.toString(sendMsg.getLast())).contains("msg1");
        assertThat(ChatMessageUtil.toString(sendMsg.getLast())).doesNotContain("msg2");
        assertThat(ChatMessageUtil.toString(sendMsg.getLast())).doesNotContain("msg3");

        // AND
        var memory = agent.getMemory().getCopy();
        List<String> userTexts = extractUserTexts(memory);
        assertThat(userTexts).hasSize(1);
        assertThat(userTexts.get(0)).contains("msg1", "msg2", "msg3");

        // AND — no drain TOOL message (queue was empty when abort hit — pollNext already consumed)
        assertThat(toolMessage.get()).contains("1 queued message");
        // AND — working flag cleared by finally block
        assertThat(agent.isWorking()).isFalse();
    }

    /**
     * Rule 4: Abort drains messages still in queue AFTER the failing message was consumed.
     * Uses latch on second doCall to queue additional messages between pollNext and doCall throw.
     */
    @Test
    void testAbortDrainsRemainingQueueOnNextTurn() throws InterruptedException {
        // GIVEN — mock succeeds once, then waits before throwing; lets us queue during 2nd iteration
        var config = LlmConfig.builder().model("mock").build();
        List<ChatMessage> messages = new ArrayList<>();
        var mockModel = streamMock.buildMock(r -> {
            messages.addAll(r.messages());
            return ChatResponse.builder().aiMessage(AiMessage.aiMessage("OK")).build();
        });

        var agent = new AiDevAgent(new ConfiguredChatModel(config, mockModel), new ToolService());
        AtomicReference<String> toolMessage = new AtomicReference<>();
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(org.sterl.llmpeon.tool.model.SimpleMessage m) {}
            @Override public void onTool(String message) { toolMessage.set(message); }
        };

        // AND we have still pending messages Pre-queue msg2
        agent.queueMessage("old message");

        // WHEN
        agent.call("next message", monitor);


        // THEN
        var msg = ChatMessageUtil.toString(messages.get(1));
        assertThat(msg).contains("old message");
        assertThat(msg).contains("next message");

        // AND
        msg = ChatMessageUtil.toString(agent.getMemory().getLastOf(UserMessage.class));
        assertThat(msg).contains("old message");
        assertThat(msg).contains("next message");
        
        // ADN
        assertThat(streamMock.getCallCount()).isOne();

        // AND
        assertThat(toolMessage.get()).isNull();
    }

    @Test
    void clearDeletesOnlyThisAgentsPersistedHistory(@TempDir Path configDir) throws Exception {
        // GIVEN
        var devStore = new FileAgentHistoryStore(configDir.resolve("state/Peon-Dev-history.jsonl"));
        var planStore = new FileAgentHistoryStore(configDir.resolve("state/Peon-Plan-history.jsonl"));
        var config = LlmConfig.builder().model("mock").build();
        var devAgent = new AiDevAgent(new ConfiguredChatModel(config, streamMock.buildMock(r -> ChatResponse.builder().aiMessage(AiMessage.from("ok")).build())), new ToolService(), configDir);
        var planAgent = new AiPlanAgent(new ConfiguredChatModel(config, streamMock.buildMock(r -> ChatResponse.builder().aiMessage(AiMessage.from("ok")).build())), new ToolService(), configDir);
        devAgent.addMessage(UserMessage.from("dev"));
        planAgent.addMessage(UserMessage.from("plan"));
        devAgent.queueMessage("queued");

        // WHEN
        devAgent.clear();

        // THEN
        assertThat(devAgent.getMemory().getCopy()).isEmpty();
        assertThat(devAgent.getQueuedMessageCount()).isZero();
        assertThat(Files.exists(devStore.historyFile())).isFalse();
        assertThat(Files.exists(planStore.historyFile())).isTrue();
    }

    /** By default an agent compacts at the full shared budget (compactFactor 1.0). */
    @Test
    void compactAfterTokens_defaultsToFullBudget() {
        var config = LlmConfig.builder().model("mock").autoCompactAfter(80000).build();
        var agent = new AiDevAgent(new ConfiguredChatModel(config, streamMock.buildMock(r -> null)), new ToolService());
        assertThat(agent.compactAfterTokens()).isEqualTo(80000);
    }

    /** Jon's slaves are constructed with a lower compactFactor so they compact earlier (R10). */
    @Test
    void compactAfterTokens_scaledByCompactFactor() {
        var config = LlmConfig.builder().model("mock").autoCompactAfter(80000).build();
        var model = new ConfiguredChatModel(config, streamMock.buildMock(r -> null));
        assertThat(new AiDevAgent(model, new ToolService(), 0.7).compactAfterTokens()).isEqualTo(56000);
        assertThat(new AiPlanAgent(model, new ToolService(), 0.7).compactAfterTokens()).isEqualTo(56000);
    }

    /** A nonsense factor (<=0 or >1) falls back to the full budget rather than compacting forever. */
    @Test
    void compactAfterTokens_nonsenseFactor_fallsBackToFullBudget() {
        var config = LlmConfig.builder().model("mock").autoCompactAfter(80000).build();
        var model = new ConfiguredChatModel(config, streamMock.buildMock(r -> null));
        assertThat(new AiDevAgent(model, new ToolService(), 0).compactAfterTokens()).isEqualTo(80000);
        assertThat(new AiDevAgent(model, new ToolService(), 1.5).compactAfterTokens()).isEqualTo(80000);
    }

    private List<String> extractUserTexts(List<ChatMessage> messages) {
        return messages.stream()
                .filter(m -> m instanceof UserMessage)
                .map(UserMessage.class::cast)
                .map(u -> ChatMessageUtil.toString(u.contents()).stripTrailing())
                .toList();
    }
}
