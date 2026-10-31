package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.compact.CompactConstants;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.tools.CompactSessionTool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * The in-loop compact <b>through the tool</b>: {@code compactSession} clears and re-seeds the memory
 * while the round is still running, and only afterwards does {@link ToolService#executeLoop} append
 * that round's AI message plus its tool result ({@link ThreadSafeMemory#addResult}). Measured here is
 * what the NEXT request actually carries — the state-based decision without a tool call is
 * {@link ToolServiceDirectCompactTest}.
 */
class ToolServiceToolCompactTest {

    private static final String OLD = "OLD-HISTORY-MARKER";
    private static final String PRESERVED = "KEEP-THIS";
    private static final String SUMMARY = "COMPACT-SUMMARY-MARKER: continue with the open gate";

    @Test
    @Timeout(10)
    void toolCompactReplacesHistoryOfTheNextRequest() {
        // GIVEN — a context over budget (provider value 260000 of 160000) whose history is
        // recognisable, a compressor that answers with a summary, and a round 1 that calls the
        // compact tool itself with preserve = KEEP-THIS
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
                return ChatResponse.builder().aiMessage(AiMessage.from(SUMMARY)).build();
            }
            sent.add(r);
            if (sent.size() == 1) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.builder()
                                .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                                        .id("1").name(CompactSessionTool.NAME)
                                        .arguments("{\"preserve\":\"" + PRESERVED + "\"}")
                                        .build()))
                                .build())
                        .tokenUsage(new TokenUsage(260000, 0, 260000))
                        .build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
        }));
        var toolService = new ToolService();
        var agent = new AiDevAgent(configuredModel, toolService);
        var memory = agent.getMemory();
        memory.add(UserMessage.from(OLD + " one"));
        memory.add(AiMessage.from("answer one"));
        memory.add(UserMessage.from(OLD + " two"));
        memory.add(AiMessage.from("answer two"));

        // WHEN — round 1 asks for compactSession, round 2 answers normally (no cancel: the round
        // must really be added to the memory and sent)
        toolService.executeLoop(ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(configuredModel)
                .owner(LoopOwner.of(agent, LlmConfig::devAgentConfig))
                .build());

        // THEN — two main-model requests, and exactly one compressor call: the tool compact. The
        // round's provider value describes the PRE-compact context (R-CC-18 D3), so the state-based
        // decision after the round must not compact a second time
        assertThat(sent).hasSize(2);
        assertThat(compactCalls.get()).isEqualTo(1);
        // AND — the seed really reached the first request, otherwise the "history is gone" check
        // below would be green without the feature
        assertThat(rendered(sent.get(0).messages())).contains(OLD);

        var second = sent.get(1).messages();
        var aiTexts = second.stream().filter(AiMessage.class::isInstance).map(AiMessage.class::cast)
                .map(AiMessage::text).toList();
        var userTexts = second.stream().filter(UserMessage.class::isInstance)
                .map(ChatMessageUtil::toString).toList();
        var toolResults = second.stream().filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast).toList();

        // AND — the summary reaches the next request inside the ONE re-seed UserMessage, behind its
        // marker (R-CC-9), and no AiMessage carries it any more
        assertThat(userTexts).anyMatch(t -> t.contains(CompactConstants.REINSERT_MARKER) && t.contains(SUMMARY));
        assertThat(aiTexts).noneMatch(t -> t != null && t.contains(SUMMARY));
        // AND — the round's tool result survived the cleared memory and carries the preserved
        // instructions; it names the compact by its numbers, never by the summary text (R-CT-8)
        assertThat(toolResults).hasSize(1);
        assertThat(toolResults.getFirst().text())
                .startsWith("🗜 compressed ")
                .contains("Preserved:")
                .contains(PRESERVED)
                .doesNotContain(SUMMARY);
        // AND — the cleared history is gone: no seeded message reaches the second request
        assertThat(rendered(second)).doesNotContain(OLD);
    }

    /** The whole request as one string, rendered by the same renderer the memory and stager use. */
    private static String rendered(List<ChatMessage> messages) {
        return messages.stream().map(ChatMessageUtil::toString).collect(Collectors.joining());
    }
}
