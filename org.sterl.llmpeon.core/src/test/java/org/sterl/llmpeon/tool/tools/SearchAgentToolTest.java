package org.sterl.llmpeon.tool.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.SmartTool;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * R-CC-15 / UC-CC-1 (docs/compact.md): the nested search-agent loop must be isolated from the
 * parent agent — no inherited owning agent, no compact on the parent memory, no
 * parent-attributed compact hint.
 */
class SearchAgentToolTest {

    /** A harmless tool used to drive loop rounds without touching network or filesystem. */
    static class ProbeTool implements SmartTool {
        @Tool("probe")
        public String probe() { return "ok"; }
        @Override public void withToolRequest(ToolLoopRequest request) {}
    }

    /** Records every request handed to executeLoop — the nested request is the one under test. */
    static class RecordingToolService extends ToolService {
        final List<ToolLoopRequest> loops = new ArrayList<>();
        @Override public ChatResponse executeLoop(ToolLoopRequest req) {
            loops.add(req);
            return super.executeLoop(req);
        }
    }

    /** Captures the onTool/onProblem lines the loop emits (monitor passthrough is intended). */
    static class CapturingMonitor implements AiMonitor {
        final List<String> toolLines = new ArrayList<>();
        final List<String> problems = new ArrayList<>();
        @Override public void onChatResponse(SimpleMessage m) {}
        @Override public void onTool(String message) { toolLines.add(message); }
        @Override public void onProblem(String message) { problems.add(message); }
    }

    private static ToolExecutionRequest toolCall(String name, String args) {
        return ToolExecutionRequest.builder().id("1").name(name).arguments(args).build();
    }

    // UC-CC-1
    @Test
    @Timeout(10)
    void shouldNotExposeParentAgentToNestedLoop() {
        // GIVEN — a parent request with an owning agent; the nested loop answers immediately
        var service = new RecordingToolService();
        var cm = new StreamMock().buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.from("found it")).build());
        var parentMemory = new ThreadSafeMemory();
        parentMemory.add(UserMessage.from("parent message"));
        var parent = mock(AiAgent.class);
        when(parent.getName()).thenReturn("Peon-PO");
        var request = ToolLoopRequest.builder()
                .memory(parentMemory)
                .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), cm))
                .monitor(new CapturingMonitor())
                .agent(parent)
                .build();
        var sniffa = service.getTool(SearchAgentTool.class).get();
        sniffa.withToolRequest(request);

        // WHEN
        sniffa.searchAgent("find something");

        // THEN — the nested request must not carry the parent agent (R-CC-15)
        assertThat(service.loops).hasSize(1);
        assertThat(service.loops.get(0).getAgent()).isNotSameAs(parent);
    }

    // UC-CC-1
    @Test
    @Timeout(10)
    void subCompactMustNotClearParentMemory() {
        // GIVEN — the parent agent owns the parent memory; the sub-LLM asks for compactSession
        var service = new RecordingToolService();
        var parentMemory = new ThreadSafeMemory();
        parentMemory.add(UserMessage.from("parent 1"));
        parentMemory.add(AiMessage.from("parent reply 1"));
        parentMemory.add(UserMessage.from("parent 2"));
        var parent = mock(AiAgent.class);
        when(parent.getName()).thenReturn("Peon-PO");
        when(parent.buildStaticMessages(any())).thenReturn(List.of());
        // the mock honors the AiAgent.compact contract: clear + re-seed the agent's own memory
        doAnswer(inv -> {
            parentMemory.clear();
            parentMemory.add(UserMessage.from("Session compacted: sub summary"));
            return CompactResult.compacted(
                    new CompactResult.Stats(3, 100, 50, CompactResult.Stage.NONE, 0, 10, "model", 1, null, true),
                    "sub summary");
        }).when(parent).compact(any());
        var round = new AtomicInteger();
        var cm = new StreamMock().buildMock(r -> round.incrementAndGet() == 1
                ? ChatResponse.builder().aiMessage(AiMessage.builder()
                        .toolExecutionRequests(List.of(toolCall(CompactSessionTool.NAME, "{\"preserve\":\"keep\"}")))
                        .build()).build()
                : ChatResponse.builder().aiMessage(AiMessage.from("done")).build());
        var request = ToolLoopRequest.builder()
                .memory(parentMemory)
                .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), cm))
                .monitor(new CapturingMonitor())
                .agent(parent)
                .build();
        var sniffa = service.getTool(SearchAgentTool.class).get();
        sniffa.withToolRequest(request);

        // WHEN
        sniffa.searchAgent("research");

        // THEN — the parent memory is untouched (no clear/re-seed by a sub-loop compact)
        assertThat(parentMemory.getCopy()).containsExactly(
                UserMessage.from("parent 1"), AiMessage.from("parent reply 1"), UserMessage.from("parent 2"));
        verify(parent, never()).compact(any());
    }

    // UC-CC-1
    @Test
    @Timeout(10)
    void subLoopHintAttributesToSubAgent() {
        // GIVEN — autoCompactAfter so tiny that the sub-loop's estimate trips the hint threshold
        var service = new RecordingToolService();
        service.addTool(new ProbeTool());
        var round = new AtomicInteger();
        var cm = new StreamMock().buildMock(r -> round.incrementAndGet() <= 2
                ? ChatResponse.builder().aiMessage(AiMessage.builder()
                        .toolExecutionRequests(List.of(toolCall("probe", "{}")))
                        .build()).build()
                : ChatResponse.builder().aiMessage(AiMessage.from("done")).build());
        var parentMemory = new ThreadSafeMemory();
        parentMemory.add(UserMessage.from("parent message"));
        var parent = mock(AiAgent.class);
        when(parent.getName()).thenReturn("Peon-PO");
        var monitor = new CapturingMonitor();
        var request = ToolLoopRequest.builder()
                .memory(parentMemory)
                .chatModel(new ConfiguredChatModel(
                        LlmConfig.newOpenAi("foo").toBuilder().autoCompactAfter(1).build(), cm))
                .monitor(monitor)
                .agent(parent)
                .build();
        var sniffa = service.getTool(SearchAgentTool.class).get();
        sniffa.withToolRequest(request);

        // WHEN
        sniffa.searchAgent("research");

        // THEN — no hint line attributes the compact to the parent agent
        assertThat(monitor.toolLines).noneMatch(l -> l.contains("Compact hint for Peon-PO"));
        // AND — the honest fallback landed in the sub-memory (the sub-loop has no compact tool)
        assertThat(service.loops.get(0).getMemory().containsMessage("cannot be compacted")).isTrue();
    }
}
