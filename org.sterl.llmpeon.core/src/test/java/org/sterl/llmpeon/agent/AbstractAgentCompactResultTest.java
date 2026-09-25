package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.compact.CompactResult;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

class AbstractAgentCompactResultTest {

    // R-CC-3
    @Test
    @Timeout(10)
    void skipsSmallContextHonestly() {
        // GIVEN — only 2 messages: a compact leaves exactly 2 behind, so this is a legitimate no-op
        var agent = devAgent();
        agent.addMessage(UserMessage.from("m1"));
        agent.addMessage(AiMessage.from("m2"));
        var problem = new AtomicReference<String>();
        var monitor = capturingMonitor(problem, new AtomicBoolean());

        // WHEN
        var result = agent.compact(monitor);

        // THEN — honestly reported as a skip, and not as a problem
        assertThat(result.status()).isEqualTo(CompactResult.Status.SKIPPED_SMALL);
        assertThat(problem.get()).isNull();
    }

    // R-CC-3
    @Test
    @Timeout(10)
    void emptyCompressorIsFailedNotEmptyNeeded() {
        // GIVEN — 3 messages (the compact actually runs) and a compressor answering with an empty summary
        var agent = devAgent(r -> ChatResponse.builder().aiMessage(AiMessage.aiMessage("")).build());
        agent.addMessage(UserMessage.from("m1"));
        agent.addMessage(AiMessage.from("m2"));
        agent.addMessage(UserMessage.from("m3"));
        var problem = new AtomicReference<String>();

        // WHEN
        var result = agent.compact(capturingMonitor(problem, new AtomicBoolean()));

        // THEN — an empty compressor is a FAILED_EMPTY, not a "nothing to compact"
        assertThat(result.status()).isEqualTo(CompactResult.Status.FAILED_EMPTY);
        // AND — the failure is reported with the agent's name (SOLL wording)
        assertThat(problem.get()).isEqualTo("Compact failed: compressor returned no summary for " + agent.getName());
    }

    // R-CC-3
    @Test
    @Timeout(10)
    void autoCompactRetriesAfterFailure() {
        // GIVEN — the auto-compact gate below the counter the compact request reports (260000),
        // a compressor that always fails with an empty summary, and a small pre-compact memory
        // (the counter crosses the gate via the compact request's own usage — a pre-set addResult
        // would fire the gate before turn 1 and muddy the retry count)
        var config = LlmConfig.builder()
                .model("main-model")
                .autoCompactAfter(200000)
                .modelConfigs(Map.of(AgentModelConfig.COMPACT,
                        new AgentModelConfig(null, null, "compact-model", null, null, null)))
                .build();
        var canceled = new AtomicBoolean(false);
        var compactCalls = new AtomicInteger();
        var mainRounds = new AtomicInteger();
        var cm = new StreamMock().buildMock(r -> {
            if ("compact-model".equals(r.modelName())) {
                compactCalls.incrementAndGet();
                return ChatResponse.builder().aiMessage(AiMessage.aiMessage("")).build();
            }
            if (mainRounds.incrementAndGet() == 1) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.builder()
                                .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                                        .id("1").name("compactSession").arguments("{}").build()))
                                .build())
                        .tokenUsage(new TokenUsage(260000, 0, 260000))
                        .build();
            }
            canceled.set(true); // user cancels -> the loop breaks before any further addResult
            return ChatResponse.builder().aiMessage(AiMessage.from("aborted")).build();
        });
        var agent = new AiDevAgent(new ConfiguredChatModel(config, cm), new ToolService());
        agent.addMessage(UserMessage.from("m1"));
        agent.addMessage(AiMessage.from("m2"));
        agent.addMessage(UserMessage.from("m3"));
        var problems = new ArrayList<String>();
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onProblem(String message) { problems.add(message); }
            @Override public boolean isCanceled() { return canceled.get(); }
        };

        // WHEN — turn 1: the model calls compactSession, the compressor fails (call 1);
        // turn 2: the real counter (260000 > 200000) must re-fire the auto-compact gate
        agent.call("start", monitor);
        agent.call("next", monitor);

        // THEN — the failed compact did not silence the gate: the compressor is tried again
        assertThat(compactCalls.get()).isEqualTo(2);
        // AND — the failure was reported, not swallowed
        assertThat(problems).anyMatch(p -> p.contains("Compact failed: compressor returned no summary for " + AiDevAgent.NAME));
    }

    private AiDevAgent devAgent() {
        return devAgent(r -> ChatResponse.builder().aiMessage(AiMessage.aiMessage("summary")).build());
    }

    private AiDevAgent devAgent(java.util.function.Function<dev.langchain4j.model.chat.request.ChatRequest, ChatResponse> fn) {
        var config = LlmConfig.builder().model("mock").build();
        return new AiDevAgent(new ConfiguredChatModel(config, new StreamMock().buildMock(fn)), new ToolService());
    }

    private AiMonitor capturingMonitor(AtomicReference<String> problem, AtomicBoolean canceled) {
        return new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onProblem(String message) { problem.set(message); }
            @Override public boolean isCanceled() { return canceled.get(); }
        };
    }
}
