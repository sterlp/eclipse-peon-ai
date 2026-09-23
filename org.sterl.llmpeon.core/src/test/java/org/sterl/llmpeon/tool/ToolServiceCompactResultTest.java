package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

class ToolServiceCompactResultTest {

    // R-CC-2
    @Test
    @Timeout(10)
    void keepsRealCounterOnFailedCompact() {
        // GIVEN — a context whose counter holds the real provider value (260000), and a
        // compressor that answers with an empty summary (FAILED_EMPTY, not the small-context guard)
        var config = LlmConfig.builder()
                .model("main-model")
                .modelConfigs(Map.of(AgentModelConfig.COMPACT,
                        new AgentModelConfig(null, null, "compact-model", null, null, null)))
                .build();
        var configuredModel = new ConfiguredChatModel(config, new StreamMock().buildMock(r -> {
            if ("compact-model".equals(r.modelName())) {
                compactCalls.incrementAndGet();
                return ChatResponse.builder().aiMessage(AiMessage.aiMessage("")).build(); // empty summary
            }
            if (mainRounds.incrementAndGet() == 1) {
                // the compact request's usage is MANDATORY: without it addResult itself would fall
                // back to the estimate and this test would pin the wrong path
                return ChatResponse.builder()
                        .aiMessage(AiMessage.builder()
                                .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                                        .id("1").name("compactSession").arguments("{}").build()))
                                .build())
                        .tokenUsage(new TokenUsage(260000, 0, 260000))
                        .build();
            }
            canceled.set(true); // user cancels -> executeLoop breaks before any addResult
            return ChatResponse.builder().aiMessage(AiMessage.from("aborted")).build();
        }));
        var toolService = new ToolService();
        var agent = new AiDevAgent(configuredModel, toolService);
        var memory = agent.getMemory();
        memory.add(UserMessage.from("u1"));
        memory.add(AiMessage.from("a1"));
        memory.add(UserMessage.from("u2"));
        memory.addResult(ChatResponse.builder()
                .aiMessage(AiMessage.from("pre-compact tail"))
                .tokenUsage(new TokenUsage(260000, 0, 260000))
                .build(), List.of());
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public boolean isCanceled() { return canceled.get(); }
        };

        // WHEN — the model calls compactSession in round 1; the compressor fails with an empty summary
        toolService.executeLoop(ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(configuredModel)
                .monitor(monitor)
                .agent(agent)
                .build());

        // THEN — the compressor was actually called (FAILED_EMPTY, not the < 3 small-context guard)
        assertThat(compactCalls.get()).isEqualTo(1);
        // AND — the real counter survives the failed compact (SOLL: "bleibt 260000")
        assertThat(memory.getTotalTokenUsed()).isEqualTo(260000);
    }
    // R-CC-2
    @Test
    @Timeout(10)
    void secondCompactInSameTurnStillDerivesOnce() {
        // GIVEN — one model round issues TWO compactSession calls: the first compacts (COMPACTED,
        // marks the turn), the second sees the small post-compact memory (SKIPPED_SMALL).
        // D3 sticky-OR: the flag must survive the skip, so the re-derive still runs exactly once.
        var config = LlmConfig.builder()
                .model("main-model")
                .modelConfigs(Map.of(AgentModelConfig.COMPACT,
                        new AgentModelConfig(null, null, "compact-model", null, null, null)))
                .build();
        var configuredModel = new ConfiguredChatModel(config, new StreamMock().buildMock(r -> {
            if ("compact-model".equals(r.modelName())) {
                compactCalls.incrementAndGet();
                return ChatResponse.builder().aiMessage(AiMessage.from("Summary of the session")).build();
            }
            if (mainRounds.incrementAndGet() == 1) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.builder()
                                .toolExecutionRequests(List.of(
                                        ToolExecutionRequest.builder().id("1").name("compactSession").arguments("{}").build(),
                                        ToolExecutionRequest.builder().id("2").name("compactSession").arguments("{}").build()))
                                .build())
                        .tokenUsage(new TokenUsage(260000, 0, 260000))
                        .build();
            }
            canceled.set(true); // user cancels -> executeLoop breaks before any addResult
            return ChatResponse.builder().aiMessage(AiMessage.from("aborted")).build();
        }));
        var toolService = new ToolService();
        var agent = new AiDevAgent(configuredModel, toolService) {
            @Override
            public List<ChatMessage> buildStaticMessages(AiMonitor monitor) {
                staticRebuilds.incrementAndGet();
                return super.buildStaticMessages(monitor);
            }
        };
        var memory = agent.getMemory();
        memory.add(UserMessage.from("u1"));
        memory.add(AiMessage.from("a1"));
        memory.add(UserMessage.from("u2"));
        memory.addResult(ChatResponse.builder()
                .aiMessage(AiMessage.from("pre-compact tail"))
                .tokenUsage(new TokenUsage(260000, 0, 260000))
                .build(), List.of());
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public boolean isCanceled() { return canceled.get(); }
        };

        // WHEN — the loop runs the round carrying both compact calls
        toolService.executeLoop(ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(configuredModel)
                .monitor(monitor)
                .agent(agent)
                .build());

        // THEN — only the first call reached the compressor; the second was an honest SKIPPED_SMALL
        assertThat(compactCalls.get()).isEqualTo(1);
        // AND — the re-derive ran: the counter is the small post-compact estimate, not the 260000
        // AddResult value a flag reset on the skip would leave behind
        assertThat(memory.getTotalTokenUsed()).isEqualTo(ChatMessageUtil.estimateTokens(memory.getCopy()));
        assertThat(memory.getTotalTokenUsed()).isLessThan(260000);
        // AND — the static messages were rebuilt exactly once (both directions of "genau einmal")
        assertThat(staticRebuilds.get()).isEqualTo(1);
    }

    private final AtomicInteger compactCalls = new AtomicInteger();
    private final AtomicInteger mainRounds = new AtomicInteger();
    private final AtomicBoolean canceled = new AtomicBoolean(false);
    private final AtomicInteger staticRebuilds = new AtomicInteger();
}
