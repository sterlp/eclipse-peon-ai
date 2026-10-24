package org.sterl.llmpeon.compact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

class HandleToolLoopCompactComponentTest {

    private static final int BUDGET = 160000;

    // R-CC-18
    @Test
    @Timeout(10)
    void directCompactBeforeCall() {
        // GIVEN — the counter (170001) is over the agent's budget (160000), the history is real
        var memory = seed(new ThreadSafeMemory(), 5);
        setCounter(memory, 170001);
        var compactCalls = new AtomicInteger();
        var agent = agent(compactCalls, memory,
                CompactResult.compacted(new CompactResult.Stats(6, 57000, 10, CompactResult.Stage.NONE,
                        0, 30, "compact-model", 5, 170001, false), "summary"));
        var toolLines = new ArrayList<String>();
        var req = request(memory, agent, toolLines);

        // WHEN — the round ends and the next model call is about to go out
        new HandleToolLoopCompactComponent().handle(req, true);

        // THEN — the compact ran exactly once, BEFORE the next call
        assertThat(compactCalls.get()).isEqualTo(1);
        // AND — the counter was re-derived from the small post-compact memory (R-CC-2)
        assertThat(memory.getTotalTokenUsed()).isLessThan(170001);
        // AND — the static messages were rebuilt for the next call
        assertThat(req.staticMessages).hasSize(1);
        assertThat(((SystemMessage) req.staticMessages.get(0)).text()).isEqualTo("static-rebuilt");
        // AND — the component emits no line of its own any more (R-CT-8): the one end line belongs
        // to AbstractAgent.compact, which this stub agent does not run
        assertThat(toolLines).isEmpty();
    }

    // R-CC-18
    @Test
    @Timeout(10)
    void failedDirectCompactIsNotRetriedInTurn() {
        for (var failed : List.of(CompactResult.skippedSmall(),
                CompactResult.failedEmpty(new CompactResult.Stats(6, 57000, 57000, CompactResult.Stage.NONE,
                        0, 0, "compact-model", 5, 170001, false), "no summary"))) {
            // GIVEN — the direct compact fails (SKIPPED_SMALL / FAILED_EMPTY) and the next round
            // of the SAME turn ends still over budget
            var memory = seed(new ThreadSafeMemory(), 5);
            setCounter(memory, 170001);
            var compactCalls = new AtomicInteger();
            var agent = agent(compactCalls, memory, failed);
            var req = request(memory, agent, new ArrayList<>());
            var handler = new HandleToolLoopCompactComponent();

            // WHEN — two consecutive rounds end over budget within one turn
            handler.handle(req, true);
            handler.handle(req, true);

            // THEN — one attempt per turn: the second round does not retry the failed compact
            assertThat(compactCalls.get()).as("status %s", failed.status()).isEqualTo(1);
        }
    }

    // R-CC-18
    @Test
    @Timeout(10)
    void hintOnlyInTheBandBelowBudget() {
        // GIVEN — the counter (155000) sits in the top-5 % band of the budget (160000), not over it
        var memory = seed(new ThreadSafeMemory(), 5);
        setCounter(memory, 155000);
        var compactCalls = new AtomicInteger();
        var agent = agent(compactCalls, memory, CompactResult.skippedSmall());
        var req = request(memory, agent, new ArrayList<>());

        // WHEN — the round ends inside the band
        new HandleToolLoopCompactComponent().handle(req, true);

        // THEN — no direct compact, but the hint fires and shows the GATE budget, not the config value
        verify(agent, never()).compact(any());
        var hints = hintMessages(memory);
        assertThat(hints).hasSize(1);
        assertThat(hints.getFirst().singleText()).contains("155000 tokens of 160000 used");
    }

    // R-CC-18
    @Test
    @Timeout(10)
    void afterSuccessfulToolCompactNoRuleFires() {
        // GIVEN — the post-compact memory: exactly the two re-seeded messages, a small counter
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("Session compacted: summary"));
        memory.add(AiMessage.from("summary"));
        var compactCalls = new AtomicInteger();
        var agent = agent(compactCalls, memory, CompactResult.skippedSmall());
        var sizeBefore = memory.size();
        var req = request(memory, agent, new ArrayList<>());

        // WHEN — the round ends right after a successful compact
        new HandleToolLoopCompactComponent().handle(req, true);

        // THEN — nothing fires: no compact, no hint, no new message
        verify(agent, never()).compact(any());
        assertThat(memory.size()).isEqualTo(sizeBefore);
        assertThat(hintMessages(memory)).isEmpty();
        assertThat(fallbackMessages(memory)).isEmpty();
    }

    // R-CC-18
    @Test
    @Timeout(10)
    void directCompactRespectsOffSwitch() {
        // GIVEN — the budget is off (0, R-CIB-1) and the counter is huge
        var memory = seed(new ThreadSafeMemory(), 5);
        setCounter(memory, 500000);
        var compactCalls = new AtomicInteger();
        var agent = agent(compactCalls, memory, CompactResult.skippedSmall());
        var req = request(memory, agent, new ArrayList<>(), 0);

        // WHEN — the round ends
        new HandleToolLoopCompactComponent().handle(req, true);

        // THEN — nothing: no compact, no message
        verify(agent, never()).compact(any());
        assertThat(memory.size()).isEqualTo(6);
        assertThat(hintMessages(memory)).isEmpty();
        assertThat(fallbackMessages(memory)).isEmpty();
    }

    // R-CC-18
    @Test
    @Timeout(10)
    void directCompactRespectsMinMessages() {
        // GIVEN — the counter (200000) is over budget, but the history has exactly
        // MIN_COMPACT_MESSAGES (3) — a compact would skip anyway (R-CC-8)
        var memory = seed(new ThreadSafeMemory(), 2);
        setCounter(memory, 200000);
        var compactCalls = new AtomicInteger();
        var agent = agent(compactCalls, memory, CompactResult.skippedSmall());
        var req = request(memory, agent, new ArrayList<>());

        // WHEN — the round ends over budget with a minimal history
        new HandleToolLoopCompactComponent().handle(req, true);

        // THEN — no direct compact (R-CC-8), no message added
        verify(agent, never()).compact(any());
        assertThat(memory.size()).isEqualTo(3);
        assertThat(fallbackMessages(memory)).isEmpty();
    }

    // R-CC-18
    @Test
    @Timeout(10)
    void atBudgetExactlyHintNotDirect() {
        // GIVEN — the counter is EXACTLY at the budget (160000) — the direct gate is strictly
        // `used > budget`, the band gate is `used > 95 %`
        var memory = seed(new ThreadSafeMemory(), 5);
        setCounter(memory, BUDGET);
        var compactCalls = new AtomicInteger();
        var agent = agent(compactCalls, memory, CompactResult.skippedSmall());
        var req = request(memory, agent, new ArrayList<>());

        // WHEN — the round ends exactly at the budget
        new HandleToolLoopCompactComponent().handle(req, true);

        // THEN — no direct compact, but the band fires the hint
        verify(agent, never()).compact(any());
        assertThat(hintMessages(memory)).hasSize(1);
    }

    // R-CC-18
    @Test
    @Timeout(10)
    void oneTokenOverBudgetDirectCompacts() {
        // GIVEN — the counter is one token OVER the budget (160001)
        var memory = seed(new ThreadSafeMemory(), 5);
        setCounter(memory, BUDGET + 1);
        var compactCalls = new AtomicInteger();
        var agent = agent(compactCalls, memory,
                CompactResult.compacted(new CompactResult.Stats(6, 53000, 10, CompactResult.Stage.NONE,
                        0, 30, "compact-model", 5, 160001, false), "summary"));
        var req = request(memory, agent, new ArrayList<>());

        // WHEN — the round ends one token over budget
        new HandleToolLoopCompactComponent().handle(req, true);

        // THEN — the direct compact fires
        assertThat(compactCalls.get()).isEqualTo(1);
    }

    // R-CC-19
    @Test
    @Timeout(10)
    void countsAppendedMessagesOfTheFinishedRound() {
        // GIVEN — the provider input (155000) is under budget (160000), but the round appends a
        // large tool result (30k chars ≈ 10k tokens) that the provider value does not include
        var memory = seed(new ThreadSafeMemory(), 5);
        var bigResult = ToolExecutionResultMessage.from("1", "readFile", "X".repeat(30000));
        memory.addResult(ChatResponse.builder()
                .aiMessage(AiMessage.from("calling readFile"))
                .tokenUsage(new TokenUsage(155000, 0, 155000))
                .build(), List.of(bigResult));
        // the forward-looking counter is now 155000 + estimate(appended) — over budget
        assertThat(memory.getTotalTokenUsed()).isGreaterThan(BUDGET);
        var compactCalls = new AtomicInteger();
        var agent = agent(compactCalls, memory,
                CompactResult.compacted(new CompactResult.Stats(7, 57000, 10, CompactResult.Stage.NONE,
                        0, 30, "compact-model", 5, 155000, false), "summary"));
        var req = request(memory, agent, new ArrayList<>());

        // WHEN — the round ends
        new HandleToolLoopCompactComponent().handle(req, true);

        // THEN — the direct compact fires: the appended tool result pushed the counter over budget
        assertThat(compactCalls.get()).isEqualTo(1);
    }

    // R-CC-18
    @Test
    @Timeout(10)
    void fallbackHintWhenNoOwningAgent() {
        // GIVEN — no owning agent (sub-loop), the counter (155000) is in the band of the
        // sub-config budget (160000) — even though the tool set offers compactSession
        var memory = seed(new ThreadSafeMemory(), 5);
        setCounter(memory, 155000);
        var req = request(memory, null, new ArrayList<>());

        // WHEN — the round ends inside the band
        new HandleToolLoopCompactComponent().handle(req, true);

        // THEN — the honest fallback lands exactly once (R-CC-15), no COMPACT_HINT
        assertThat(fallbackMessages(memory)).hasSize(1);
        assertThat(hintMessages(memory)).isEmpty();
    }

    // R-CC-18
    @Test
    @Timeout(10)
    void fallbackHintWhenToolSetLacksCompact() {
        // GIVEN — an owning agent, the counter (155000) in the band, but this loop's tool set
        // does NOT offer compactSession
        var memory = seed(new ThreadSafeMemory(), 5);
        setCounter(memory, 155000);
        var compactCalls = new AtomicInteger();
        var agent = agent(compactCalls, memory, CompactResult.skippedSmall());
        var req = request(memory, agent, new ArrayList<>());

        // WHEN — the round ends inside the band with a tool set lacking compactSession
        new HandleToolLoopCompactComponent().handle(req, false);

        // THEN — the fallback lands exactly once (R-CC-15), NOT the COMPACT_HINT
        assertThat(fallbackMessages(memory)).hasSize(1);
        assertThat(hintMessages(memory)).isEmpty();
        verify(agent, never()).compact(any());
    }

    // R-CC-18.3 — the hint band is 95 % of the RAW config budget (Paul case: 175000 of a 250000
    // window must NOT hint — the old agent factor fired at 70 %)
    @Test
    @Timeout(10)
    void hint_band_is_95_percent_of_raw_config() {
        // GIVEN — budget 250000, the counter sits one token above the 95 % band (237500)
        var memory = seed(new ThreadSafeMemory(), 5);
        setCounter(memory, 237501);
        var compactCalls = new AtomicInteger();
        var agent = agent(compactCalls, memory, CompactResult.skippedSmall());
        var req = request(memory, agent, new ArrayList<>(), 250000);

        // WHEN — the round ends inside the band of the raw config
        new HandleToolLoopCompactComponent().handle(req, true);

        // THEN — the hint fires and shows the raw config budget
        verify(agent, never()).compact(any());
        var hints = hintMessages(memory);
        assertThat(hints).hasSize(1);
        assertThat(hints.getFirst().singleText()).contains("237501 tokens of 250000 used");

        // AND — far below the band (175000 = 70 % of the window): no hint, no compact, no fallback
        var memory2 = seed(new ThreadSafeMemory(), 5);
        setCounter(memory2, 175000);
        var agent2 = agent(compactCalls, memory2, CompactResult.skippedSmall());
        var req2 = request(memory2, agent2, new ArrayList<>(), 250000);
        new HandleToolLoopCompactComponent().handle(req2, true);
        verify(agent2, never()).compact(any());
        assertThat(hintMessages(memory2)).isEmpty();
        assertThat(fallbackMessages(memory2)).isEmpty();
    }

    // R-CC-18.1 — the direct compact fires at the RAW config budget, not at an agent factor:
    // 175001 (70 % + 1 of 250000) is where the old factor would have compacted — nothing fires
    @Test
    @Timeout(10)
    void direct_compact_at_raw_config_budget_not_agent_factor() {
        // GIVEN — budget 250000, the counter at the old factor threshold (175001)
        var memory = seed(new ThreadSafeMemory(), 5);
        setCounter(memory, 175001);
        var compactCalls = new AtomicInteger();
        var agent = agent(compactCalls, memory, CompactResult.skippedSmall());
        var req = request(memory, agent, new ArrayList<>(), 250000);

        // WHEN — the round ends at the old factor threshold
        new HandleToolLoopCompactComponent().handle(req, true);

        // THEN — no compact, no hint
        verify(agent, never()).compact(any());
        assertThat(hintMessages(memory)).isEmpty();

        // AND — one token over the raw budget (250001): the direct compact fires
        var memory2 = seed(new ThreadSafeMemory(), 5);
        setCounter(memory2, 250001);
        var agent2 = agent(compactCalls, memory2,
                CompactResult.compacted(new CompactResult.Stats(6, 57000, 10, CompactResult.Stage.NONE,
                        0, 30, "compact-model", 5, 250001, false), "summary"));
        var req2 = request(memory2, agent2, new ArrayList<>(), 250000);
        new HandleToolLoopCompactComponent().handle(req2, true);
        assertThat(compactCalls.get()).isEqualTo(1);
    }

    private static ToolLoopRequest request(ThreadSafeMemory memory, AiAgent agent, List<String> toolLines) {
        return request(memory, agent, toolLines, BUDGET);
    }

    private static ToolLoopRequest request(ThreadSafeMemory memory, AiAgent agent, List<String> toolLines,
            int budget) {
        return ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(new ConfiguredChatModel(
                        LlmConfig.builder().model("mock").autoCompactAfter(budget).build(),
                        new StreamMock().buildMock(r -> ChatResponse.builder()
                                .aiMessage(AiMessage.from("never sent")).build())))
                .monitor(new AiMonitor() {
                    @Override public void onChatResponse(SimpleMessage m) {}
                    @Override public void onTool(String message) { toolLines.add(message); }
                })
                .agent(agent)
                .build();
    }

    /**
     * A Mockito owning agent. On COMPACTED the answer mirrors {@code AbstractAgent.compact}:
     * the memory is already cleared and re-seeded with two small messages before the result
     * is returned.
     */
    private static AiAgent agent(AtomicInteger compactCalls, ThreadSafeMemory memory,
            CompactResult compactResult) {
        var agent = mock(AiAgent.class);
        when(agent.getName()).thenReturn("Peon-Dev");
        when(agent.buildStaticMessages(any())).thenReturn(List.of(SystemMessage.from("static-rebuilt")));
        doAnswer(inv -> {
            compactCalls.incrementAndGet();
            if (compactResult.status() == CompactResult.Status.COMPACTED) {
                memory.clear();
                memory.add(UserMessage.from("Session compacted: summary"));
                memory.add(AiMessage.from("summary"));
            }
            return compactResult;
        }).when(agent).compact(any());
        return agent;
    }

    private static ThreadSafeMemory seed(ThreadSafeMemory memory, int messages) {
        for (int i = 0; i < messages; i++) {
            memory.add(i % 2 == 0 ? UserMessage.from("seed " + i) : AiMessage.from("seed " + i));
        }
        return memory;
    }

    /**
     * Sets the counter to EXACTLY {@code tokens} — R-CC-19 makes addResult forward-looking
     * (provider input + chars/3 estimate of the appended message), so the appended AI message's
     * estimate is netted out of the provider input. The gate tests assert on the exact value.
     */
    private static void setCounter(ThreadSafeMemory memory, int tokens) {
        var ai = AiMessage.from("counter seed");
        var input = tokens - ChatMessageUtil.estimateTokens(List.of(ai));
        memory.addResult(ChatResponse.builder()
                .aiMessage(ai)
                .tokenUsage(new TokenUsage(input, 0, input))
                .build(), List.of());
    }

    private static List<UserMessage> hintMessages(ThreadSafeMemory memory) {
        return memory.getCopy().stream()
                .filter(m -> m instanceof UserMessage um && um.singleText().contains("CONTEXT LIMIT WARNING"))
                .map(m -> (UserMessage) m)
                .toList();
    }

    private static List<UserMessage> fallbackMessages(ThreadSafeMemory memory) {
        return memory.getCopy().stream()
                .filter(m -> m instanceof UserMessage um && um.singleText().contains("cannot be compacted"))
                .map(m -> (UserMessage) m)
                .toList();
    }
}
