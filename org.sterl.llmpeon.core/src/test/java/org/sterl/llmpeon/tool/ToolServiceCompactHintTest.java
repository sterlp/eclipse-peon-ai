package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.tools.CompactSessionTool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

class ToolServiceCompactHintTest {

    // R-CC-4
    @Test
    @Timeout(10)
    void hintIsAddedOnce() {
        // GIVEN — 10 memory messages and two consecutive tool rounds, each reporting real usage
        // (153000) above the 0.95 hint threshold of autoCompactAfter (160000 * 0.95 = 152000)
        var memory = seedMemory(new ThreadSafeMemory(), 10);
        var rounds = new AtomicInteger();
        var cm = new StreamMock().buildMock(r -> rounds.incrementAndGet() <= 2
                ? toolResponse("probe")
                : ChatResponse.builder().aiMessage(AiMessage.from("done")).build());
        var hints = new ArrayList<String>();
        var monitor = hintCapturingMonitor(hints);
        var req = ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(new ConfiguredChatModel(hintConfig(), cm))
                .monitor(monitor)
                .build();

        // WHEN — two tool rounds each crossing the hint threshold
        new ToolService().executeLoop(req);

        // THEN — the hint is added exactly once (memory and monitor), not once per round
        assertThat(hintMessages(memory)).hasSize(1);
        assertThat(hints).hasSize(1);
    }

    // R-CC-4
    @Test
    @Timeout(10)
    void hintReappearsAfterSuccessfulCompact() {
        // GIVEN — a compact-capable agent; after a successful compact (memory reseeded with the
        // "Session compacted" marker) the context grows back over the hint threshold
        var config = LlmConfig.builder()
                .model("main-model")
                .autoCompactAfter(160000)
                .modelConfigs(Map.of(AgentModelConfig.COMPACT,
                        new AgentModelConfig(null, null, "compact-model", null, null, null)))
                .build();
        // The round-2 growth targets the agent's memory — resolved after the agent exists (set below).
        var memoryRef = new AtomicReference<ThreadSafeMemory>();
        var mainRounds = new AtomicInteger();
        var cm = new StreamMock().buildMock(r -> {
            if ("compact-model".equals(r.modelName())) {
                return ChatResponse.builder().aiMessage(AiMessage.aiMessage("summary")).build();
            }
            return switch (mainRounds.incrementAndGet()) {
                case 1 -> toolResponse(CompactSessionTool.NAME);
                case 2 -> {
                    growMemory(memoryRef.get()); // context fills up again after the compact
                    yield toolResponse("probe");
                }
                default -> ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
            };
        });
        var toolService = new ToolService();
        var agent = new AiDevAgent(new ConfiguredChatModel(config, cm), toolService);
        // The request must share the agent's memory — compact() clears the agent's own memory.
        var memory = seedMemory(agent.getMemory(), 10);
        memoryRef.set(memory);
        var hints = new ArrayList<String>();
        var monitor = hintCapturingMonitor(hints);
        var req = ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(new ConfiguredChatModel(config, cm))
                .monitor(monitor)
                .agent(agent)
                .build();

        // WHEN — round 1 compacts successfully, round 2 crosses the threshold in the NEW memory
        toolService.executeLoop(req);

        // THEN — the compact happened (marker in the new memory) and the hint re-armed exactly once
        assertThat(memory.containsUserMessage("Session compacted")).isTrue();
        assertThat(hintMessages(memory)).hasSize(1);
        assertThat(hints).hasSize(1);
    }

    // R-CC-8
    @Test
    @Timeout(10)
    void hintNotBelowMinCompactMessages() {
        // GIVEN — 1 seeded message: after the turn's AiMessage + tool result the history is
        // exactly MIN_COMPACT_MESSAGES (3) at the hint check — too short for a meaningful compact
        var memory = seedMemory(new ThreadSafeMemory(), 1);
        var rounds = new AtomicInteger();
        var cm = new StreamMock().buildMock(r -> rounds.incrementAndGet() == 1
                ? toolResponse("probe")
                : ChatResponse.builder().aiMessage(AiMessage.from("done")).build());
        var hints = new ArrayList<String>();
        var monitor = hintCapturingMonitor(hints);
        var req = ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(new ConfiguredChatModel(hintConfig(), cm))
                .monitor(monitor)
                .build();

        // WHEN
        new ToolService().executeLoop(req);

        // THEN — no hint: the history is too short (≤ MIN_COMPACT_MESSAGES)
        assertThat(hintMessages(memory)).isEmpty();
        assertThat(hints).isEmpty();
    }

    // R-CC-8
    @Test
    @Timeout(10)
    void hintSharpAboveMinCompactMessages() {
        // GIVEN — 2 seeded messages: after the turn's AiMessage + tool result the history is
        // MIN_COMPACT_MESSAGES + 1 (4) at the hint check — just above the boundary
        var memory = seedMemory(new ThreadSafeMemory(), 2);
        var rounds = new AtomicInteger();
        var cm = new StreamMock().buildMock(r -> rounds.incrementAndGet() == 1
                ? toolResponse("probe")
                : ChatResponse.builder().aiMessage(AiMessage.from("done")).build());
        var hints = new ArrayList<String>();
        var monitor = hintCapturingMonitor(hints);
        var req = ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(new ConfiguredChatModel(hintConfig(), cm))
                .monitor(monitor)
                .build();

        // WHEN
        new ToolService().executeLoop(req);

        // THEN — the hint is sharp above the boundary (exactly one, R-CC-4 dedup)
        assertThat(hintMessages(memory)).hasSize(1);
        assertThat(hints).hasSize(1);
    }

    private static LlmConfig hintConfig() {
        return LlmConfig.builder().model("mock").autoCompactAfter(160000).build();
    }

    private static ThreadSafeMemory seedMemory(ThreadSafeMemory memory, int messages) {
        for (int i = 0; i < messages; i++) {
            memory.add(i % 2 == 0 ? UserMessage.from("seed " + i) : AiMessage.from("seed " + i));
        }
        return memory;
    }

    /** Adds 10 small messages and a real usage above the hint threshold (153000 > 152000). */
    private static void growMemory(ThreadSafeMemory memory) {
        for (int i = 0; i < 10; i++) {
            memory.add(i % 2 == 0 ? UserMessage.from("grow " + i) : AiMessage.from("grow " + i));
        }
        memory.addResult(ChatResponse.builder()
                .aiMessage(AiMessage.from("context refilled"))
                .tokenUsage(new TokenUsage(153000, 0, 153000))
                .build(), List.of());
    }

    private static ChatResponse toolResponse(String toolName) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.builder()
                        .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                                .id("1").name(toolName).arguments("{}").build()))
                        .build())
                .tokenUsage(new TokenUsage(153000, 0, 153000))
                .build();
    }

    private static AiMonitor hintCapturingMonitor(List<String> hints) {
        return new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onTool(String message) {
                if (message.contains("🗜 Compact hint")) hints.add(message);
            }
        };
    }

    private static List<UserMessage> hintMessages(ThreadSafeMemory memory) {
        return memory.getCopy().stream()
                .filter(m -> m instanceof UserMessage um && ChatMessageUtil.toString(um).contains("CONTEXT LIMIT WARNING"))
                .map(m -> (UserMessage) m)
                .toList();
    }
}
