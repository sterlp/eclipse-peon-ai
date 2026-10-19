package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

class ToolServiceDirectCompactTest {

    // R-CC-18
    @Test
    @Timeout(10)
    void directCompactBeforeNextCall() {
        // GIVEN — the counter holds the real provider value (260000), the budget is 160000, the
        // compressor works, and round 1 is a NON-compact tool call ("probe")
        var sent = new ArrayList<ChatRequest>();
        var compactCalls = new AtomicInteger();
        var config = LlmConfig.builder()
                .model("main-model")
                .autoCompactAfter(160000)
                .modelConfigs(Map.of(AgentModelConfig.COMPACT,
                        new AgentModelConfig(null, null, "compact-model", null, null, null)))
                .build();
        var configuredModel = new ConfiguredChatModel(config, new StreamMock().buildMock(r -> {
            if ("compact-model".equals(r.modelName())) {
                compactCalls.incrementAndGet();
                return ChatResponse.builder().aiMessage(AiMessage.from("Summary of the session")).build();
            }
            sent.add(r);
            if (sent.size() == 1) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.builder()
                                .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                                        .id("1").name("probe").arguments("{}").build()))
                                .build())
                        .tokenUsage(new TokenUsage(260000, 0, 260000))
                        .build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
        }));
        var toolService = new ToolService();
        var agent = new AiDevAgent(configuredModel, toolService);
        var memory = agent.getMemory();
        memory.add(UserMessage.from("seed one"));
        memory.add(AiMessage.from("seed two"));
        memory.add(UserMessage.from("seed three"));
        memory.addResult(ChatResponse.builder()
                .aiMessage(AiMessage.from("pre-compact tail"))
                .tokenUsage(new TokenUsage(260000, 0, 260000))
                .build(), List.of());
        var toolLines = new ArrayList<String>();
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onTool(String message) { toolLines.add(message); }
        };

        // WHEN — the loop runs round 1 and the state-based compact decision before the next call
        toolService.executeLoop(ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(configuredModel)
                .monitor(monitor)
                .agent(agent)
                .build());

        // THEN — exactly one compressor call: the direct compact fired, no hint was asked
        assertThat(compactCalls.get()).isEqualTo(1);
        // AND — the SECOND request (the sent payload) carries the new small history: 1 system +
        // the 2 re-seeded messages, NO seed messages (without the feature this request would
        // carry the full 7-message history and this test is red)
        assertThat(sent).hasSize(2);
        assertThat(sent.get(1).messages()).hasSize(3);
        sent.get(1).messages().forEach(m ->
                assertThat(ChatMessageUtil.toString(m)).doesNotContain("seed"));
        // AND — no COMPACT_HINT landed in the memory (the direct path replaces the ask)
        assertThat(memory.getCopy()).noneMatch(m ->
                ChatMessageUtil.toString(m).contains("CONTEXT LIMIT WARNING"));
        // AND — the onTool line announces the direct compact with the numbers
        assertThat(toolLines).anyMatch(l -> l.contains("Direct compact for Peon-Dev"));
    }
}
