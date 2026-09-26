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
import org.sterl.llmpeon.compact.CompactConstants;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
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

    // R-CC-9 / agent-level compact+reseed integration (migrated from the legacy compressor test)
    @Test
    @Timeout(10)
    void compact_delegatesToEngineAndReseeds() {
        // GIVEN — a real history and a compressor that answers with a summary
        var agent = devAgent(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: Build a Java Hello world application")).build());
        agent.addMessage(UserMessage.from("Build a Hello world"));
        agent.addMessage(AiMessage.from("In which language?"));
        agent.addMessage(UserMessage.from("In java"));
        agent.addMessage(AiMessage.from("What should it do?"));
        agent.addMessage(UserMessage.from("It should show a Hello world"));

        // WHEN
        var result = agent.compact(AiMonitor.NULL_MONITOR);

        // THEN — the compact succeeded and the memory is re-seeded to exactly two messages
        assertThat(result.status()).isEqualTo(CompactResult.Status.COMPACTED);
        var copy = agent.getMemory().getCopy();
        assertThat(copy).hasSize(2);
        // AND — the first is the resume UserMessage carrying the re-insert marker (R-CC-9)
        assertThat(copy.get(0)).isInstanceOf(UserMessage.class);
        assertThat(ChatMessageUtil.toString(copy.get(0))).contains(CompactConstants.REINSERT_MARKER);
        // AND — the second is the summary as an AI message
        assertThat(copy.get(1)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) copy.get(1)).text()).contains("WHAT: Build a Java Hello world application");
    }

    // R-CC-12
    @Test
    @Timeout(10)
    void compactCapturesRequestTokensBeforeMemoryClear() {
        // GIVEN — a real history whose last model call reported 80211 input tokens
        var agent = devAgent(r -> ChatResponse.builder().aiMessage(AiMessage.aiMessage("summary")).build());
        agent.addMessage(UserMessage.from("m1"));
        agent.addMessage(AiMessage.from("m2"));
        agent.addMessage(UserMessage.from("m3"));
        agent.getMemory().addResult(ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("m4"))
                .tokenUsage(new TokenUsage(80_211, 0, 80_211))
                .build());

        // WHEN
        var result = agent.compact(AiMonitor.NULL_MONITOR);

        // THEN — the result carries the provider value captured BEFORE the clear
        assertThat(result.status()).isEqualTo(CompactResult.Status.COMPACTED);
        assertThat(result.stats().requestTokens()).isEqualTo(80_211);
        assertThat(result.stats().requestIsEstimate()).isFalse();
        // AND — the clear wiped the provider value: a capture after the clear would see null
        assertThat(agent.getMemory().getLastProviderInputTokens()).isNull();
    }

    // R-CC-14
    @Test
    @Timeout(10)
    void compressorChatEventsDoNotReachAgentMonitor() {
        // GIVEN — a real history and a compressor answering with a summary; the monitor captures
        // every chat event the agent's monitor would see
        var agent = devAgent(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: compressor summary")).build());
        agent.addMessage(UserMessage.from("m1"));
        agent.addMessage(AiMessage.from("m2"));
        agent.addMessage(UserMessage.from("m3"));
        var responses = new ArrayList<SimpleMessage>();
        var chatRequests = new ArrayList<Integer>();
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) { responses.add(m); }
            @Override public void onChatMessage(int iteration, ChatRequest.Builder request) { chatRequests.add(iteration); }
        };

        // WHEN
        var result = agent.compact(monitor);

        // THEN — no compressor chat event reaches the agent's monitor (the internal call goes to the log only)
        assertThat(result.status()).isEqualTo(CompactResult.Status.COMPACTED);
        assertThat(chatRequests).isEmpty();
        assertThat(responses).noneMatch(m -> m.message() != null && m.message().contains("WHAT: compressor summary"));
    }

    // R-CC-14
    @Test
    @Timeout(10)
    void startLineEmittedByAgent() {
        // GIVEN — a real history (the guard passes) and a monitor capturing the tool lines
        var agent = devAgent();
        agent.addMessage(UserMessage.from("m1"));
        agent.addMessage(AiMessage.from("m2"));
        agent.addMessage(UserMessage.from("m3"));
        var toolLines = new ArrayList<String>();
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onTool(String message) { toolLines.add(message); }
        };

        // WHEN
        var result = agent.compact(monitor);

        // THEN — exactly ONE start line, emitted by the agent (after the guard, before the clear)
        assertThat(result.status()).isEqualTo(CompactResult.Status.COMPACTED);
        assertThat(toolLines).hasSize(1);
        assertThat(toolLines.getFirst()).startsWith("Compressing conversation 3 messages");
    }

    // R-CC-14
    @Test
    @Timeout(10)
    void summaryIsCarriedInResult() {
        // GIVEN — three agents: COMPACTED (summary), FAILED_EMPTY (empty compressor), SKIPPED_SMALL (2 messages)
        var compactedAgent = devAgent(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: the summary")).build());
        compactedAgent.addMessage(UserMessage.from("m1"));
        compactedAgent.addMessage(AiMessage.from("m2"));
        compactedAgent.addMessage(UserMessage.from("m3"));
        var failedAgent = devAgent(r -> ChatResponse.builder().aiMessage(AiMessage.aiMessage("")).build());
        failedAgent.addMessage(UserMessage.from("m1"));
        failedAgent.addMessage(AiMessage.from("m2"));
        failedAgent.addMessage(UserMessage.from("m3"));
        var smallAgent = devAgent();
        smallAgent.addMessage(UserMessage.from("m1"));
        smallAgent.addMessage(AiMessage.from("m2"));

        // WHEN
        var compacted = compactedAgent.compact(AiMonitor.NULL_MONITOR);
        var failed = failedAgent.compact(AiMonitor.NULL_MONITOR);
        var small = smallAgent.compact(AiMonitor.NULL_MONITOR);

        // THEN — only COMPACTED carries the compressor's summary; the other statuses stay null
        assertThat(compacted.status()).isEqualTo(CompactResult.Status.COMPACTED);
        assertThat(compacted.summary()).isEqualTo("WHAT: the summary");
        assertThat(failed.status()).isEqualTo(CompactResult.Status.FAILED_EMPTY);
        assertThat(failed.summary()).isNull();
        assertThat(small.status()).isEqualTo(CompactResult.Status.SKIPPED_SMALL);
        assertThat(small.summary()).isNull();
    }

    // R-CC-3
    @Test
    @Timeout(10)
    void compact_failedEmpty_onProblemKept() {
        // GIVEN — 3 messages and a compressor answering empty (FAILED_EMPTY)
        var agent = devAgent(r -> ChatResponse.builder().aiMessage(AiMessage.aiMessage("")).build());
        agent.addMessage(UserMessage.from("m1"));
        agent.addMessage(AiMessage.from("m2"));
        agent.addMessage(UserMessage.from("m3"));
        var problem = new AtomicReference<String>();
        var monitor = capturingMonitor(problem, new AtomicBoolean());
        var sizeBefore = agent.getMemory().size();

        // WHEN
        var result = agent.compact(monitor);

        // THEN — FAILED_EMPTY is reported and the history is preserved (not cleared on failure)
        assertThat(result.status()).isEqualTo(CompactResult.Status.FAILED_EMPTY);
        assertThat(problem.get()).isNotNull();
        assertThat(agent.getMemory().size()).isEqualTo(sizeBefore);
    }

    // R-CC-9b
    @Test
    @Timeout(10)
    void reinsertedMessagesDoNotTriggerImmediateReCompact() {
        // GIVEN — a successful compact re-seeds the memory to exactly two messages
        var agent = devAgent(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: summary")).build());
        agent.addMessage(UserMessage.from("m1"));
        agent.addMessage(AiMessage.from("m2"));
        agent.addMessage(UserMessage.from("m3"));
        var first = agent.compact(AiMonitor.NULL_MONITOR);
        assertThat(first.status()).isEqualTo(CompactResult.Status.COMPACTED);
        assertThat(agent.getMemory().size()).isEqualTo(2);

        // WHEN — an immediate re-compact: the two reinserted messages are below the minimum
        var second = agent.compact(AiMonitor.NULL_MONITOR);

        // THEN — honestly skipped, not a looping/failed compact
        assertThat(second.status()).isEqualTo(CompactResult.Status.SKIPPED_SMALL);
    }

    // Q2 / R-CIB-1
    @Test
    @Timeout(10)
    void compactWithZeroBudget_neverCaps() {
        // GIVEN — autoCompactAfter = 0 (budget off): the staging must never truncate
        var streamMock = new StreamMock();
        var cm = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: summary")).build());
        var config = LlmConfig.builder().model("mock").autoCompactAfter(0).build();
        var agent = new AiDevAgent(new ConfiguredChatModel(config, cm), new ToolService());
        var big = "X".repeat(5000);
        agent.addMessage(UserMessage.from(big));
        agent.addMessage(AiMessage.from("m2"));
        agent.addMessage(UserMessage.from("m3"));

        // WHEN
        var result = agent.compact(AiMonitor.NULL_MONITOR);

        // THEN — the compact runs and the full (uncapped) message reaches the wire
        assertThat(result.status()).isEqualTo(CompactResult.Status.COMPACTED);
        var compacted = streamMock.getLast(UserMessage.class).orElseThrow();
        assertThat(compacted.singleText()).contains(big);
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
