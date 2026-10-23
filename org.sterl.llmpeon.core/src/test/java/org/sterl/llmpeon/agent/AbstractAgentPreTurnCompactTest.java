package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.compact.CompactFeedback;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * R-CC-20: the pre-turn over-budget check — a turn without a tool round (first call, pure chat,
 * queued follow-up) must not sail over the raw config budget into the provider call unchecked.
 */
@Timeout(10)
class AbstractAgentPreTurnCompactTest {

    private StreamMock streamMock;
    private AtomicInteger compactCalls;
    private List<ChatRequest> requests;

    @BeforeEach
    void beforeEach() {
        streamMock = new StreamMock();
        compactCalls = new AtomicInteger();
        requests = new ArrayList<>();
    }

    // R-CC-20
    @Test
    void preTurnCompactKeepsFreshMessage() {
        // GIVEN — the counter is over the raw budget (90 000 > 80 000) with a compactable history
        var agent = overBudgetAgent();
        var toolLines = new ArrayList<String>();
        var monitor = monitor(toolLines);

        // WHEN — a new turn starts with a fresh message
        agent.call("fresh message", monitor);

        // THEN — the compressor ran once …
        assertThat(compactCalls.get()).isEqualTo(1);
        // AND — the event line names the agent, the reason and the numbers (R-CC-10)
        assertThat(toolLines).anyMatch(l -> l.startsWith("🗜 Pre-turn compact for Peon-Dev (context over budget): compressed")
                && l.contains(" | memory="));
        // AND — compacted BEFORE the provider call: the main-model request no longer carries the seed …
        var request = firstMainRequest();
        var userTexts = request.messages().stream()
                .filter(m -> m instanceof UserMessage)
                .map(m -> ChatMessageUtil.toString(((UserMessage) m).contents()).stripTrailing())
                .toList();
        var aiTexts = request.messages().stream()
                .filter(m -> m instanceof AiMessage)
                .map(m -> ((AiMessage) m).text())
                .toList();
        assertThat(userTexts).noneMatch(t -> "m1".equals(t) || "m3".equals(t));
        assertThat(aiTexts).noneMatch(t -> "m2".equals(t) || "m4".equals(t));
        // … carries the summary instead, and the fresh message
        assertThat(aiTexts).anyMatch(t -> t != null && t.contains("Summary of the session"));
        assertThat(userTexts).anyMatch(t -> t.contains("fresh message"));
        // AND — the fresh message survived the compact and is in the memory
        assertThat(agent.hasUserText("fresh message")).isTrue();
        // AND — the summary reseeded the history
        assertThat(agent.getMemory().getCopy()).anyMatch(m ->
                m instanceof AiMessage ai && ai.text() != null && ai.text().contains("Summary of the session"));
    }

    // R-CC-20
    @Test
    void preTurnCompactAppliesToQueuedFollowUp() {
        // GIVEN — the counter is over the raw budget and a follow-up waits in the queue
        var agent = overBudgetAgent();
        var toolLines = new ArrayList<String>();
        var monitor = monitor(toolLines);
        agent.queueMessage("queued follow-up");

        // WHEN — the queued message runs as its own turn (null initial)
        agent.call(null, monitor);

        // THEN — the same pre-turn check fired (the compressor ran once) …
        assertThat(compactCalls.get()).isEqualTo(1);
        // AND — the queued message survived the compact, still marked as a queued message
        assertThat(extractUserTexts(agent.getMemory().getCopy()))
                .anyMatch(t -> t.contains("[Queued Message]") && t.contains("queued follow-up"));
    }

    // R-CC-20
    @Test
    void preTurnCompactRespectsOffSwitch() {
        // GIVEN — autoCompactAfter = 0 (R-CIB-1: off) although the counter is over any threshold
        var agent = overBudgetAgent(0);
        var toolLines = new ArrayList<String>();
        var monitor = monitor(toolLines);

        // WHEN
        agent.call("fresh message", monitor);

        // THEN — no compact, no event line, the history is untouched
        assertThat(compactCalls.get()).isZero();
        assertThat(toolLines).noneMatch(l -> l.contains("Pre-turn compact"));
        assertThat(agent.getMemory().size()).isEqualTo(6); // 4 seeded + the fresh message + the turn's AI response
        assertThat(agent.hasUserText("fresh message")).isTrue();
    }

    // R-CC-20
    @Test
    void preTurnCompactRespectsMinMessages() {
        // GIVEN — over the raw budget but the history is exactly MIN_COMPACT_MESSAGES (3, not more)
        var agent = new AiDevAgent(new ConfiguredChatModel(config(80_000), mockModel()), new ToolService());
        agent.addMessage(UserMessage.from("m1"));
        agent.addMessage(AiMessage.from("m2"));
        agent.getMemory().addResult(ChatResponse.builder()
                .aiMessage(AiMessage.from("m3"))
                .tokenUsage(new TokenUsage(90_000, 0, 90_000))
                .build());
        var toolLines = new ArrayList<String>();
        var monitor = monitor(toolLines);

        // WHEN
        agent.call("fresh message", monitor);

        // THEN — no compact (R-CC-8), no event line
        assertThat(compactCalls.get()).isZero();
        assertThat(toolLines).noneMatch(l -> l.contains("Pre-turn compact"));
        assertThat(agent.hasUserText("fresh message")).isTrue();
    }

    // R-CC-20
    @Test
    void preTurnCompact_atExact95Percent_noFire() {
        // GIVEN — the counter sits EXACTLY on the 95 % band (76 000 = 95 % of 80 000): the rule is a strict >
        var agent = agentWithCounter(80_000, 76_000);
        var toolLines = new ArrayList<String>();
        var monitor = monitor(toolLines);

        // WHEN
        agent.call("fresh message", monitor);

        // THEN — nothing fired (strict >), no event line, the fresh message is in the memory
        assertThat(compactCalls.get()).isZero();
        assertThat(toolLines).noneMatch(l -> l.contains("Pre-turn compact") || l.contains(CompactFeedback.START_PREFIX));
        assertThat(agent.hasUserText("fresh message")).isTrue();
    }

    // R-CC-20
    @Test
    void preTurnCompact_compactsBetween95And100() {
        // GIVEN — the counter is just over the 95 % band (76 001 > 95 % of 80 000), still under 100 %
        var agent = agentWithCounter(80_000, 76_001);
        var toolLines = new ArrayList<String>();
        var monitor = monitor(toolLines);

        // WHEN
        agent.call("fresh message", monitor);

        // THEN — the pre-turn compact fired once …
        assertThat(compactCalls.get()).isEqualTo(1);
        // AND — the event line names the agent and the reason (R-CC-10)
        assertThat(toolLines).anyMatch(l -> l.startsWith("🗜 Pre-turn compact for"));
        // AND — the fresh message survived the compact
        assertThat(agent.hasUserText("fresh message")).isTrue();
        // AND — compacted BEFORE the provider call: the main-model request no longer carries the seed
        //      (RED under the old 100 % threshold — 76 001 < 80 000 would not have fired)
        var userTexts = firstMainRequest().messages().stream()
                .filter(m -> m instanceof UserMessage)
                .map(m -> ChatMessageUtil.toString(((UserMessage) m).contents()).stripTrailing())
                .toList();
        assertThat(userTexts).noneMatch("m1"::equals);
    }

    // R-CC-20
    @Test
    void preTurnCompactFailureDoesNotKillTurn() {
        // GIVEN — over the raw budget and a compressor that fails on its provider call
        var agent = overBudgetAgent(80_000, mockModelFailingCompact());
        var toolLines = new ArrayList<String>();
        var problems = new ArrayList<String>();
        var monitor = monitor(toolLines, problems);

        // WHEN — the turn starts with a fresh message
        var response = agent.call("fresh message", monitor);

        // THEN — the compact was attempted and reported, the turn itself completed
        assertThat(compactCalls.get()).isEqualTo(1);
        assertThat(problems).containsExactly("Pre-turn compact failed: provider unavailable");
        assertThat(response).isNotNull();
        assertThat(response.aiMessage().text()).isEqualTo("OK");
        // AND — no compact happened: no result line, history intact, fresh message in the memory
        assertThat(toolLines).noneMatch(l -> l.startsWith("🗜 Pre-turn compact for"));
        assertThat(agent.hasUserText("m1")).isTrue();
        assertThat(agent.hasUserText("fresh message")).isTrue();
        assertThat(agent.getMemory().size()).isEqualTo(6); // 4 seeded + fresh message + the turn's AI response
    }

    /** Budget 80 000 and a 4-message history whose counter is over it (90 000 + estimate). */
    private AiDevAgent overBudgetAgent() {
        return overBudgetAgent(80_000);
    }

    private AiDevAgent overBudgetAgent(int budget) {
        return overBudgetAgent(budget, mockModel());
    }

    private AiDevAgent overBudgetAgent(int budget, StreamingChatModel model) {
        var agent = new AiDevAgent(new ConfiguredChatModel(config(budget), model), new ToolService());
        agent.addMessage(UserMessage.from("m1"));
        agent.addMessage(AiMessage.from("m2"));
        agent.addMessage(UserMessage.from("m3"));
        agent.getMemory().addResult(ChatResponse.builder()
                .aiMessage(AiMessage.from("m4"))
                .tokenUsage(new TokenUsage(90_000, 0, 90_000))
                .build());
        return agent;
    }

    /** A 4-message history (m1–m4) whose token counter is pinned to {@code used}, budget {@code budget}. */
    private AbstractAgent agentWithCounter(int budget, int used) {
        var memory = new ThreadSafeMemory() {
            @Override public int getTotalTokenUsed() { return used; }
        };
        memory.add(UserMessage.from("m1"));
        memory.add(AiMessage.from("m2"));
        memory.add(UserMessage.from("m3"));
        memory.add(AiMessage.from("m4"));
        return new AbstractAgent(new ConfiguredChatModel(config(budget), mockModel()), new ToolService(), memory) {
            @Override public String getName() { return "test"; }
            @Override public String getSystemPrompt() { return "test"; }
        };
    }

    private LlmConfig config(int budget) {
        return LlmConfig.builder()
                .model("mock")
                .autoCompactAfter(budget)
                .modelConfigs(Map.of(AgentModelConfig.COMPACT,
                        new AgentModelConfig(null, null, "compact-model", null, null, null)))
                .build();
    }

    private StreamingChatModel mockModel() {
        return streamMock.buildMock(r -> {
            requests.add(r);
            if ("compact-model".equals(r.modelName())) {
                compactCalls.incrementAndGet();
                return ChatResponse.builder().aiMessage(AiMessage.from("Summary of the session")).build();
            }
            // the provider reports the (over-budget) input tokens of the request it just saw
            return ChatResponse.builder().aiMessage(AiMessage.from("OK"))
                    .tokenUsage(new TokenUsage(85_000, 100, 85_100)).build();
        });
    }

    private StreamingChatModel mockModelFailingCompact() {
        return streamMock.buildMock(r -> {
            requests.add(r);
            if ("compact-model".equals(r.modelName())) {
                compactCalls.incrementAndGet();
                throw new RuntimeException("provider unavailable"); // the compressor call fails
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("OK"))
                    .tokenUsage(new TokenUsage(85_000, 100, 85_100)).build();
        });
    }

    /** The first main-model request (not the compressor) — the actual provider payload. */
    private ChatRequest firstMainRequest() {
        return requests.stream()
                .filter(r -> !"compact-model".equals(r.modelName()))
                .findFirst()
                .orElseThrow();
    }

    private AiMonitor monitor(List<String> toolLines) {
        return new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onTool(String message) { toolLines.add(message); }
        };
    }

    private AiMonitor monitor(List<String> toolLines, List<String> problems) {
        return new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onTool(String message) { toolLines.add(message); }
            @Override public void onProblem(String message) { problems.add(message); }
        };
    }

    private List<String> extractUserTexts(List<ChatMessage> messages) {
        return messages.stream()
                .filter(m -> m instanceof UserMessage)
                .map(UserMessage.class::cast)
                .map(u -> ChatMessageUtil.toString(u.contents()).stripTrailing())
                .toList();
    }
}
