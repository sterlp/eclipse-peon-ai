package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * R-CT-7: what a listener sharing ONE monitor between an owner and a delegated member sees. The
 * compact signal is the single, named {@code onCompactCompleted}; the owner's one arrives after the
 * fresh question entered its memory.
 */
@Timeout(10)
class AbstractAgentTurnEventsTest {

    private static final int BUDGET = 80_000;

    /** Records every event of the shared monitor, in order; reads the owner's memory at its compact event. */
    static class RecordingMonitor implements AiMonitor {
        final List<String> events = Collections.synchronizedList(new ArrayList<>());
        final List<String> compacts = Collections.synchronizedList(new ArrayList<>());
        final AtomicReference<AbstractAgent> owner = new AtomicReference<>();
        volatile boolean ownerMemoryHadQuestionAtCompact;

        @Override public void onChatResponse(SimpleMessage m) {}
        @Override public void onCallStart(String userMessage) { events.add("callStart:" + userMessage); }
        @Override public void onChatMessage(int iteration, ChatRequest.Builder request) { events.add("chatMessage"); }
        @Override public void onCompactCompleted(String agentName, CompactResult result, boolean withinTurn) {
            compacts.add(agentName + "=" + result.status());
            events.add("compact:" + agentName);
            if ("Jon".equals(agentName)) {
                ownerMemoryHadQuestionAtCompact = owner.get().getMemory().getCopy().stream()
                        .anyMatch(m -> m instanceof UserMessage um && ChatMessageUtil.toString(um).contains("go"));
            }
        }
    }

    private static AbstractAgent agent(String name, ThreadSafeMemory memory, Consumer<ChatRequest> onMainCall) {
        var config = LlmConfig.builder().model("mock").autoCompactAfter(BUDGET)
                .modelConfigs(Map.of(AgentModelConfig.COMPACT,
                        new AgentModelConfig(null, null, "compact-model", null, null, null)))
                .build();
        var model = new StreamMock().buildMock(r -> {
            if ("compact-model".equals(r.modelName())) {
                return ChatResponse.builder().aiMessage(AiMessage.from("Summary of " + name)).build();
            }
            onMainCall.accept(r);
            return ChatResponse.builder().aiMessage(AiMessage.from("OK"))
                    .tokenUsage(new TokenUsage(100, 10, 110)).build();
        });
        return new AbstractAgent(new ConfiguredChatModel(config, model), new ToolService(), memory) {
            @Override public String getName() { return name; }
            @Override public String getSystemPrompt() { return name; }
        };
    }

    // UC-CT-17
    @Test
    void delegationTurnFiresOnlyTheOwnersCompactEvent() {
        // GIVEN — an owner "Jon" over the budget (pre-turn compact) and a member "Mek" with a short
        // history, both on ONE monitor — like a delegation, where the member streams into the owner's monitor
        var jonMemory = new ThreadSafeMemory();
        jonMemory.add(UserMessage.from("m1"));
        jonMemory.add(AiMessage.from("m2"));
        jonMemory.add(UserMessage.from("m3"));
        jonMemory.addResult(ChatResponse.builder().aiMessage(AiMessage.from("m4"))
                .tokenUsage(new TokenUsage(90_000, 0, 90_000)).build());
        var mekMemory = new ThreadSafeMemory();
        mekMemory.add(UserMessage.from("k1"));
        mekMemory.add(AiMessage.from("k2"));
        mekMemory.add(UserMessage.from("k3"));
        var mek = agent("Mek", mekMemory, r -> {});
        var monitor = new RecordingMonitor();
        var delegated = new AtomicBoolean();
        var jon = agent("Jon", jonMemory, r -> {
            if (delegated.compareAndSet(false, true)) {
                // the delegation: the member runs a turn and a compact on the owner's monitor
                mek.call("help", monitor);
                mek.compact(monitor);
            }
        });
        monitor.owner.set(jon);

        // WHEN — the owner's turn starts with a fresh question
        jon.call("go", monitor);

        // THEN — exactly one compact event per agent, each under its OWN name, none re-fired at turn end
        assertThat(monitor.compacts).containsExactly("Jon=COMPACTED", "Mek=COMPACTED");
        // AND — the owner's event came after its question entered the memory …
        assertThat(monitor.ownerMemoryHadQuestionAtCompact).isTrue();
        // AND — … and before its own onCallStart (the member's own callStart comes later)
        assertThat(monitor.events.indexOf("compact:Jon")).isLessThan(monitor.events.indexOf("callStart:go"));
        // Honest scope: this proves only the agent half — the one compact signal is the single, named
        // onCompactCompleted. That the view ignores the member's onCallStart/onChatMessage is *missing
        // code* in AIChatView (field + override deleted) and not headless testable -> manual smoke (R-CT-7).
    }
}
