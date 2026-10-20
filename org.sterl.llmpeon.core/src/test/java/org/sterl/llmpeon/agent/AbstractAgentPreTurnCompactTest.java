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
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
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

    @BeforeEach
    void beforeEach() {
        streamMock = new StreamMock();
        compactCalls = new AtomicInteger();
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

        // THEN — compacted BEFORE the provider call (the compressor ran once) …
        assertThat(compactCalls.get()).isEqualTo(1);
        // AND — the event line names the agent, the reason and the numbers (R-CC-10)
        assertThat(toolLines).anyMatch(l -> l.startsWith("🗜 Pre-turn compact for Peon-Dev (context over budget):"));
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
    void preTurnCompact_atExactBudget_noFire() {
        // GIVEN — the counter sits EXACTLY on the budget (80 000 = 80 000): the rule is a strict >
        var memory = new ThreadSafeMemory() {
            @Override public int getTotalTokenUsed() { return 80_000; }
        };
        memory.add(UserMessage.from("m1"));
        memory.add(AiMessage.from("m2"));
        memory.add(UserMessage.from("m3"));
        memory.add(AiMessage.from("m4"));
        var agent = new AbstractAgent(new ConfiguredChatModel(config(80_000), mockModel()), new ToolService(), memory) {
            @Override public String getName() { return "test"; }
            @Override public String getSystemPrompt() { return "test"; }
        };
        var toolLines = new ArrayList<String>();
        var monitor = monitor(toolLines);

        // WHEN
        agent.call("fresh message", monitor);

        // THEN — nothing fired (strict >), no event line
        assertThat(compactCalls.get()).isZero();
        assertThat(toolLines).noneMatch(l -> l.contains("Pre-turn compact"));
        assertThat(agent.hasUserText("fresh message")).isTrue();
    }

    /** Budget 80 000 and a 4-message history whose counter is over it (90 000 + estimate). */
    private AiDevAgent overBudgetAgent() {
        return overBudgetAgent(80_000);
    }

    private AiDevAgent overBudgetAgent(int budget) {
        var agent = new AiDevAgent(new ConfiguredChatModel(config(budget), mockModel()), new ToolService());
        agent.addMessage(UserMessage.from("m1"));
        agent.addMessage(AiMessage.from("m2"));
        agent.addMessage(UserMessage.from("m3"));
        agent.getMemory().addResult(ChatResponse.builder()
                .aiMessage(AiMessage.from("m4"))
                .tokenUsage(new TokenUsage(90_000, 0, 90_000))
                .build());
        return agent;
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
            if ("compact-model".equals(r.modelName())) {
                compactCalls.incrementAndGet();
                return ChatResponse.builder().aiMessage(AiMessage.from("Summary of the session")).build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("OK")).build();
        });
    }

    private AiMonitor monitor(List<String> toolLines) {
        return new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onTool(String message) { toolLines.add(message); }
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
