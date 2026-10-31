package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.agent.AgentQueueSeam;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.model.TaggedResult;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.queuedmessages.QueuedUserMessages;
import org.sterl.llmpeon.queuedmessages.UserMessageQueue;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.model.SimpleMessage.Type;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

/**
 * Rule 11: the urgent entries a round picked up ride on the FIRST tool result of that round — the
 * running turn reads them on its next model call without the memory being touched.
 *
 * <p>Every assertion reads the payload the loop actually put into the next {@link ChatRequest} (plus
 * the memory behind it), never just "a round happened".
 */
class ToolServiceQueuedUserMessageTest {

    private static final long AT_1432 = Instant.parse("2026-09-23T14:32:00Z").toEpochMilli();
    private static final String OUTPUT = "tool-output";
    private static final String LN = TaggedResult.LN;

    private final ToolService service = new ToolService(false); // only the fake tool below
    private final List<ChatRequest> sent = new ArrayList<>();
    private final List<String> toolLines = new ArrayList<>();
    private final AiMonitor monitor = new AiMonitor() {
        // the loop stamps its lines with an OwnerMonitor, which hands them on as SimpleMessages and
        // never calls onTool on its delegate (ADR-0078); both channels feed one list
        @Override public void onChatResponse(SimpleMessage m) { if (m.role() == Type.TOOL) toolLines.add(m.message()); }
        @Override public void onTool(String message) { toolLines.add(message); }
    };

    @BeforeEach
    void registerFakeTool() {
        service.addTool(new EchoTool(() -> {}));
    }

    @Test
    void urgentMessagesRideOnTheFirstToolResultOfTheRound() {
        // GIVEN — two urgent entries waiting, one iteration with two tool calls
        var round = new AtomicInteger();
        var model = modelAnswering(req -> round.incrementAndGet() == 1 ? twoToolCalls() : answer("done"));
        var agent = agent(model);
        agent.queueUrgentMessage("u1");
        agent.queueUrgentMessage("u2");

        // WHEN
        runRound(agent, model);

        // THEN — the round's first result carries BOTH messages, one line each with the queue time …
        var results = toolResults(sent.get(1).messages());
        assertThat(results).hasSize(2);
        assertThat(results.getFirst().text()).isEqualTo(OUTPUT + LN + LN
                + TaggedResult.PREFIX + TaggedResult.TAG_QUEUED_USER_MESSAGE + LN
                + "(queued 14:32): u1" + LN
                + "(queued 14:32): u2" + LN
                + TaggedResult.SUFFIX + TaggedResult.TAG_QUEUED_USER_MESSAGE);
        // AND — exactly ONE result of the iteration carries it, not every tool (R11) …
        assertThat(results.get(1).text()).isEqualTo(OUTPUT);
        // AND — the memory keeps the very same payload …
        assertThat(toolResults(agent.getMemory().getCopy()).getFirst().text())
                .isEqualTo(results.getFirst().text());
        // AND — consumption is final: the lane is empty, so nothing can deliver these again …
        assertThat(agent.getQueuedMessageCount()).isZero();
        // AND — and every message was disclosed once, with its queue time (Rule 9).
        assertThat(toolLines).containsExactly(
                "Reading queued User message: u1 (queued 14:32)",
                "Reading queued User message: u2 (queued 14:32)");
    }

    @Test
    void emptyUrgentLaneLeavesTheToolResultUntouched() {
        // GIVEN — a normal round, nothing queued
        var round = new AtomicInteger();
        var model = modelAnswering(req -> round.incrementAndGet() == 1 ? twoToolCalls() : answer("done"));
        var agent = agent(model);

        // WHEN
        runRound(agent, model);

        // THEN — plain tool outputs, no empty block …
        assertThat(toolResults(sent.get(1).messages()))
                .hasSize(2)
                .allSatisfy(result -> assertThat(result.text()).isEqualTo(OUTPUT));
        // AND — and no disclosure line for a delivery that never happened.
        assertThat(sent.get(1).messages())
                .noneMatch(m -> ChatMessageUtil.toString(m).contains(TaggedResult.TAG_QUEUED_USER_MESSAGE));
        assertThat(toolLines).noneMatch(line -> line.startsWith("Reading queued User message:"));
    }

    /**
     * Rule 11 + Rule 8.5 over the whole turn: an entry that arrives while the turn works is delivered
     * by the round — and the turn must not answer it a second time as a follow-up marker.
     */
    @Test
    void consumedUrgentEntryIsNotDeliveredTwice() {
        // GIVEN — a full agent turn; the „!" can only arrive while its tool round runs
        var arriving = new AtomicInteger();
        var agentRef = new AtomicReference<AiDevAgent>();
        service.replaceTool(new EchoTool(() -> {
            if (arriving.compareAndSet(0, 1)) agentRef.get().queueUrgentMessage("bitte abbrechen");
        }));
        var round = new AtomicInteger();
        var model = modelAnswering(req -> round.incrementAndGet() == 1 ? twoToolCalls() : answer("done"));
        var agent = agent(model);
        agentRef.set(agent);

        // WHEN
        agent.call("go", monitor);

        // THEN — delivered with the round …
        assertThat(toolResults(agent.getMemory().getCopy()).getFirst().text())
                .contains(TaggedResult.PREFIX + TaggedResult.TAG_QUEUED_USER_MESSAGE)
                .contains("(queued 14:32): bitte abbrechen");
        // AND — never again as the turn-end follow-up marker: the entry is gone, no double delivery …
        assertThat(agent.getMemory().getCopy())
                .noneMatch(m -> ChatMessageUtil.toString(m).contains(UserMessageQueue.QUEUED_MARKER_PREFIX));
        // AND — and the turn ended after its answer instead of running a redundant third round.
        assertThat(agent.getQueuedMessageCount()).isZero();
        assertThat(round.get()).isEqualTo(2);
    }

    @Test
    void attachedBlockIsNotAPureBlockAndSurvivesCompaction() {
        // GIVEN — a real round delivering one urgent entry
        var round = new AtomicInteger();
        var model = modelAnswering(req -> round.incrementAndGet() == 1 ? twoToolCalls() : answer("done"));
        var agent = agent(model);
        agent.queueUrgentMessage("u1");

        // WHEN
        runRound(agent, model);

        // THEN — the block is embedded BEHIND the tool output, so the result is no pure block …
        var result = toolResults(sent.get(1).messages()).getFirst().text();
        assertThat(result).startsWith(OUTPUT)
                .contains(TaggedResult.PREFIX + TaggedResult.TAG_QUEUED_USER_MESSAGE)
                .endsWith(TaggedResult.SUFFIX + TaggedResult.TAG_QUEUED_USER_MESSAGE);
        // AND — which is what keeps it in the compact input (R-TR-2; the trim behaviour itself is
        // pinned by ContextTrimComponentTest#embeddedBlockInToolResultIsNotDropped).
        assertThat(TaggedResult.isTagged(result)).isFalse();
    }

    @Test
    void agentLessLoopLeavesTheUrgentLaneAlone() {
        // GIVEN — a search-style sub-loop: it names an owner but has no agent (LoopOwner.named)
        var round = new AtomicInteger();
        var model = modelAnswering(req -> round.incrementAndGet() == 1 ? twoToolCalls() : answer("done"));
        var agent = agent(model); // the PARENT whose queue holds the entry
        agent.queueUrgentMessage("u1");
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("go"));

        // WHEN
        service.executeLoop(request(agent, model, memory).owner(LoopOwner.named("Search", LlmConfig::searchAgentConfig)));

        // THEN — the parent keeps its entry for its own next round …
        assertThat(agent.getQueuedMessageCount()).isEqualTo(1);
        // AND — and the sub-loop's results stay clean, with no disclosure of a foreign lane.
        assertThat(toolResults(sent.get(1).messages()))
                .allSatisfy(result -> assertThat(result.text()).isEqualTo(OUTPUT));
        assertThat(toolLines).noneMatch(line -> line.startsWith("Reading queued User message:"));
    }

    /**
     * R12 over R11: an askUser result ends with the user's answer, so a late „!" must ride on the
     * NEXT result of the same round — never behind that answer, and never as a second block on the
     * same result.
     */
    @Test
    void urgentArrivingAfterAnAskUserResultRidesOnTheNextResultWithoutOne() {
        // GIVEN — round = [askUser, echo]; the „!" arrives once the question is answered and the
        // echo of the same round is still running
        var arriving = new AtomicInteger();
        var agentRef = new AtomicReference<AiDevAgent>();
        service.addTool(new AskUserFormTool(() -> {
            if (arriving.compareAndSet(0, 1)) agentRef.get().queueUrgentMessage("bitte abbrechen");
        }));
        var round = new AtomicInteger();
        var model = modelAnswering(req -> round.incrementAndGet() == 1
                ? toolCalls("askUser", "echo")
                : answer("done"));
        var agent = agent(model);
        agentRef.set(agent);

        // WHEN
        runRound(agent, model);

        // THEN — the block lands on the first result still free of one …
        var results = toolResults(sent.get(1).messages());
        assertThat(results).hasSize(2);
        assertThat(results.get(1).text()).isEqualTo(OUTPUT + LN + LN
                + TaggedResult.PREFIX + TaggedResult.TAG_QUEUED_USER_MESSAGE + LN
                + "(queued 14:32): bitte abbrechen" + LN
                + TaggedResult.SUFFIX + TaggedResult.TAG_QUEUED_USER_MESSAGE);
        // AND — the askUser result stayed exactly as the tool built it: one block, answer as last line …
        assertThat(results.getFirst().text()).isEqualTo(askUserResultForm());
        // AND — delivered once, so the follow-up turn has nothing left to repeat …
        assertThat(agent.getQueuedMessageCount()).isZero();
        assertThat(toolLines).containsExactly(
                "Reading queued User message: bitte abbrechen (queued 14:32)");
    }

    /**
     * A drain without a consumer would be a silent loss (disclosure is mandatory), so a round whose
     * results all carry a block delivers nothing — the entry waits for the follow-up turn (R8.3).
     */
    @Test
    void askUserOnlyRoundLeavesLateUrgentEntriesQueued() {
        // GIVEN — a round of exactly one askUser result, the „!" arrives after its answer is built
        var arriving = new AtomicInteger();
        var agentRef = new AtomicReference<AiDevAgent>();
        service.addTool(new AskUserFormTool(() -> {
            if (arriving.compareAndSet(0, 1)) agentRef.get().queueUrgentMessage("bitte abbrechen");
        }));
        var round = new AtomicInteger();
        var model = modelAnswering(req -> round.incrementAndGet() == 1
                ? toolCalls("askUser")
                : answer("done"));
        var agent = agent(model);
        agentRef.set(agent);

        // WHEN
        runRound(agent, model);

        // THEN — nothing was attached to any result of the round …
        assertThat(toolResults(sent.get(1).messages()))
                .allSatisfy(result -> assertThat(result.text()).doesNotContain("bitte abbrechen"));
        // AND — and nothing was consumed either: the entry is still queued for the follow-up turn …
        assertThat(agent.getQueuedMessageCount()).isEqualTo(1);
        // AND — with no disclosure line for a delivery that never happened.
        assertThat(toolLines).noneMatch(line -> line.startsWith("Reading queued User message:"));
    }

    // ========== fixture ==========

    /** A tool with a fixed answer and a hook, so a test can let a message arrive mid-round. */
    static class EchoTool implements SmartTool {
        private final Runnable beforeAnswer;

        EchoTool(Runnable beforeAnswer) { this.beforeAnswer = beforeAnswer; }

        @Tool("fake echo tool, answers with a fixed string")
        public String echo() {
            beforeAnswer.run();
            return OUTPUT;
        }

        @Override public void withToolRequest(ToolLoopRequest request) {}
    }

    /**
     * The RESULT FORM of the plugin's askUser (which is not reachable from core): the queued block it
     * rode with, then the answer as the last line. The hook runs after that text is built — the state
     * of the world once the question is through and later tools of the round still run.
     */
    static class AskUserFormTool implements SmartTool {
        private final Runnable afterAnswer;

        AskUserFormTool(Runnable afterAnswer) { this.afterAnswer = afterAnswer; }

        @Tool("fake tool answering in the askUser result form")
        public String askUser() {
            var answer = askUserResultForm();
            afterAnswer.run();
            return answer;
        }

        @Override public void withToolRequest(ToolLoopRequest request) {}
    }

    private static String askUserResultForm() {
        return QueuedUserMessages.block(List.of("(queued 14:32): alt")) + LN + "yes";
    }

    /** An agent owning {@link #service} — so its turn and the test drive the same tool set. */
    private AiDevAgent agent(StreamingChatModel model) {
        var agent = new AiDevAgent(configured(model), service);
        AgentQueueSeam.setMessageQueue(agent,
                new UserMessageQueue(10_000, Clock.fixed(Instant.ofEpochMilli(AT_1432), ZoneOffset.UTC)));
        return agent;
    }

    /** One iteration with two tool calls, then the answer that ends the loop — through the real loop. */
    private void runRound(AiDevAgent agent, StreamingChatModel model) {
        var memory = agent.getMemory();
        memory.add(UserMessage.from("go"));
        service.executeLoop(request(agent, model, memory).owner(LoopOwner.of(agent, base -> agent.getConfig())));
    }

    private ToolLoopRequest request(AiDevAgent agent, StreamingChatModel model, ThreadSafeMemory memory) {
        return ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(configured(model))
                .monitor(monitor)
                .build();
    }

    private ConfiguredChatModel configured(StreamingChatModel model) {
        return new ConfiguredChatModel(LlmConfig.builder().model("mock").build(), model);
    }

    private ChatResponse twoToolCalls() {
        return toolCalls("echo", "echo");
    }

    /** One AI answer containing exactly the given tool calls, in that order. */
    private ChatResponse toolCalls(String... names) {
        var requests = new ArrayList<ToolExecutionRequest>();
        for (int i = 0; i < names.length; i++) {
            requests.add(ToolExecutionRequest.builder()
                    .id(String.valueOf(i + 1)).name(names[i]).arguments("{}").build());
        }
        return ChatResponse.builder().aiMessage(AiMessage.builder()
                .toolExecutionRequests(requests).build()).build();
    }

    private ChatResponse answer(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }

    /** Captures every request the loop sends — the assertions read the payload, not just the memory. */
    private StreamingChatModel modelAnswering(Function<ChatRequest, ChatResponse> handler) {
        var model = mock(StreamingChatModel.class);
        doAnswer(inv -> {
            ChatRequest request = inv.getArgument(0, ChatRequest.class);
            sent.add(request);
            inv.getArgument(1, StreamingChatResponseHandler.class).onCompleteResponse(handler.apply(request));
            return null;
        }).when(model).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        return model;
    }

    private static List<ToolExecutionResultMessage> toolResults(List<ChatMessage> messages) {
        return messages.stream().filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast).toList();
    }
}
