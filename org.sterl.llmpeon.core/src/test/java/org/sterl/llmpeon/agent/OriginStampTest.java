package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.compact.CompactFeedback;
import org.sterl.llmpeon.context.SimpleContextItem;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.OnPartialAiResponse;
import org.sterl.llmpeon.shared.OwnerMonitor;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Every line a turn emits carries the id of the agent that emitted it
 * (docs/adr/0078-origin-in-core-owner-monitor.md). Guarded per <b>emitter</b> — one case per entry
 * point of {@link AbstractAgent}, no reflection over method signatures: what matters is that a real
 * turn produces stamped lines, wherever inside the turn they were built.
 */
@Timeout(30)
class OriginStampTest {

    private StreamMock streamMock;

    @BeforeEach
    void beforeEach() {
        streamMock = new StreamMock();
    }

    @Test
    void callEntryLinesCarryTheCallingAgent() {
        // GIVEN — a turn that emits at its own entry: the 📋 line of a labeled context item, and a
        // user message that arrives while the turn is still running
        var stamps = new Stamps();
        var queuedMidTurn = new AtomicBoolean();
        var agentRef = new AtomicReference<AiDevAgent>();
        var agent = new AiDevAgent(new ConfiguredChatModel(config(), streamMock.buildMock(r -> {
            if (queuedMidTurn.compareAndSet(false, true)) agentRef.get().queueMessage("queued question");
            return ChatResponse.builder().aiMessage(AiMessage.from("OK")).build();
        })), new ToolService());
        agentRef.set(agent);
        agent.setStaticContext(List.of(new SimpleContextItem("docs/memory.md", "memory")));

        // WHEN
        agent.call("fresh question", stamps);

        // THEN — the loading line of the system prompt belongs to the agent …
        assertThat(stamps.lines).anyMatch(m -> m.message().startsWith("📋 Loading docs/memory.md")
                && "Dev".equals(m.origin()));
        // AND — so does the entry line for a message that joined the running turn
        var queuedLine = stamps.lines.stream()
                .filter(m -> m.message().startsWith("Reading queued User message:"))
                .findFirst().orElseThrow(() -> new AssertionError("no queued-message line, got " + stamps));
        assertThat(queuedLine.origin()).isEqualTo("Dev");
        // AND — nothing escaped unstamped
        assertThat(stamps.lines).allSatisfy(m -> assertThat(m.origin())
                .as("origin of %s", m.message()).isEqualTo("Dev"));
    }

    @Test
    void delegatedTurnLinesCarryTheMemberName() {
        // GIVEN — a member turn running on the monitor Jon passes down (its own wrapper is outside)
        var member = devAgent();
        member.setStaticContext(List.of(new SimpleContextItem("AGENTS-DEV.md", "member rules")));
        var stamps = new Stamps();
        var jonsMonitor = new OwnerMonitor("Jon", stamps);

        // WHEN
        member.call("implement it", jonsMonitor);

        // THEN — the member owns its lines, Jon's outer frame renames nothing
        assertThat(stamps.lines).isNotEmpty();
        assertThat(stamps.lines).allSatisfy(m -> assertThat(m.origin())
                .as("origin of %s", m.message()).isEqualTo("Dev"));
    }

    @Test
    void compactStartAndEndLinesBelongToTheCompactingAgent() {
        // GIVEN — an over-budget member, compacted the way the roster button does it
        var member = overBudgetDevAgent();
        var stamps = new Stamps();

        // WHEN
        member.compact(stamps);

        // THEN — both R-CT-8 lines carry the compacting agent's id
        assertThat(stamps.lines).anyMatch(m -> m.message().startsWith(CompactFeedback.START_PREFIX)
                && "Dev".equals(m.origin()));
        var endLine = stamps.lines.stream()
                .filter(m -> m.message().contains(CompactFeedback.END_MARKER))
                .findFirst().orElseThrow(() -> new AssertionError("no compact end line, got " + stamps.lines));
        assertThat(endLine.origin()).isEqualTo("Dev");
        assertThat(stamps.lines).allSatisfy(m -> assertThat(m.origin())
                .as("origin of %s", m.message()).isEqualTo("Dev"));
    }

    @Test
    void staticMessagesBuildLinesCarryTheAgent() {
        // GIVEN — the outside call of the compact tooling (HandleToolLoopCompactComponent, CompactSessionTool)
        var agent = devAgent();
        agent.setStaticContext(List.of(new SimpleContextItem("docs/memory.md", "memory")));
        var stamps = new Stamps();

        // WHEN
        var messages = agent.buildStaticMessages(stamps);

        // THEN — the prompt was built, and its loading line is ours
        assertThat(messages).hasSize(1);
        assertThat(stamps.lines).isNotEmpty();
        assertThat(stamps.lines).allSatisfy(m -> assertThat(m.origin())
                .as("origin of %s", m.message()).isEqualTo("Dev"));
    }

    @Test
    void asyncTurnLinesCarryTheAgent() {
        // GIVEN — a model that streams its answer chunk by chunk
        var agent = new AiDevAgent(new ConfiguredChatModel(config(), streamingModel()), new ToolService());
        var stamps = new Stamps();

        // WHEN
        agent.call("fresh question", stamps);

        // THEN — every streamed chunk is owned, not just the lines
        assertThat(stamps.chunks).isNotEmpty();
        assertThat(stamps.chunks).allSatisfy(r -> assertThat(r.origin())
                .as("origin of chunk %s", r.value()).isEqualTo("Dev"));
    }

    // ---------- fixtures ----------

    private AiDevAgent devAgent() {
        return new AiDevAgent(new ConfiguredChatModel(config(), model()), new ToolService());
    }

    /** Over the raw budget (90 000 > 80 000) with a compactable history. */
    private AiDevAgent overBudgetDevAgent() {
        var agent = new AiDevAgent(new ConfiguredChatModel(config(), model()), new ToolService());
        agent.addMessage(UserMessage.from("m1"));
        agent.addMessage(AiMessage.from("m2"));
        agent.addMessage(UserMessage.from("m3"));
        agent.getMemory().addResult(ChatResponse.builder()
                .aiMessage(AiMessage.from("m4"))
                .tokenUsage(new TokenUsage(90_000, 0, 90_000))
                .build());
        return agent;
    }

    private LlmConfig config() {
        return LlmConfig.builder()
                .model("mock")
                .autoCompactAfter(80_000)
                .modelConfigs(Map.of(AgentModelConfig.COMPACT,
                        new AgentModelConfig(null, null, "compact-model", null, null, null)))
                .build();
    }

    /** Main model answers in plain text, the compressor returns a summary. */
    private StreamingChatModel model() {
        return streamMock.buildMock(r -> "compact-model".equals(r.modelName())
                ? ChatResponse.builder().aiMessage(AiMessage.from("Summary of the session")).build()
                : ChatResponse.builder().aiMessage(AiMessage.from("OK")).build());
    }

    private StreamingChatModel streamingModel() {
        return streamMock.buildStreamingMock(r -> ChatResponse.builder().aiMessage(AiMessage.from("Hello")).build(),
                "Hel", "lo");
    }

    /** TOOL and PROBLEM lines arrive here too — an {@link OwnerMonitor} builds them, never onTool. */
    private static class Stamps implements AiMonitor {
        final List<SimpleMessage> lines = new ArrayList<>();
        final List<OnPartialAiResponse> chunks = new ArrayList<>();

        @Override public void onChatResponse(SimpleMessage message) { lines.add(message); }
        @Override public void onStreamingChunk(OnPartialAiResponse r) { chunks.add(r); }

        @Override public String toString() {
            var all = new ArrayList<String>();
            lines.forEach(m -> all.add(m.role() + " " + m.message()));
            chunks.forEach(r -> all.add("chunk " + r.value()));
            return String.join(" | ", all);
        }
    }
}
