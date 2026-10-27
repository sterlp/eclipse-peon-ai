package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.OnPartialAiResponse;
import org.sterl.llmpeon.tool.SmartTool;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * R20 (org.sterl.llmpeon/docs/streaming-display.md): the turn start belongs to the agent — every
 * chunk of one turn carries the time the agent got its working flag, whatever the bridge or the
 * compressor does in between. The agent clock is moved by hours between the calls, so a chunk that
 * carries "now" instead of the turn start can never be equal by accident.
 */
@Timeout(10)
class AbstractAgentTurnTimeTest {

    static final Instant T0 = Instant.parse("2020-01-01T10:00:00Z");

    /** Clock the test moves by hand. */
    static final class MutableClock extends Clock {
        private volatile Instant instant;
        MutableClock(Instant start) { this.instant = start; }
        void advance(Duration d) { instant = instant.plus(d); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    /** A harmless tool used to drive a second loop round. */
    static class ProbeTool implements SmartTool {
        @Tool("probe")
        public String probe() { return "ok"; }
        @Override public void withToolRequest(ToolLoopRequest request) {}
    }

    /** Records the {@code startedAt} of every streaming chunk, in order. */
    static class ChunkMonitor implements AiMonitor {
        final List<Instant> startedAt = new ArrayList<>();
        final AtomicBoolean canceled = new AtomicBoolean();
        @Override public void onChatResponse(SimpleMessage m) {}
        @Override public void onStreamingChunk(OnPartialAiResponse chunk) { startedAt.add(chunk.startedAt()); }
        @Override public boolean isCanceled() { return canceled.get(); }
    }

    private static ChatResponse answer(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.aiMessage(text)).build();
    }

    private static ChatResponse toolCall(String name) {
        return ChatResponse.builder().aiMessage(AiMessage.builder()
                .toolExecutionRequests(List.of(ToolExecutionRequest.builder().id("1").name(name).arguments("{}").build()))
                .build()).build();
    }

    private static AiDevAgent agent(MutableClock clock, ToolService tools, Function<ChatRequest, ChatResponse> model) {
        var config = LlmConfig.builder().model("mock").build();
        var agent = new AiDevAgent(new ConfiguredChatModel(config, new StreamMock().buildMock(model)), tools);
        agent.setClock(clock);
        return agent;
    }

    private static void seedThreeMessages(AiDevAgent agent) {
        agent.addMessage(UserMessage.from("m1"));
        agent.addMessage(AiMessage.from("m2"));
        agent.addMessage(UserMessage.from("m3"));
    }

    // R20
    @Test
    void toolLoopCallsOfOneTurnCarrySameStartedAt() {
        // GIVEN — a turn with two loop rounds, the clock an hour later at every model call
        var clock = new MutableClock(T0);
        var tools = new ToolService();
        tools.addTool(new ProbeTool());
        var round = new AtomicInteger();
        var agent = agent(clock, tools, r -> {
            clock.advance(Duration.ofHours(1));
            return round.incrementAndGet() == 1 ? toolCall("probe") : answer("done");
        });
        var monitor = new ChunkMonitor();

        // WHEN
        agent.call("go", monitor);

        // THEN — START and END of both rounds show the turn start, not the start of their own call
        assertThat(round.get()).isEqualTo(2);
        assertThat(monitor.startedAt).hasSize(4).containsOnly(T0);
    }

    // R20
    @Test
    void compressorChunksInTurnCarryTurnStartOfTheTurn() {
        // GIVEN — a turn whose model call compacts the same agent (the in-turn trigger), an hour later
        var clock = new MutableClock(T0);
        var agentRef = new AtomicReference<AiDevAgent>();
        var monitor = new ChunkMonitor();
        var compacted = new AtomicBoolean();
        var agent = agent(clock, new ToolService(), r -> {
            if (compacted.compareAndSet(false, true)) {
                clock.advance(Duration.ofHours(1));
                agentRef.get().compact(monitor);
            }
            return answer("summary");
        });
        agentRef.set(agent);
        seedThreeMessages(agent);

        // WHEN
        agent.call("go", monitor);

        // THEN — main START, compressor START + END, main END: the compressor never restarts "Started"
        assertThat(monitor.startedAt).hasSize(4).containsOnly(T0);
    }

    // R20
    @Test
    void delegatedMemberHasOwnStartAndOwnerKeepsHis() {
        // GIVEN — an owner at T0 that delegates to a member inside its model call (the member runs five
        // minutes later on its own clock), then compacts itself in the same turn; ONE shared monitor
        var ownerClock = new MutableClock(T0);
        var memberClock = new MutableClock(T0.plus(Duration.ofMinutes(5)));
        var t1 = memberClock.instant();
        var monitor = new ChunkMonitor();
        var memberRef = new AtomicReference<AiDevAgent>();
        var ownerRef = new AtomicReference<AiDevAgent>();
        var first = new AtomicBoolean();
        var member = agent(memberClock, new ToolService(), r -> answer("member says hi"));
        memberRef.set(member);
        var owner = agent(ownerClock, new ToolService(), r -> {
            if (first.compareAndSet(false, true)) {
                memberRef.get().call("help", monitor);
                ownerRef.get().compact(monitor);
            }
            return answer("owner done");
        });
        ownerRef.set(owner);
        seedThreeMessages(owner);

        // WHEN
        owner.call("go", monitor);

        // THEN — owner START, member START + END, owner's compressor START + END, owner END
        assertThat(monitor.startedAt).containsExactly(T0, t1, t1, T0, T0, T0);
    }

    // R20
    @Test
    void idleAgentCompactIsItsOwnTurn() {
        // GIVEN — an agent that ran a turn at T0 and has been idle for two hours
        var clock = new MutableClock(T0);
        var agent = agent(clock, new ToolService(), r -> answer("ok"));
        agent.call("first", new ChunkMonitor());
        agent.addMessage(UserMessage.from("m3"));
        clock.advance(Duration.ofHours(2));
        var t2 = clock.instant();
        var monitor = new ChunkMonitor();

        // WHEN — the user compacts the idle agent (button): it takes the working flag itself
        agent.compact(monitor);

        // THEN — the compressor's chunks show this compact's start, not the stale turn of two hours ago
        assertThat(monitor.startedAt).isNotEmpty().containsOnly(t2);
    }

    // R20
    @Test
    void canceledTurnDoesNotLeakItsStart() {
        // GIVEN — a turn the user stopped
        var clock = new MutableClock(T0);
        var agent = agent(clock, new ToolService(), r -> answer("ok"));
        var stopped = new ChunkMonitor();
        stopped.canceled.set(true);
        try {
            agent.call("first", stopped);
        } catch (RuntimeException expectedOnCancel) {
            // a cancel may surface as an exception — only the next turn is under test
        }
        assertThat(agent.isWorking()).isFalse();

        // WHEN — the next turn starts an hour later
        clock.advance(Duration.ofHours(1));
        var t2 = clock.instant();
        var monitor = new ChunkMonitor();
        agent.call("second", monitor);

        // THEN — it shows its own start
        assertThat(monitor.startedAt).isNotEmpty().containsOnly(t2);
    }
}
