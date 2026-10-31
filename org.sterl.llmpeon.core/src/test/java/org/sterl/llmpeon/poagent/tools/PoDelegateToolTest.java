package org.sterl.llmpeon.poagent.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.IntSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.AiReviewAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.compact.CompactFeedback;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.context.SimpleContextItem;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

class PoDelegateToolTest {

    private StreamMock streamMock = new StreamMock();
    private ConfiguredChatModel model;

    @BeforeEach
    void beforeEach() {
        streamMock.reset();
        var cm = streamMock.buildMock(req -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("SLAVE REPLY")).build());
        model = new ConfiguredChatModel(LlmConfig.newOllama("foo"), cm);
    }

    private AiAgent planSlave()   { return new AiPlanAgent(model, new ToolService()); }
    private AiAgent reviewSlave() { return new AiReviewAgent(model, new ToolService()); }
    private AiAgent devSlave()    { return new AiDevAgent(model, new ToolService()); }

    private PoDelegateTool newTool() {
        return newTool(t -> List.of());
    }

    private PoDelegateTool newTool(Function<AiAgent, List<ContextItem>> ordersFor) {
        return newTool(ordersFor, () -> 80000);
    }

    private PoDelegateTool newTool(Function<AiAgent, List<ContextItem>> ordersFor,
            IntSupplier autoCompactAfter) {
        return new PoDelegateTool(planSlave(), reviewSlave(), devSlave(), ordersFor, autoCompactAfter);
    }

    private PoDelegateTool newTool(AiAgent plan, AiAgent review, AiAgent dev, IntSupplier autoCompactAfter) {
        return new PoDelegateTool(plan, review, dev,
                t -> List.of(), autoCompactAfter);
    }

    @Test
    void talkPlan_drivesPlanSlave_andReturnsReply() {
        var tool = newTool();

        var reply = tool.talkPlan("make a plan");

        // WHEN
        assertThat(reply).contains("SLAVE REPLY");
        assertThat(tool.getPlanSlave().getMemory().containsUserMessage("make a plan")).isTrue();
        // AND
        // Token-Zahl ist plattformabhängig (lineSeparator im Prompt) — wir prüfen das Format des Kontext-Reports, nicht die Zahl.
        assertThat(reply).containsPattern("Context: ~\\d+ \\(estimate\\) token - \\d+% used\\.");
    }

    @Test
    void askDev_drivesDevSlave_andReturnsReply() {
        // GIVEN
        var tool = newTool();

        // WHEN
        var reply = tool.askDev("what did you build?");
        
        // THEN
        assertThat(reply).contains("SLAVE REPLY");
        assertThat(tool.getDevSlave().getMemory().containsUserMessage("what did you build?")).isTrue();
        
        // AND
        // Token-Zahl ist plattformabhängig (lineSeparator im Prompt) — wir prüfen das Format des Kontext-Reports, nicht die Zahl.
        assertThat(reply).containsPattern("Context: ~\\d+ \\(estimate\\) token - \\d+% used\\.");
    }

    // UC-TD-1
    /** Pins the full stats tail of a dispatch reply — the CallStats migration must stay byte-identical. */
    @Test
    void dispatchReplyPinsStatsSuffixFormat() {
        var reply = newTool().askDev("x");

        assertThat(reply).containsPattern(
                "done\\. Context: ~\\d+ \\(estimate\\) token - \\d+% used\\. \\(\\d+(?:m \\d+)?s, \\d{2}:\\d{2}\\)");
    }


    /** planWithPlanAgent injects the plan-writing discipline as a standing order; talkPlan does not. */
    @Test
    void planWithPlanAgent_injectsPlanWriteLoop_talkPlanDoesNot() {
        var tool = newTool();

        // WHEN
        tool.talkPlan("just a question");
        // THEN
        var data = streamMock.getLastUserMessagesAsString();
        assertThat(data).hasSize(1);
        assertThat(data).contains("just a question");
        assertThat(data).doesNotContain("plan tools (planSave/planUpdate");

        // WHEN
        tool.planWithPlanAgent("write the plan");
        assertThat(streamMock.getLastUserMessagesAsString())
                .doesNotContain("plan tools (planSave/planUpdate");
    }

    /** ADR-0025: the slave is a shared singleton — the same instance across calls, context carries. */
    @Test
    void slaves_arePersistentSingletons() {
        var tool = newTool();

        var slave = tool.getPlanSlave();
        tool.talkPlan("first");
        tool.talkPlan("second");

        assertThat(tool.getPlanSlave()).isSameAs(slave); // same instance, reused
        assertThat(slave.getMemory().containsUserMessage("first")).isTrue();
        assertThat(slave.getMemory().containsUserMessage("second")).isTrue();
    }

    /** ADR-0024: the slaves are RAM-only — no history store, nothing persisted as JSON. */
    @Test
    void slaves_areRamOnly() {
        var tool = newTool();

        assertThat(tool.getPlanSlave().getMemory().isPersistent()).isFalse();
        assertThat(tool.getDevSlave().getMemory().isPersistent()).isFalse();
    }

    /** The shared memory is injected into each slave's standing orders (read-only for slaves). */
    @Test
    void slaves_getSharedMemoryInjected() {
        // GIVEN
        var memory = "MEMORY: always run the tests";
        var tool = newTool(t -> List.of(new SimpleContextItem("MEMORY: always run the tests")));

        // WHEN
        tool.talkPlan("go");
        tool.talkPlan("go");
        // THEN — user messages only: the system prompt also contains "go" ("goal") since the ADR-0030 render fix
        assertThat(streamMock.count(UserMessage.class, "go")).isEqualTo(2);
        assertThat(streamMock.count(memory)).isEqualTo(1);

        // WHEN
        tool.askDev("go");
        tool.askDev("go");

        assertThat(streamMock.count(UserMessage.class, "go")).isEqualTo(2);
        assertThat(streamMock.count(memory)).isEqualTo(1);
    }

    /** buildWithDev planPath goes into the Dev slave's standing orders and stays sticky across calls. */
    @Test
    void buildWithDev_planPath_becomesStickyStandingOrder() {
        // GIVEN
        var plan = "peon-plan/overview.md";
        var tool = newTool();

        tool.buildWithDev("start du ratte!", plan);
        
        assertThat(streamMock.count(plan)).isEqualTo(1);
        assertThat(streamMock.count("start du ratte!")).isEqualTo(1);

        // a later plan-less build call must keep the plan path (survives compaction)
        tool.buildWithDev("continue 2", null);
        tool.buildWithDev("continue 3", plan);
        assertThat(streamMock.count(plan)).isEqualTo(1);
        assertThat(streamMock.count("continue 3")).isEqualTo(1);
    }

    /** reviewPlanAgent drives the Review slave and reuses the sticky plan path set by buildWithDev. */
    @Test
    void reviewPlanAgent_reusesStickyPlanPath() {
        // GIVEN
        var plan = "peon-plan/overview.md";
        var tool = newTool();
        tool.buildWithDev("start", plan);

        // WHEN — no planPath passed: the sticky path must ride in the standing orders
        var reply = tool.reviewPlanAgent("check the build", null);

        // THEN
        assertThat(reply).contains("SLAVE REPLY");
        assertThat(streamMock.count(plan)).isEqualTo(1);
        assertThat(streamMock.count("check the build")).isEqualTo(1);
    }

    /** Without a sticky plan path, reviewPlanAgent injects no path — Da Dok asks instead of guessing. */
    @Test
    void reviewPlanAgent_withoutStickyPath_injectsNothing() {
        var tool = newTool();

        var reply = tool.reviewPlanAgent("check", null);

        assertThat(reply).contains("SLAVE REPLY");
        assertThat(streamMock.count("peon-plan/overview.md")).isEqualTo(0);
    }

    /** clearReview wipes the Review slave's memory — only Da Dok, not the other slaves. */
    @Test
    void clearReview_wipesReviewSlaveMemoryOnly() {
        // GIVEN
        var tool = newTool();
        tool.reviewPlanAgent("first review", null);
        tool.talkPlan("a question");
        assertThat(tool.getReviewSlave().getMemory().containsUserMessage("first review")).isTrue();

        // WHEN
        tool.clearReview();

        // THEN
        assertThat(tool.getReviewSlave().getMemory().containsUserMessage("first review")).isFalse();
        assertThat(tool.getPlanSlave().getMemory().containsUserMessage("a question")).isTrue();
    }

    /** compactReview compacts the Review slave — the old history is replaced by the summary. */
    @Test
    void compactReview_compactsReviewSlaveMemory() {
        // GIVEN
        var tool = newTool();
        tool.reviewPlanAgent("first review", null);
        tool.reviewPlanAgent("second review", null);
        assertThat(tool.getReviewSlave().getMemory().containsUserMessage("second review")).isTrue();

        // WHEN
        var reply = tool.compactReview();

        // THEN
        assertThat(reply).containsPattern("Context: ~\\d+ \\(estimate\\) token - \\d+% used\\.");
        assertThat(reply).contains(AiReviewAgent.NAME + " compacted.");
        assertThat(tool.getReviewSlave().getMemory().containsUserMessage("second review")).isFalse();
    }

    /** R16 (sharpened): below MIN_COMPACT_MESSAGES compactDev is an honest no-op —
     *  "Nothing to compact (2 messages)", no "compacted.", no LLM call. A successful compact leaves
     *  only 1 message (R-CC-9), so the guard also covers the direct re-compact case. */
    @Test
    void compactDev_skipsWithHonestMessage_whenSlaveBelowThreeMessages() {
        // GIVEN — the Dev slave with exactly 2 messages
        var tool = newTool();
        var dev = tool.getDevSlave();
        dev.getMemory().add(UserMessage.from("old"));
        dev.getMemory().add(AiMessage.from("old reply"));
        var before = dev.getMemory().getCopy();

        // WHEN
        var reply = tool.compactDev();

        // THEN — honest skip message verbatim, no LLM call, memory untouched
        assertThat(reply).isEqualTo("Nothing to compact (2 messages)");
        assertThat(streamMock.getCallCount()).isZero();
        assertThat(dev.getMemory().getCopy()).isEqualTo(before);
    }

    // ---------- pre-delegation slave compact: 0.7 × the shared budget ----------

    private static final int BUDGET = 80000;

    /** A Mockito slave around a real memory: compact() is counted and returns the prepared result. */
    private static AiAgent mockSlave(ThreadSafeMemory memory, AtomicInteger compactCalls,
            CompactResult compactResult) {
        var slave = mock(AiAgent.class);
        when(slave.getName()).thenReturn("Peon-Dev");
        when(slave.getMemory()).thenReturn(memory);
        when(slave.tokenContextUsedInPercent()).thenReturn(0);
        when(slave.call(any(), any())).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("SLAVE REPLY")).build());
        doAnswer(inv -> {
            compactCalls.incrementAndGet();
            return compactResult;
        }).when(slave).compact(any());
        return slave;
    }

    /** A real memory with the counter at exactly {@code tokens} and {@code seedMessages} seed pairs. */
    private static ThreadSafeMemory memoryWithCounter(int seedMessages, int tokens) {
        var memory = new ThreadSafeMemory();
        for (int i = 0; i < seedMessages; i++) {
            memory.add(UserMessage.from("seed " + i));
            memory.add(AiMessage.from("seed reply " + i));
        }
        setCounter(memory, tokens);
        return memory;
    }

    /**
     * Sets the counter to EXACTLY {@code tokens} — R-CC-19 makes addResult forward-looking
     * (provider input + chars/3 estimate of the appended message), so the appended AI message's
     * estimate is netted out of the provider input.
     */
    private static void setCounter(ThreadSafeMemory memory, int tokens) {
        var ai = AiMessage.from("counter seed");
        var input = tokens - ChatMessageUtil.estimateTokens(List.of(ai));
        memory.addResult(ChatResponse.builder()
                .aiMessage(ai)
                .tokenUsage(new TokenUsage(input, 0, input))
                .build(), List.of());
    }

    private static CompactResult compacted() {
        return CompactResult.compacted(new CompactResult.Stats(
                3, 60000, 50, CompactResult.Stage.NONE, 0, 100, "mock", 5, null, false), "summary");
    }

    /** GIVEN a slave above 70 % of the shared budget WHEN a delegation THEN it compacts before the request. */
    @Test
    void delegate_compactsSlave_whenUsedAbove70PercentOfBudget() {
        // GIVEN — 60 000 used > 56 000 (0.7 × 80 000)
        var compactCalls = new AtomicInteger();
        var slave = mockSlave(memoryWithCounter(2, 60000), compactCalls, compacted());
        var tool = newTool(slave, slave, slave, () -> BUDGET);

        // WHEN
        var reply = tool.talkPlan("do the plan");

        // THEN — one compact before the request, the delegation itself still runs
        assertThat(compactCalls.get()).isEqualTo(1);
        assertThat(reply).contains("SLAVE REPLY");
    }

    /** GIVEN a slave at exactly 70 % of the budget WHEN a delegation THEN no compact (strict >). */
    @Test
    void delegate_doesNotCompact_whenUsedAtOrBelow70Percent() {
        // GIVEN — exactly 56 000 = 0.7 × 80 000
        var compactCalls = new AtomicInteger();
        var slave = mockSlave(memoryWithCounter(2, 56000), compactCalls, compacted());
        var tool = newTool(slave, slave, slave, () -> BUDGET);

        // WHEN
        tool.talkPlan("do the plan");

        // THEN
        assertThat(compactCalls.get()).isZero();
    }

    /** GIVEN a slave just above 70 % of the shared budget (56 001 = 0.7 × 80 000 + 1) WHEN a delegation
     *  THEN it compacts exactly once — pins the 0.7 boundary (at 0.72 the line moves to 57 600 → red). */
    @Test
    void delegate_compactsSlave_justAbove70Percent() {
        // GIVEN — 56 001 used, the smallest counter above the 0.7 line (56 000)
        var compactCalls = new AtomicInteger();
        var slave = mockSlave(memoryWithCounter(2, 56001), compactCalls, compacted());
        var tool = newTool(slave, slave, slave, () -> BUDGET);

        // WHEN
        var reply = tool.talkPlan("do the plan");

        // THEN — exactly one compact at the boundary, the delegation itself still runs
        assertThat(compactCalls.get()).isEqualTo(1);
        assertThat(reply).contains("SLAVE REPLY");
    }

    /** GIVEN autoCompactAfter = 0 (off, R-CIB-1) WHEN a delegation THEN no compact, even above 70 %. */
    @Test
    void delegate_doesNotCompact_whenAutoCompactAfterIsZero() {
        // GIVEN — 60 000 used, but the budget is off
        var compactCalls = new AtomicInteger();
        var slave = mockSlave(memoryWithCounter(2, 60000), compactCalls, compacted());
        var tool = newTool(slave, slave, slave, () -> 0);

        // WHEN
        tool.talkPlan("do the plan");

        // THEN
        assertThat(compactCalls.get()).isZero();
    }

    /**
     * GIVEN a slave below MIN_COMPACT_MESSAGES WHEN a delegation THEN compact() IS called — no second
     * guard in PoDelegateTool, R-CC-8 stays inside compact() — and a SKIPPED_SMALL is not disclosed.
     */
    @Test
    void delegate_noCompactEffect_whenSlaveBelowMinCompactMessages() {
        // GIVEN — 1 message < MIN_COMPACT_MESSAGES (3), counter above the 70 % line
        var compactCalls = new AtomicInteger();
        var memory = memoryWithCounter(0, 60000);
        var slave = mockSlave(memory, compactCalls, CompactResult.skippedSmall());
        var tool = newTool(slave, slave, slave, () -> BUDGET);
        var toolLines = new ArrayList<String>();
        tool.withToolRequest(ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(model)
                .monitor(new AiMonitor() {
                    // the real slave wraps this monitor: its lines arrive as TOOL chat messages, the
                    // stamping owner being the slave (ADR-0078)
                    @Override public void onChatResponse(SimpleMessage m) {
                        if (m.role() == SimpleMessage.Type.TOOL) toolLines.add(m.message());
                    }
                    @Override public void onTool(String message) { toolLines.add(message); }
                })
                .build());
        var before = memory.getCopy();

        // WHEN
        tool.talkPlan("do the plan");

        // THEN — the gate still decided (compact called), the skip is not disclosed, memory untouched
        assertThat(compactCalls.get()).isEqualTo(1);
        assertThat(toolLines).noneMatch(l -> l.contains("auto-compact"));
        assertThat(memory.getCopy()).isEqualTo(before);
    }

    /** Plan, Review and Dev all get the same pre-delegation compact rule. */
    @Test
    void delegate_compacts_allThreeSlaves() {
        // GIVEN — all three above the 70 % line
        var planCalls = new AtomicInteger();
        var reviewCalls = new AtomicInteger();
        var devCalls = new AtomicInteger();
        var tool = newTool(
                mockSlave(memoryWithCounter(2, 60000), planCalls, compacted()),
                mockSlave(memoryWithCounter(2, 60000), reviewCalls, compacted()),
                mockSlave(memoryWithCounter(2, 60000), devCalls, compacted()),
                () -> BUDGET);

        // WHEN
        tool.talkPlan("plan it");
        tool.reviewPlanAgent("review it", null);
        tool.askDev("build it");

        // THEN — each slave compacted exactly once
        assertThat(planCalls.get()).isEqualTo(1);
        assertThat(reviewCalls.get()).isEqualTo(1);
        assertThat(devCalls.get()).isEqualTo(1);
    }

    /**
     * A slave at 0.9 × the shared budget compacts ONLY via the PoDelegateTool 0.7 rule (pre-delegation) —
     * the removed per-agent compactFactor / pre-turn gate no longer fires. Real slave, real compact.
     */
    @Test
    void slave_doesNotCompactViaCompactFactor_anymore() {
        // GIVEN — the Dev slave at 72 000 (0.9 × 80 000), above the 0.7 rule line (56 000)
        var dev = devSlave();
        var memory = dev.getMemory();
        for (int i = 0; i < 2; i++) {
            memory.add(UserMessage.from("seed " + i));
            memory.add(AiMessage.from("seed reply " + i));
        }
        setCounter(memory, 72000);
        var tool = newTool(planSlave(), reviewSlave(), dev, () -> BUDGET);
        var toolLines = new ArrayList<String>();
        tool.withToolRequest(ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(model)
                .monitor(new AiMonitor() {
                    // the real slave wraps this monitor: its lines arrive as TOOL chat messages, the
                    // stamping owner being the slave (ADR-0078)
                    @Override public void onChatResponse(SimpleMessage m) {
                        if (m.role() == SimpleMessage.Type.TOOL) toolLines.add(m.message());
                    }
                    @Override public void onTool(String message) { toolLines.add(message); }
                })
                .build());

        // WHEN
        var reply = tool.askDev("build it");

        // THEN — the 0.7 pre-delegation rule compacted the real slave (old history gone)
        assertThat(reply).contains("SLAVE REPLY");
        // AND — R-CT-8: it shows as the uniform start/end pair (no "auto-compact before delegation" line)
        assertThat(toolLines).anyMatch(l -> l.startsWith(CompactFeedback.START_PREFIX + "Dev"));
        assertThat(toolLines.stream().filter(l -> l.contains(CompactFeedback.END_MARKER)).toList()).hasSize(1);
        assertThat(toolLines).noneMatch(l -> l.contains("auto-compact before delegation"));
        assertThat(memory.containsUserMessage("seed 0")).isFalse();
        // AND — no compact came from the removed per-agent pre-turn gate (compactFactor)
        assertThat(toolLines).noneMatch(l -> l.contains("Auto Compact before execution"));
    }

    // UC-CT-13
    @Test
    void compactDevEmitsExactlyOneEndLine() {
        // GIVEN — a real Dev slave with enough history to compact, and a monitor capturing the tool lines
        var dev = devSlave();
        var memory = dev.getMemory();
        for (int i = 0; i < 2; i++) {
            memory.add(UserMessage.from("seed " + i));
            memory.add(AiMessage.from("seed reply " + i));
        }
        var tool = newTool(planSlave(), reviewSlave(), dev, () -> BUDGET);
        var toolLines = new ArrayList<String>();
        tool.withToolRequest(ToolLoopRequest.builder()
                .memory(new ThreadSafeMemory())
                .chatModel(model)
                .monitor(new AiMonitor() {
                    // the real slave wraps this monitor: its lines arrive as TOOL chat messages, the
                    // stamping owner being the slave (ADR-0078)
                    @Override public void onChatResponse(SimpleMessage m) {
                        if (m.role() == SimpleMessage.Type.TOOL) toolLines.add(m.message());
                    }
                    @Override public void onTool(String message) { toolLines.add(message); }
                })
                .build());

        // WHEN
        var reply = tool.compactDev();

        // THEN — the LLM still gets its tool reply ...
        assertThat(reply).contains(AiDevAgent.NAME + " compacted.");
        // AND — the chat shows exactly one start and one end line: a slave reads like the root (R-CT-8)
        assertThat(toolLines).filteredOn(l -> l.startsWith(CompactFeedback.START_PREFIX)).hasSize(1);
        var endLines = toolLines.stream().filter(l -> l.contains(CompactFeedback.END_MARKER)).toList();
        assertThat(endLines).hasSize(1);
        // AND — the end line names the agent, its before/after numbers, the shared duration+wall-time
        // suffix and the result size (system clock ⇒ pattern, not a literal)
        assertThat(endLines.getFirst()).matches("🗜 " + AiDevAgent.NAME + CompactFeedback.END_MARKER
                + "\\d+ → 1 messages, ~\\d+ → ~\\d+ tokens \\(\\d+(?:m \\d+)?s, \\d{2}:\\d{2}\\) \\(\\d+ chars\\)");
        // AND — the removed "<name> compacted." onTool line never returns as a second success message
        assertThat(toolLines).noneMatch(l -> l.startsWith(AiDevAgent.NAME + " compacted."));
    }

}
