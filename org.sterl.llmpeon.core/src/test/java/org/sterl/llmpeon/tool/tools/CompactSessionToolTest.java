package org.sterl.llmpeon.tool.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.CoreTestFixtures;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

class CompactSessionToolTest {

    private StreamMock streamMock;

    @BeforeEach
    void beforeEach() {
        streamMock = new StreamMock();
    }

    @Test
    void testCompactSessionUsesConfiguredCompactModel() {
        // GIVEN — config with a compact record model="compact-specific-model", a real owning agent
        var config = LlmConfig.builder()
                .model("default-model")
                .modelConfigs(Map.of(AgentModelConfig.COMPACT,
                        new AgentModelConfig(null, null, "compact-specific-model", null, null, null)))
                .build();
        var cm = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: Test context summary"))
                .build());
        var configuredModel = new ConfiguredChatModel(config, cm);
        var agent = new AiDevAgent(configuredModel, new ToolService());
        agent.getMemory().add(UserMessage.from("First message"));
        agent.getMemory().add(AiMessage.from("AI response 1"));
        agent.getMemory().add(UserMessage.from("Second message"));
        agent.getMemory().add(AiMessage.from("AI response 2"));

        var subject = new CompactSessionTool();
        subject.withToolRequest(ToolLoopRequest.builder()
                .chatModel(configuredModel)
                .memory(agent.getMemory())
                .agent(agent)
                .build());

        // WHEN
        subject.compactSession(null);

        // THEN — the compressor request should have modelName="compact-specific-model"
        assertThat(streamMock.getLastRequest()).isNotNull();
        assertThat(streamMock.getLastRequest().modelName()).isEqualTo("compact-specific-model");
    }

    @Test
    void testCompactSessionWithoutCompactModelUsesDefault() {
        // GIVEN — config without compactModel (null), a real owning agent
        var config = LlmConfig.builder()
                .model("default-model")
                .build();
        var cm = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: Test context summary"))
                .build());
        var configuredModel = new ConfiguredChatModel(config, cm);
        var agent = new AiDevAgent(configuredModel, new ToolService());
        agent.getMemory().add(UserMessage.from("Test message"));
        agent.getMemory().add(AiMessage.from("AI response"));
        agent.getMemory().add(UserMessage.from("Third message")); // R16 guard minimum of 3

        var subject = new CompactSessionTool();
        subject.withToolRequest(ToolLoopRequest.builder()
                .chatModel(configuredModel)
                .memory(agent.getMemory())
                .agent(agent)
                .build());

        // WHEN
        subject.compactSession(null);

        // THEN — the compressor request should have no modelName override (null means provider default)
        assertThat(streamMock.getLastRequest()).isNotNull();
        assertThat(streamMock.getLastRequest().modelName()).isNull();
    }

    @Test
    void testCompactSessionDelegatesToAgent() {
        // GIVEN — a request with an agent set
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("Test message"));
        memory.add(AiMessage.from("AI response"));

        var config = LlmConfig.builder().model("test").build();
        var cm = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: Compressed summary"))
                .build());
        var configuredModel = new ConfiguredChatModel(config, cm);

        AtomicBoolean compressCalled = new AtomicBoolean(false);
        AiAgent mockAgent = new AiAgent() {
            @Override public String getName() { return "test-agent"; }
            @Override public String getSystemPrompt() { return "system"; }
            @Override public ChatResponse call(String message, AiMonitor monitor) { return null; }
            @Override public CompactResult compact(AiMonitor monitor) {
                compressCalled.set(true);
                return CoreTestFixtures.compactedResult();
            }
            @Override public ThreadSafeMemory getMemory() { return memory; }
            @Override public void clear() {}
            @Override public boolean isToolActive(SmartToolExecutor exec) { return true; }
            @Override public boolean isMcpToolActive(String toolName) { return true; }
            @Override public int tokenContextUsedInPercent() { return 0; }
            // never called in this test context
            @Override public List<ChatMessage> buildStaticMessages(AiMonitor monitor) { return List.of(); }
        };

        var toolRequest = ToolLoopRequest.builder()
                .chatModel(configuredModel)
                .memory(memory)
                .agent(mockAgent)
                .build();

        var subject = new CompactSessionTool();
        subject.withToolRequest(toolRequest);

        // WHEN
        String result = subject.compactSession(null);

        // THEN — agent.compact was called
        assertThat(compressCalled).isTrue();
        // AND — the tool result no longer carries the summary text (SOLL 2026-09-10: the summary
        // lives exclusively as an AiMessage in the memory)
        assertThat(result).doesNotContain("WHAT: Compressed summary");
        // AND — without preserve the result carries the non-colliding marker (SOLL 2026-09-11),
        // preceded by the stats line (R-CIB-6)
        assertThat(result).contains("(nothing preserved)");
    }

    @Test
    void testCompactSessionWithoutAgentThrows() {
        // GIVEN — a request without an agent (mis-wiring)
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("Test message"));
        memory.add(AiMessage.from("AI response"));

        var config = LlmConfig.builder().model("test").build();
        var cm = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("summary"))
                .build());

        var toolRequest = ToolLoopRequest.builder()
                .chatModel(new ConfiguredChatModel(config, cm))
                .memory(memory)
                .build();

        var subject = new CompactSessionTool();
        subject.withToolRequest(toolRequest);

        // WHEN / THEN — the mis-wiring surfaces loudly, nothing runs
        assertThatThrownBy(() -> subject.compactSession(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owning agent");
        assertThat(memory.getCopy()).hasSize(2);
    }

    private static AiAgent compactStub(ThreadSafeMemory memory) {
        return agentReturning(memory, CoreTestFixtures.compactedResult());
    }

    private static AiAgent agentReturning(ThreadSafeMemory memory, CompactResult result) {
        return new AiAgent() {
            @Override public String getName() { return "stub-agent"; }
            @Override public String getSystemPrompt() { return "system"; }
            @Override public ChatResponse call(String message, AiMonitor monitor) { return null; }
            @Override public CompactResult compact(AiMonitor monitor) {
                var summary = AiMessage.aiMessage("SUMMARY-X");
                memory.add(summary);
                return result;
            }
            @Override public ThreadSafeMemory getMemory() { return memory; }
            @Override public void clear() {}
            @Override public boolean isToolActive(SmartToolExecutor exec) { return true; }
            @Override public boolean isMcpToolActive(String toolName) { return true; }
            @Override public int tokenContextUsedInPercent() { return 0; }
            // never called in this test context
            @Override public List<ChatMessage> buildStaticMessages(AiMonitor monitor) { return List.of(); }
        };
    }

    /** Raw text of any message type — UserMessage (single or joined contents), AiMessage, ToolExecutionResult. */
    private static String textOf(ChatMessage m) {
        if (m instanceof UserMessage um) {
            if (um.hasSingleText()) return um.singleText();
            return um.contents().stream()
                    .filter(c -> c instanceof TextContent)
                    .map(c -> ((TextContent) c).text())
                    .collect(Collectors.joining());
        }
        if (m instanceof AiMessage ai) return ai.text() == null ? "" : ai.text();
        if (m instanceof ToolExecutionResultMessage tr) return tr.text() == null ? "" : tr.text();
        return "";
    }

    private static CompactSessionTool compactSessionTool(ThreadSafeMemory memory, AiAgent agent) {
        var config = LlmConfig.builder().model("test").build();
        var cm = new StreamMock().buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("unused"))
                .build());
        var subject = new CompactSessionTool();
        subject.withToolRequest(ToolLoopRequest.builder()
                .chatModel(new ConfiguredChatModel(config, cm))
                .memory(memory)
                .agent(agent)
                .build());
        return subject;
    }

    @Test
    void preserveTextKept() {
        // GIVEN — an agent whose compact() stores the summary "SUMMARY-X" as an AiMessage in the memory
        // and returns a ChatResponse carrying the same text
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("Test message"));
        memory.add(AiMessage.from("AI response"));
        var subject = compactSessionTool(memory, compactStub(memory));

        // WHEN
        String result = subject.compactSession("KEEP-1");

        // THEN — the tool result carries the preserve text, never the summary (SOLL 2026-09-10:
        // the summary lives exclusively as an AiMessage in the memory)
        assertThat(result).contains("KEEP-1");
        assertThat(result).doesNotContain("SUMMARY-X");
        // AND — the memory holds SUMMARY-X exactly once (the AiMessage added by compact()),
        // counted over ALL message types — a UserMessage/ToolResult duplicate would be invisible
        // to an AiMessage-only filter (SOLL 2026-09-11)
        assertThat(memory.getCopy().stream()
                .filter(m -> textOf(m).contains("SUMMARY-X"))
                .count()).isEqualTo(1);
    }

    @Test
    void testCompactSessionWithoutPreserveReturnsMarker() {
        // GIVEN — an agent whose compact() stores the summary "SUMMARY-X" as an AiMessage
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("Test message"));
        memory.add(AiMessage.from("AI response"));
        var subject = compactSessionTool(memory, compactStub(memory));

        // WHEN
        String result = subject.compactSession(null);

        // THEN — tool results are never empty: without preserve the result carries the marker
        // (a non-colliding marker, not a duplicate of the resume UserMessage — SOLL 2026-09-11),
        // preceded by the stats line (R-CIB-6)
        assertThat(result).contains("(nothing preserved)");
    }

    @Test
    void compactedToolResultCarriesStats() {
        // GIVEN — an agent whose compact() returns COMPACTED with concrete stats (R-CIB-6:
        // log and tool result carry the same numbers)
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("Test message"));
        memory.add(AiMessage.from("AI response"));
        var compacted = CompactResult.compacted(new CompactResult.Stats(
                61, 114_000, 40_000, CompactResult.Stage.TOOL_RESULTS, 500_000, 2_300, "compact-model", 1234L));
        var subject = compactSessionTool(memory, agentReturning(memory, compacted));

        // WHEN
        String toolResult = subject.compactSession(null);

        // THEN — the tool result carries the same numbers as the log (resultLine)
        assertThat(toolResult).contains("compressed 61 messages ~114k → input ~40k, result 2k, stage: tool results 6000");
        // AND — without preserve the marker follows the stats line
        assertThat(toolResult).contains("(nothing preserved)");
    }

    // R-CC-10
    @Test
    void skippedSmallLineCarriesTokenDiagnosis() {
        // GIVEN — a small memory (2 messages < MIN_COMPACT_MESSAGES) with a reported usage; the agent skips
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("Test message"));
        memory.addResult(ChatResponse.builder()
                .aiMessage(AiMessage.from("AI response"))
                .tokenUsage(new TokenUsage(80211, 0, 80211))
                .build());
        var tools = new ArrayList<String>();
        var subject = compactSessionToolWithMonitor(memory, skipStub(memory), tools);

        // WHEN
        subject.compactSession(null);

        // THEN — the skip LOG line carries all three diagnosis fields (memory exact, model = provider input)
        assertThat(tools).anySatisfy(line -> assertThat(line)
                .contains("skipped because of small context")
                .contains("memory=")
                .contains("(estimate=false)")
                .contains("model=80211")
                .contains(" estimate="));
    }

    // R-CC-10
    @Test
    void skippedSmallLineShowsEstimateMemoryWithoutModel() {
        // GIVEN — one large seed message, no provider usage (Paul's "memory cannot be right" case)
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("X".repeat(100000)));
        var tools = new ArrayList<String>();
        var subject = compactSessionToolWithMonitor(memory, skipStub(memory), tools);

        // WHEN
        subject.compactSession(null);

        // THEN — the skip LOG line shows an estimated memory with no model value
        assertThat(tools).anySatisfy(line -> {
            var matcher = java.util.regex.Pattern.compile("memory=(\\d+)").matcher(line);
            assertThat(matcher.find()).isTrue();
            assertThat(Integer.parseInt(matcher.group(1))).isGreaterThan(20000);
            assertThat(line).contains("(estimate=true)").contains("model=n/a");
        });
    }

    /** An agent whose compact() returns SKIPPED_SMALL without touching the memory (the diagnosis is read after). */
    private static AiAgent skipStub(ThreadSafeMemory memory) {
        return new AiAgent() {
            @Override public String getName() { return "stub-agent"; }
            @Override public String getSystemPrompt() { return "system"; }
            @Override public ChatResponse call(String message, AiMonitor monitor) { return null; }
            @Override public CompactResult compact(AiMonitor monitor) { return CompactResult.skippedSmall(); }
            @Override public ThreadSafeMemory getMemory() { return memory; }
            @Override public void clear() {}
            @Override public boolean isToolActive(SmartToolExecutor exec) { return true; }
            @Override public boolean isMcpToolActive(String toolName) { return true; }
            @Override public int tokenContextUsedInPercent() { return 0; }
            @Override public List<ChatMessage> buildStaticMessages(AiMonitor monitor) { return List.of(); }
        };
    }

    /** A compactSession tool wired to a monitor capturing the onTool LOG lines into {@code tools}. */
    private static CompactSessionTool compactSessionToolWithMonitor(ThreadSafeMemory memory, AiAgent agent, List<String> tools) {
        var config = LlmConfig.builder().model("test").build();
        var cm = new StreamMock().buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("unused"))
                .build());
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onTool(String message) { tools.add(message); }
        };
        var subject = new CompactSessionTool();
        subject.withToolRequest(ToolLoopRequest.builder()
                .chatModel(new ConfiguredChatModel(config, cm))
                .memory(memory)
                .agent(agent)
                .monitor(monitor)
                .build());
        return subject;
    }
}
