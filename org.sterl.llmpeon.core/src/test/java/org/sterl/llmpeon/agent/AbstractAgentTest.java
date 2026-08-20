package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.context.SimpleContextItem;
import org.sterl.llmpeon.memory.FileAgentHistoryStore;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

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

        callerThread.join(10_000);

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

    /** tokenContextUsedInPercent() calculates percentage relative to autoCompactAfter budget. */
    @Test
    void tokenContextUsedInPercent_calculatesCorrectly() {
        // GIVEN — 50% of 8000 budget used
        var config = LlmConfig.builder().model("mock").autoCompactAfter(8000).build();
        var model = new ConfiguredChatModel(config, streamMock.buildMock(r -> null));
        ThreadSafeMemory testMemory = new ThreadSafeMemory() {
            @Override public int getTotalTokenUsed() { return 4000; }
        };
        
        var agent = new AbstractAgent(model, new ToolService(), testMemory, 1.0) {
            @Override public String getName() { return "test"; }
            @Override public String getSystemPrompt() { return "test"; }
            @Override public Double getTemperature() { return 0.7; }
        };
        
        // WHEN
        int percent = agent.tokenContextUsedInPercent();
        
        // THEN
        assertThat(percent).isEqualTo(50);
    }

    /** tokenContextUsedInPercent() returns 0 for very small context (<100 tokens). */
    @Test
    void tokenContextUsedInPercent_returnsZeroForSmallContext() {
        // GIVEN
        var config = LlmConfig.builder().model("mock").autoCompactAfter(8000).build();
        var model = new ConfiguredChatModel(config, streamMock.buildMock(r -> null));
        ThreadSafeMemory testMemory = new ThreadSafeMemory() {
            @Override public int getTotalTokenUsed() { return 50; }
        };
        
        var agent = new AbstractAgent(model, new ToolService(), testMemory, 1.0) {
            @Override public String getName() { return "test"; }
            @Override public String getSystemPrompt() { return "test"; }
            @Override public Double getTemperature() { return 0.7; }
        };
        
        // WHEN & THEN
        assertThat(agent.tokenContextUsedInPercent()).isZero();
    }

    private List<String> extractUserTexts(List<ChatMessage> messages) {
        return messages.stream()
                .filter(m -> m instanceof UserMessage)
                .map(UserMessage.class::cast)
                .map(u -> ChatMessageUtil.toString(u.contents()).stripTrailing())
                .toList();
    }

    /** compactContext clears memory, invalidates systemMessage, restores turn context, then adds summary. */
    @Test
    void test_compactContext_clearsMemoryAndRestoresTurnContext() {
        var config = LlmConfig.builder().model("mock").build();
        var mockModel = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("compressed summary")).build());

        var agent = new AiDevAgent(new ConfiguredChatModel(config, mockModel), new ToolService());
        agent.addMessage(UserMessage.from("old message"));

        // Set turn context supplier
        agent.setTurnContextSupplier(() -> List.of(new SimpleContextItem("turn context item")));

        // WHEN
        agent.compressContext(monitor -> {});

        // THEN — memory cleared, turn context restored, summary added
        var memory = agent.getMemory().getCopy();
        assertThat(memory).hasSize(2);
        assertThat(memory.get(0)).isInstanceOf(UserMessage.class);
        assertThat(ChatMessageUtil.toString(memory.get(0))).contains("turn context item");
        assertThat(memory.get(1)).isInstanceOf(AiMessage.class);
        assertThat(ChatMessageUtil.toString(memory.get(1))).contains("compressed summary");
    }

    /** call() rebuilds systemMessage after compact cleared it. */
    @Test
    void test_call_rebuildsSystemMessageAfterClear() {
        var config = LlmConfig.builder().model("mock").build();
        AtomicInteger callCount = new AtomicInteger();
        var mockModel = streamMock.buildMock(r -> {
            callCount.incrementAndGet();
            return ChatResponse.builder().aiMessage(AiMessage.aiMessage("OK")).build();
        });

        var agent = new AiDevAgent(new ConfiguredChatModel(config, mockModel), new ToolService());
        agent.setStaticContext(List.of(new SimpleContextItem("persistent context")));

        // First call — builds systemMessage
        agent.call("first", monitor -> {});

        // Compact — clears systemMessage (compressor also makes a call)
        agent.compressContext(monitor -> {});

        // Second call — rebuilds systemMessage
        agent.call("second", monitor -> {});

        // 3 calls total: first call + compressor + second call
        assertThat(callCount.get()).isEqualTo(3);
    }

    /** restoreTurnContext skips items already in memory (contains-check). */
    @Test
    void test_restoreTurnContext_skipsDuplicates() {
        var config = LlmConfig.builder().model("mock").build();
        var mockModel = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("compressed")).build());

        var agent = new AiDevAgent(new ConfiguredChatModel(config, mockModel), new ToolService());

        // Pre-add turn context item to memory
        String turnContextText = "unique turn context";
        agent.addMessage(UserMessage.from(turnContextText));

        // Set turn context supplier with the same item
        agent.setTurnContextSupplier(() -> List.of(new SimpleContextItem(turnContextText)));

        // WHEN — compress triggers restoreTurnContext
        agent.compressContext(monitor -> {});

        // THEN — turn context appears only once (skipped on restore), plus compressed AI message
        var memory = agent.getMemory().getCopy();
        List<String> userTexts = extractUserTexts(memory);
        assertThat(userTexts).hasSize(1);
        assertThat(userTexts.get(0)).contains("unique turn context");
        // AND the AI summary is present
        assertThat(memory).anyMatch(m -> m instanceof AiMessage ai
                && ChatMessageUtil.toString(ai).contains("compressed"));
    }

    /** compressContext restores only turnContextSupplier (no double-restore). */
    @Test
    void test_compressContext_noUserContextRestore() {
        var config = LlmConfig.builder().model("mock").build();
        var mockModel = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("compressed summary")).build());

        var agent = new AiDevAgent(new ConfiguredChatModel(config, mockModel), new ToolService());
        agent.addMessage(UserMessage.from("old message"));

        // Set via turnContextSupplier
        agent.setTurnContextSupplier(() -> List.of(new SimpleContextItem("AGENTS.md: Rule 1 — be concise")));

        // WHEN
        agent.compressContext(monitor -> {});

        // THEN — memory has the shimmed item (via turnContextSupplier), resume message, AI summary
        var memory = agent.getMemory().getCopy();
        List<String> userTexts = extractUserTexts(memory);
        assertThat(userTexts).hasSize(1);
        var mergedUserText = userTexts.get(0);
        assertThat(mergedUserText).contains("AGENTS.md: Rule 1 — be concise");
        assertThat(mergedUserText).contains("Session compacted");
        // AND the AI summary is present
        assertThat(memory).anyMatch(m -> m instanceof AiMessage ai
                && ChatMessageUtil.toString(ai).contains("compressed summary"));
    }

    /** compressContext skips duplicates in turnContextSupplier (contains-check). */
    @Test
    void test_compressContext_skipsDuplicatesInTurnContext() {
        var config = LlmConfig.builder().model("mock").build();
        var mockModel = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("compressed")).build());

        var agent = new AiDevAgent(new ConfiguredChatModel(config, mockModel), new ToolService());

        // Pre-add context item to memory (simulating it was already injected)
        String turnContextText = "existing turn context";
        agent.addMessage(UserMessage.from(turnContextText));

        // Set via turnContextSupplier with the same item
        agent.setTurnContextSupplier(() -> List.of(new SimpleContextItem(turnContextText)));

        // WHEN
        agent.compressContext(monitor -> {});

        // THEN — context item appears only once (skipped on restore), plus resume message
        var memory = agent.getMemory().getCopy();
        List<String> userTexts = extractUserTexts(memory);
        assertThat(userTexts).hasSize(1);
        var mergedText = userTexts.get(0);
        // Count occurrences — should appear exactly once
        assertThat(countOccurrences(mergedText, "existing turn context")).isOne();
        assertThat(mergedText).contains("Session compacted");
        // AND the AI summary is present
        assertThat(memory).anyMatch(m -> m instanceof AiMessage ai
                && ChatMessageUtil.toString(ai).contains("compressed"));
    }

    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

    /** doCall() injects turn context on Turn 1 (no compact required) and skips duplicates on Turn 2. */
    @Test
    void test_doCall_injectsTurnContextOnFirstTurn_noDuplicatesOnSecond() {
        var config = LlmConfig.builder().model("mock").autoCompactAfter(80000).build();
        var mockModel = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("OK")).build());

        var agent = new AiDevAgent(new ConfiguredChatModel(config, mockModel), new ToolService());
        agent.setTurnContextSupplier(() -> List.of(new SimpleContextItem("turn context item")));

        // WHEN — Turn 1
        agent.call("first", monitor -> {});

        // THEN — Turn 1 injected context into memory
        var memory1 = agent.getMemory().getCopy();
        List<String> userTexts1 = extractUserTexts(memory1);
        assertThat(userTexts1).anyMatch(t -> t.contains("turn context item"));

        // WHEN — Turn 2 (no compact triggered)
        agent.call("second", monitor -> {});

        // THEN — Turn 2 did NOT duplicate the context
        var memory2 = agent.getMemory().getCopy();
        List<String> userTexts2 = extractUserTexts(memory2);
        long contextCount = userTexts2.stream()
                .filter(t -> t.contains("turn context item"))
                .count();
        assertThat(contextCount).isOne();
    }

    /** buildSystemPrompt fires monitor.onTool("Loading 📋 <label>") for labeled persistent context items. */
    @Test
    void test_buildSystemPrompt_reportsLabeledItems() {
        var config = LlmConfig.builder().model("mock").autoCompactAfter(80000).build();
        var mockModel = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("OK")).build());

        var agent = new AiDevAgent(new ConfiguredChatModel(config, mockModel), new ToolService());
        ContextItem labeled = new SimpleContextItem("docs/memory.md", "labeled content");
        ContextItem unlabeled = new SimpleContextItem("unlabeled content");
        agent.setStaticContext(List.of(labeled, unlabeled));

        List<String> toolMessages = new ArrayList<>();
        AiMonitor monitor = new AiMonitor() {
            @Override public void onChatResponse(org.sterl.llmpeon.tool.model.SimpleMessage m) {}
            @Override public void onTool(String message) { toolMessages.add(message); }
        };

        // WHEN
        agent.call("test", monitor);

        // THEN — onTool called for labeled item only
        assertThat(toolMessages).contains("Loading 📋 docs/memory.md");
        assertThat(toolMessages).noneMatch(m -> m.contains("unlabeled"));
    }

    /** restoreTurnContext fires monitor.onTool("Loading 📋 <label>") for labeled turn context items. */
    @Test
    void test_restoreTurnContext_reportsLabeledItems() {
        var config = LlmConfig.builder().model("mock").autoCompactAfter(80000).build();
        var mockModel = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("OK")).build());

        var agent = new AiDevAgent(new ConfiguredChatModel(config, mockModel), new ToolService());
        ContextItem labeled = new SimpleContextItem("peon-plan/overview.md", "turn labeled content");
        agent.setTurnContextSupplier(() -> List.of(labeled));

        List<String> toolMessages = new ArrayList<>();
        AiMonitor monitor = new AiMonitor() {
            @Override public void onChatResponse(org.sterl.llmpeon.tool.model.SimpleMessage m) {}
            @Override public void onTool(String message) { toolMessages.add(message); }
        };

        // WHEN
        agent.call("test", monitor);

        // THEN — onTool called for labeled turn context item
        assertThat(toolMessages).contains("Loading 📋 peon-plan/overview.md");
    }

    /** S1: keyed item — file content change does not re-inject; the key check skips rendering entirely. */
    @Test
    void test_restoreTurnContext_fileContentChange_doesNotReinject() {
        var config = LlmConfig.builder().model("mock").autoCompactAfter(80000).build();
        var mockModel = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("OK")).build());

        var agent = new AiDevAgent(new ConfiguredChatModel(config, mockModel), new ToolService());

        AtomicInteger renderCount = new AtomicInteger();
        AtomicReference<String> content = new AtomicReference<>("memory v1");
        ContextItem keyed = new ContextItem() {
            @Override public String render() {
                renderCount.incrementAndGet();
                return "C:/x/memory.md:\n---\n" + content.get();
            }
            @Override public String dedupKey() { return "C:/x/memory.md"; }
        };
        agent.setTurnContextSupplier(() -> List.of(keyed));

        // WHEN — Turn 1 injects v1
        agent.call("hi", monitor -> {});

        // THEN — rendered once, v1 in memory
        assertThat(renderCount.get()).isOne();
        assertThat(extractUserTexts(agent.getMemory().getCopy())).anyMatch(t -> t.contains("memory v1"));

        // AND — the file changed to v2
        content.set("memory v2");

        // WHEN — Turn 2 (no compact)
        agent.call("again", monitor -> {});

        // THEN — not re-injected; key was in history so render was never called
        assertThat(renderCount.get()).isOne();
        assertThat(extractUserTexts(agent.getMemory().getCopy())).noneMatch(t -> t.contains("memory v2"));
    }

    /** S2: file context is injected before the user message, with a loading status. */
    @Test
    void test_doCall_injectsFileContextBeforeUserMessage_withStatus() {
        var config = LlmConfig.builder().model("mock").autoCompactAfter(80000).build();
        var mockModel = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("OK")).build());

        var agent = new AiDevAgent(new ConfiguredChatModel(config, mockModel), new ToolService());
        ContextItem keyed = new ContextItem() {
            @Override public String render() { return "/proj/docs/memory.md:\n---\nmemory content"; }
            @Override public String label() { return "/proj/docs/memory.md"; }
            @Override public String dedupKey() { return "/proj/docs/memory.md"; }
        };
        agent.setTurnContextSupplier(() -> List.of(keyed));

        List<String> toolMessages = new ArrayList<>();
        AiMonitor monitor = new AiMonitor() {
            @Override public void onChatResponse(org.sterl.llmpeon.tool.model.SimpleMessage m) {}
            @Override public void onTool(String message) { toolMessages.add(message); }
        };

        // WHEN
        agent.call("hi", monitor);

        // THEN — file content merged into the same user message, BEFORE "hi"
        var userTexts = extractUserTexts(agent.getMemory().getCopy());
        assertThat(userTexts).hasSize(1);
        var text = userTexts.get(0);
        assertThat(text.indexOf("memory content")).isNotNegative().isLessThan(text.indexOf("hi"));
        // AND — loading status reported
        assertThat(toolMessages).contains("Loading 📋 /proj/docs/memory.md");
    }

    /** S3: render() = null → nothing injected, no loading status, no exception. */
    @Test
    void test_restoreTurnContext_nullRender_noInjectionNoStatus() {
        var config = LlmConfig.builder().model("mock").autoCompactAfter(80000).build();
        var mockModel = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("OK")).build());

        var agent = new AiDevAgent(new ConfiguredChatModel(config, mockModel), new ToolService());
        ContextItem missing = new ContextItem() {
            @Override public String render() { return null; }
            @Override public String label() { return "/proj/docs/missing.md"; }
            @Override public String dedupKey() { return "/proj/docs/missing.md"; }
        };
        agent.setTurnContextSupplier(() -> List.of(missing));

        List<String> toolMessages = new ArrayList<>();
        AiMonitor monitor = new AiMonitor() {
            @Override public void onChatResponse(org.sterl.llmpeon.tool.model.SimpleMessage m) {}
            @Override public void onTool(String message) { toolMessages.add(message); }
        };

        // WHEN
        agent.call("hi", monitor);

        // THEN — memory holds only the user message, no loading status
        var userTexts = extractUserTexts(agent.getMemory().getCopy());
        assertThat(userTexts).hasSize(1);
        assertThat(userTexts.get(0)).isEqualTo("hi");
        assertThat(toolMessages).noneMatch(m -> m.startsWith("Loading 📋"));
    }

    /** S4: keyed dedup — render is never called when the key is already in history. */
    @Test
    void test_restoreTurnContext_keyedDedup_skipsRenderWhenKeyInHistory() {
        var config = LlmConfig.builder().model("mock").build();
        var mockModel = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("compressed")).build());

        var agent = new AiDevAgent(new ConfiguredChatModel(config, mockModel), new ToolService());

        AtomicInteger renderCount = new AtomicInteger();
        ContextItem keyed = new ContextItem() {
            @Override public String render() {
                renderCount.incrementAndGet();
                return "/proj/docs/memory.md:\n---\nfile content body";
            }
            @Override public String dedupKey() { return "/proj/docs/memory.md"; }
        };

        // Pre-add the keyed content to memory (already injected before)
        agent.addMessage(UserMessage.from("/proj/docs/memory.md:\n---\nfile content body"));
        agent.setTurnContextSupplier(() -> List.of(keyed));

        // WHEN — a turn runs restoreTurnContext without clearing memory
        agent.call("hi", monitor -> {});

        // THEN — render was never called, content appears only once
        assertThat(renderCount.get()).isZero();
        var userTexts = extractUserTexts(agent.getMemory().getCopy());
        assertThat(countOccurrences(userTexts.get(0), "file content body")).isOne();
    }

    /** Header-Dedup (Bugfix 2026-08-16): a Memory message that only mentions the bare path
        (e.g. a Compact message) does NOT dedupe — the exact header is missing →
        the item is still injected. */
    @Test
    void test_restoreTurnContext_barePathInHistory_stillInjects() {
        var config = LlmConfig.builder().model("mock").build();
        var mockModel = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("OK")).build());

        var agent = new AiDevAgent(new ConfiguredChatModel(config, mockModel), new ToolService());

        AtomicInteger renderCount = new AtomicInteger();
        String header = "C:/x/memory.md" + ":" + System.lineSeparator() + "---" + System.lineSeparator();
        ContextItem keyed = new ContextItem() {
            @Override public String render() {
                renderCount.incrementAndGet();
                return header + "file content body";
            }
            @Override public String dedupKey() { return header; }
        };

        // GIVEN — history only mentions the bare path (e.g. a Compact message), no exact header
        agent.addMessage(UserMessage.from("During the previous session we loaded C:/x/memory.md."));
        agent.setTurnContextSupplier(() -> List.of(keyed));

        // WHEN — a turn runs restoreTurnContext
        agent.call("hi", monitor -> {});

        // THEN — the item was injected (render called), content is in memory
        assertThat(renderCount.get()).isOne();
        assertThat(extractUserTexts(agent.getMemory().getCopy()))
                .anyMatch(t -> t.contains("file content body"));

        // AND — second call: the exact header is now in history → no re-injection
        agent.call("again", monitor -> {});
        assertThat(renderCount.get()).isOne();
    }

    /** S4: after compact the key is gone from history → the keyed item is re-injected. */
    @Test
    void test_restoreTurnContext_keyedItem_reinjectedAfterCompact() {
        var config = LlmConfig.builder().model("mock").build();
        var mockModel = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("compressed")).build());

        var agent = new AiDevAgent(new ConfiguredChatModel(config, mockModel), new ToolService());
        ContextItem keyed = new ContextItem() {
            @Override public String render() { return "/proj/docs/memory.md:\n---\nfile content body"; }
            @Override public String dedupKey() { return "/proj/docs/memory.md"; }
        };
        agent.setTurnContextSupplier(() -> List.of(keyed));

        // GIVEN — first call injected the file
        agent.call("hi", monitor -> {});

        // WHEN — compact clears memory, then restores turn context
        agent.compressContext(monitor -> {});

        // THEN — file content is back in memory
        assertThat(extractUserTexts(agent.getMemory().getCopy()))
                .anyMatch(t -> t.contains("file content body"));
    }

    /** S4: project switch — old key in history, item now returns a new key → re-injected. */
    @Test
    void test_restoreTurnContext_keyedItem_reinjectedOnProjectSwitch() {
        var config = LlmConfig.builder().model("mock").autoCompactAfter(80000).build();
        var mockModel = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("OK")).build());

        var agent = new AiDevAgent(new ConfiguredChatModel(config, mockModel), new ToolService());
        AtomicReference<String> key = new AtomicReference<>("/projA/docs/memory.md");
        ContextItem keyed = new ContextItem() {
            @Override public String render() { return key.get() + ":\n---\nfile content body"; }
            @Override public String dedupKey() { return key.get(); }
        };
        agent.setTurnContextSupplier(() -> List.of(keyed));

        // GIVEN — first call injected the file from project A
        agent.call("hi", monitor -> {});

        // AND — the project switched
        key.set("/projB/docs/memory.md");

        // WHEN — next call
        agent.call("again", monitor -> {});

        // THEN — the new project's file was injected
        assertThat(extractUserTexts(agent.getMemory().getCopy()))
                .anyMatch(t -> t.contains("/projB/docs/memory.md"));
    }

    /** restoreTurnContext does NOT fire onTool for unlabeled items (SimpleContextItem). */
    @Test
    void test_restoreTurnContext_silentForUnlabeledItems() {
        var config = LlmConfig.builder().model("mock").autoCompactAfter(80000).build();
        var mockModel = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("OK")).build());

        var agent = new AiDevAgent(new ConfiguredChatModel(config, mockModel), new ToolService());
        agent.setTurnContextSupplier(() -> List.of(new SimpleContextItem("silent content")));

        List<String> toolMessages = new ArrayList<>();
        AiMonitor monitor = new AiMonitor() {
            @Override public void onChatResponse(org.sterl.llmpeon.tool.model.SimpleMessage m) {}
            @Override public void onTool(String message) { toolMessages.add(message); }
        };

        // WHEN
        agent.call("test", monitor);

        // THEN — no onTool calls for unlabeled items
        assertThat(toolMessages).noneMatch(m -> m.startsWith("Loading 📋"));
    }
}