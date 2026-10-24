package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.compact.CompactConstants;
import org.sterl.llmpeon.compact.CompactFeedback;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.shared.OnPartialAiResponse;
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
        var sizeBefore = agent.getMemory().size();

        // WHEN
        var result = agent.compact(capturingMonitor(problem, new AtomicBoolean()));

        // THEN — an empty compressor is a FAILED_EMPTY, not a "nothing to compact"
        assertThat(result.status()).isEqualTo(CompactResult.Status.FAILED_EMPTY);
        // AND — the failure is reported with the agent's name (SOLL wording)
        assertThat(problem.get()).isEqualTo("Compact failed: compressor returned no summary for " + agent.getName());
        // AND — the history is preserved (not cleared on failure)
        assertThat(agent.getMemory().size()).isEqualTo(sizeBefore);
    }

    // R-CC-3
    // R-CC-20
    @Test
    @Timeout(10)
    void autoCompactRetriesAfterFailure() {
        // GIVEN — the auto-compact gate below the counter the compact request reports (260000),
        // a compressor that always fails with an empty summary, and a small pre-compact memory
        // (the counter crosses the gate via the compact request's own usage)
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

        // WHEN — turn 1: the model calls compactSession, the compressor fails (call 1), and the
        // over-budget state (260000 > 200000) fires the direct in-loop attempt (call 2, R-CC-18,
        // FAILED_EMPTY); the over-budget state persists, so turn 2's pre-turn check (R-CC-20 —
        // one attempt per turn) makes the third attempt (call 3, FAILED_EMPTY)
        agent.call("start", monitor);
        agent.call("next", monitor);

        // THEN — the failed compacts did not silence the gates: 3 attempts — turn 1 tool attempt +
        // turn 1 direct attempt (R-CC-18) + turn 2 pre-turn attempt (R-CC-20), all FAILED_EMPTY
        assertThat(compactCalls.get()).isEqualTo(3);
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

    // R-CT-5
    // UC-CT-7
    @Test
    @Timeout(10)
    void compressorRequestStartReachesCallerMonitor() {
        // GIVEN — a real history and a monitor capturing every caller-side chat event
        var agent = devAgent(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: compressor summary")).build());
        agent.addMessage(UserMessage.from("m1"));
        agent.addMessage(AiMessage.from("m2"));
        agent.addMessage(UserMessage.from("m3"));
        var toolLines = new ArrayList<String>();
        var chatRequests = new ArrayList<Integer>();
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onChatMessage(int iteration, ChatRequest.Builder request) { chatRequests.add(iteration); }
            @Override public void onTool(String message) { toolLines.add(message); }
        };

        // WHEN
        var result = agent.compact(monitor);

        // THEN — the compressor's request start reaches the caller: its UI shows the working state
        assertThat(result.status()).isEqualTo(CompactResult.Status.COMPACTED);
        assertThat(chatRequests).containsExactly(1);
        // AND — the service adds no line of its own: the compact is the one start line plus its end line
        assertThat(toolLines).hasSize(2);
        assertThat(toolLines.getFirst()).startsWith(CompactFeedback.START_PREFIX);
        assertThat(toolLines.getLast()).contains(CompactFeedback.END_MARKER);
    }

    // R-CT-5
    // UC-CT-7
    @Test
    @Timeout(10)
    void requestStartSeenBeforeCompressorCall() {
        // GIVEN — a caller monitor that flags the request start, and a compressor mock reading that
        // flag at the moment its own call starts
        var startSeen = new AtomicBoolean();
        var seenAtCallTime = new AtomicReference<Boolean>();
        var cm = new StreamMock().buildMock(r -> {
            seenAtCallTime.set(startSeen.get());
            return ChatResponse.builder().aiMessage(AiMessage.aiMessage("WHAT: summary")).build();
        });
        var agent = new AiDevAgent(
                new ConfiguredChatModel(LlmConfig.builder().model("mock").build(), cm), new ToolService());
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onChatMessage(int iteration, ChatRequest.Builder request) { startSeen.set(true); }
        };
        addThreeMessages(agent);

        // WHEN
        var result = agent.compact(monitor);

        // THEN — the mirror happens BEFORE the blocking call: the caller's working state is up while
        // the (minutes-long) compressor call is still ahead. The count-based test above stays green
        // when the mirror moves behind the call — this one is what pins the order.
        assertThat(result.status()).isEqualTo(CompactResult.Status.COMPACTED);
        assertThat(seenAtCallTime.get()).isTrue();
    }

    // R-CC-14
    // UC-CT-14
    @Test
    @Timeout(10)
    void compressorStreamingReachesCallerMonitor() {
        // GIVEN — a compact whose compressor streams its summary in two chunks; the caller captures
        // streaming chunks and chat responses
        var agent = streamingDevAgent(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: compressor summary")).build(), "WHAT: ", "compressor summary");
        addThreeMessages(agent);
        var chunks = new ArrayList<String>();
        var responses = new ArrayList<SimpleMessage>();
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) { responses.add(m); }
            @Override public void onStreamingChunk(OnPartialAiResponse r) { chunks.add(r.type() + ":" + r.value()); }
        };

        // WHEN
        var result = agent.compact(monitor);

        // THEN — the compressor streams live into the caller's monitor, in order (R-CT-8): without the
        // pass-through only the START of the blocking call arrives
        assertThat(result.status()).isEqualTo(CompactResult.Status.COMPACTED);
        assertThat(chunks).containsExactly(
                "START:null", "ANSWER:WHAT: ", "ANSWER:compressor summary", "END:null");
        // AND — the text never arrives as a chat response, only as those chunks: the only messages
        // here are the two compact lines the default onTool forwards (R-CC-14)
        assertThat(responses).noneMatch(m -> String.valueOf(m.message()).contains("compressor summary"));
        assertThat(responses).extracting(SimpleMessage::message)
                .allSatisfy(m -> assertThat((String) m).startsWith("🗜 "));
    }

    // R-CT-6
    // UC-CT-9
    @Test
    @Timeout(10)
    void compactStartLineNamesAgent() {
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

        // THEN — one start line and one end line, both emitted by the agent (R-CT-8)
        assertThat(result.status()).isEqualTo(CompactResult.Status.COMPACTED);
        assertThat(toolLines).hasSize(2);
        // AND — the start names the agent, so a compact of any trigger is attributable (R-CT-6)
        assertThat(toolLines.getFirst())
                .startsWith(CompactFeedback.START_PREFIX + AiDevAgent.NAME + ": 3 messages ")
                .contains(" tokens (estimate)");
        // AND — the end names it too, and shows the re-seeded memory: 2 messages left
        assertThat(toolLines.getLast())
                .startsWith("🗜 " + AiDevAgent.NAME + CompactFeedback.END_MARKER + "3 → 2 messages, ")
                .endsWith(") " + org.sterl.llmpeon.shared.StringUtil.charsSuffix("summary"));
    }

    // UC-CT-15
    @Test
    @Timeout(10)
    void failedCompactEmitsNoEndLine() {
        // GIVEN — a compact that runs but whose compressor returns no summary
        var agent = devAgent(r -> ChatResponse.builder().aiMessage(AiMessage.aiMessage("")).build());
        addThreeMessages(agent);
        var toolLines = new ArrayList<String>();
        var problems = new ArrayList<String>();
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onTool(String message) { toolLines.add(message); }
            @Override public void onProblem(String message) { problems.add(message); }
        };

        // WHEN
        var result = agent.compact(monitor);

        // THEN — the failure is reported as a problem, and the start line stays the only tool line:
        // nothing was compacted, so there is nothing to close (R-CT-8)
        assertThat(result.status()).isEqualTo(CompactResult.Status.FAILED_EMPTY);
        assertThat(problems).hasSize(1);
        assertThat(toolLines).hasSize(1);
        assertThat(toolLines.getFirst()).startsWith(CompactFeedback.START_PREFIX);
        assertThat(toolLines).noneMatch(l -> l.contains(CompactFeedback.END_MARKER));
    }

    // R-CT-7
    // UC-CT-10
    @Test
    @Timeout(10)
    void compactCompletedEventCarriesAgentAndResult() {
        // GIVEN — a compact that runs and succeeds, its events captured with the working state
        var succeeded = devAgent(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: summary")).build());
        addThreeMessages(succeeded);
        var events = new ArrayList<String>();

        // WHEN
        var result = succeeded.compact(completionMonitor(events, succeeded));

        // THEN — exactly one event naming the agent and its status: the view's reset decision keys on
        // the name (R-CT-7), it fires only after the working flag is released, and it reports no turn
        // — this compact ran standalone, so the view rebuilds immediately
        assertThat(result.status()).isEqualTo(CompactResult.Status.COMPACTED);
        assertThat(events).containsExactly(AiDevAgent.NAME + "=COMPACTED working=false within=false");

        // WHEN — a compact that runs but gets no summary
        var failed = devAgent(r -> ChatResponse.builder().aiMessage(AiMessage.aiMessage("")).build());
        addThreeMessages(failed);
        var failedEvents = new ArrayList<String>();
        failed.compact(completionMonitor(failedEvents, failed));

        // THEN — the failure completes too: its caller has a working state to leave (UC-CT-12 keeps
        // the chat, the reset policy reads this status)
        assertThat(failedEvents).containsExactly(AiDevAgent.NAME + "=FAILED_EMPTY working=false within=false");
    }

    // R-CT-7
    // UC-CT-10
    @Test
    @Timeout(10)
    void inTurnCompactReportsTurnFlag() {
        // GIVEN — a turn whose model call compacts the same agent while the turn holds the working
        // flag: that is exactly where the pre-turn, in-loop and tool triggers run
        var events = new ArrayList<String>();
        var agentRef = new AtomicReference<AiDevAgent>();
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onCompactCompleted(String agentName, CompactResult result, boolean withinTurn) {
                events.add(agentName + "=" + result.status() + " within=" + withinTurn);
            }
        };
        var compactedFromTheCall = new AtomicBoolean();
        var agent = devAgent(r -> {
            if (compactedFromTheCall.compareAndSet(false, true)) {
                agentRef.get().compact(monitor); // nested — the turn already owns the flag (R-CT-2)
            }
            return ChatResponse.builder().aiMessage(AiMessage.aiMessage("summary")).build();
        });
        agentRef.set(agent);
        addThreeMessages(agent);

        // WHEN — one turn
        agent.call("go", AiMonitor.NULL_MONITOR);

        // THEN — the event reports the turn: the compact's CAS failed against the turn's flag, and the
        // view defers its rebuild on this very flag, because until that turn's onCallStart the fresh
        // message is not in the memory a rebuild would read (F1)
        assertThat(events).containsExactly(AiDevAgent.NAME + "=COMPACTED within=true");
    }

    @Test
    @Timeout(10)
    void noCompactCompletedOnSkipOrThrow() {
        // GIVEN — a compact below the minimum: nothing runs
        var small = devAgent();
        small.addMessage(UserMessage.from("m1"));
        small.addMessage(AiMessage.from("m2"));
        var events = new ArrayList<String>();

        // WHEN
        assertThat(small.compact(completionMonitor(events, small)).status())
                .isEqualTo(CompactResult.Status.SKIPPED_SMALL);

        // THEN — no event for a compact that never ran: it would rebuild an untouched chat
        assertThat(events).isEmpty();

        // WHEN — a compressor call that dies without a response (Log OR throw, R-CC-3)
        var dying = dyingAgent();
        assertThatThrownBy(() -> dying.compact(completionMonitor(events, dying)))
                .isInstanceOf(IllegalStateException.class);

        // THEN — the throw propagates past the event, so no half-run compact reports completion
        assertThat(events).isEmpty();
        // AND — the throw still released the working flag: the agent stays usable
        assertThat(dying.isWorking()).isFalse();
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

    /** The same, but the answer arrives as streaming chunks first — what the real model does. */
    private AiDevAgent streamingDevAgent(
            java.util.function.Function<dev.langchain4j.model.chat.request.ChatRequest, ChatResponse> fn,
            String... chunks) {
        var config = LlmConfig.builder().model("mock").build();
        return new AiDevAgent(new ConfiguredChatModel(config, new StreamMock().buildStreamingMock(fn, chunks)),
                new ToolService());
    }

    private AiMonitor capturingMonitor(AtomicReference<String> problem, AtomicBoolean canceled) {
        return new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onProblem(String message) { problem.set(message); }
            @Override public boolean isCanceled() { return canceled.get(); }
        };
    }

    /** Collects every {@code onCompactCompleted} as {@code <agent>=<status> working=<flag at event> within=<turn flag>}. */
    private AiMonitor completionMonitor(ArrayList<String> events, AiAgent agent) {
        return new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onCompactCompleted(String agentName, CompactResult result, boolean withinTurn) {
                events.add(agentName + "=" + result.status() + " working=" + agent.isWorking()
                        + " within=" + withinTurn);
            }
        };
    }

    private void addThreeMessages(AiDevAgent agent) {
        agent.addMessage(UserMessage.from("m1"));
        agent.addMessage(AiMessage.from("m2"));
        agent.addMessage(UserMessage.from("m3"));
    }

    /** An agent over three messages whose model call returns null — the service throws (R-CC-3). */
    private AiDevAgent dyingAgent() {
        var dyingModel = new ConfiguredChatModel(LlmConfig.builder().model("mock").build(),
                new StreamMock().buildMock(r -> null)) {
            @Override
            public ChatResponse callBlocking(ChatRequest req, AgentConfig agent, AiMonitor monitor) {
                return null;
            }
        };
        var agent = new AiDevAgent(dyingModel, new ToolService());
        addThreeMessages(agent);
        return agent;
    }
}
