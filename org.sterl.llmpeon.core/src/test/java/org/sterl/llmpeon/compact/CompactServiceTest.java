package org.sterl.llmpeon.compact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.mock.MockLlmServer;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * CompactService tests (R-CIB-3 entry log, R-CIB-6 result line, slot routing) — the request payload is
 * captured and asserted, not only "a call happened" (AGENTS-DEV test honesty).
 */
class CompactServiceTest {

    private MockLlmServer server;
    private MockLlmServer serverB;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockLlmServer(0);
        server.start();
        serverB = new MockLlmServer();
        serverB.start();
        Thread.sleep(100);
    }

    @AfterEach
    void tearDown() {
        server.stop();
        serverB.stop();
    }

    // ---------- entry log / result log ----------

    @Test
    void entryDebugLogExactlyOnceWithInitialValues() {
        // GIVEN — two messages, a capturing log
        var log = new CapturingLog();
        var engine = engineWithSummary(log);
        var messages = List.<ChatMessage>of(UserMessage.from("Foo"), AiMessage.from("Bar"));
        var budget = 100000;

        // WHEN
        var result = engine.compact("dev-agent", messages, budget, "", null, true, AiMonitor.NULL_MONITOR).result();

        // THEN — exactly ONE debug log with the initial values, before any truncation (R-CIB-3)
        var estimate = ChatMessageUtil.estimateTokens(messages);
        var debugs = log.lines(CapturingLog.Level.DEBUG);
        assertThat(debugs).hasSize(1);
        assertThat(debugs.getFirst())
                .contains("agent=dev-agent")
                .contains("messageCount=2")
                .contains("estimatedInputTokens=" + estimate)
                .contains("budget=" + budget)
                .contains("thinkingEnabled=false");

        // AND — the compact itself succeeded
        assertThat(result.status()).isEqualTo(CompactResult.Status.COMPACTED);
    }
    @Test
    void entryLogCarriesTokenDiagnosis() {
        // GIVEN — two messages, a capturing log, and the diagnosis string the agent passes (R-CC-10)
        var log = new CapturingLog();
        var engine = engineWithSummary(log);
        var messages = List.<ChatMessage>of(UserMessage.from("Foo"), AiMessage.from("Bar"));
        var diagnosis = " | memory=289493(estimate=false) model=80211 estimate=82450";

        // WHEN
        engine.compact("dev-agent", messages, 100000, diagnosis, null, true, AiMonitor.NULL_MONITOR);

        // THEN — the single debug log carries the diagnosis right after thinkingEnabled, before the
        // diagnostic block (newline) — the "exactly one debug log" contract still holds
        var debugs = log.lines(CapturingLog.Level.DEBUG);
        assertThat(debugs).hasSize(1);
        assertThat(debugs.getFirst())
                .contains("thinkingEnabled=false" + diagnosis)
                .contains(diagnosis + "\n");
    }


    @Test
    void diagnosticBlockListsPerMessageDropsWhenCapped() {
        // GIVEN — a tool-heavy history that reaches stage 2 (6000-char caps), capturing log
        var log = new CapturingLog();
        var engine = engineWithSummary(log);
        var messages = List.<ChatMessage>of(
                AiMessage.builder().text("a").thinking("T".repeat(12000))
                        .toolExecutionRequests(List.of(ToolExecutionRequest.builder().id("1").name("write")
                                .arguments("A".repeat(12000)).build())).build(),
                ToolExecutionResultMessage.from("id", "write", "R".repeat(12000)));

        // WHEN
        engine.compact("dev", messages, 7000, "", null, true, AiMonitor.NULL_MONITOR);

        // THEN — the single debug log carries the per-message drops (tool named) and the output summary
        var debugs = log.lines(CapturingLog.Level.DEBUG);
        assertThat(debugs).hasSize(1);
        assertThat(debugs.getFirst())
                .contains("tool(write)")
                .contains(" -> ")
                .contains("(dropped ")
                .contains("stage=TOOL_RESULTS")
                .contains("dropRate=");
    }

    @Test
    void diagnosticBlockOmitsPerMessageLinesWhenNothingDropped() {
        // GIVEN — a small history, well under budget
        var log = new CapturingLog();
        var engine = engineWithSummary(log);

        // WHEN
        engine.compact("dev", List.of(UserMessage.from("Foo"), AiMessage.from("Bar")), 100000, "", null, true, AiMonitor.NULL_MONITOR);

        // THEN — only the output summary line, no per-message drop lines
        var debugs = log.lines(CapturingLog.Level.DEBUG);
        assertThat(debugs).hasSize(1);
        assertThat(debugs.getFirst())
                .contains("output: 2 rendered")
                .contains("stage=NONE")
                .doesNotContain(" -> ");
    }

    @Test
    void zeroBudget_onlyEntryLog() {
        // GIVEN — budget off (≤ 0): the entry log is the only log (R-CIB-1)
        var log = new CapturingLog();
        var engine = engineWithSummary(log);
        var messages = List.<ChatMessage>of(UserMessage.from("Foo"), AiMessage.from("Bar"));

        // WHEN
        var result = engine.compact("dev-agent", messages, 0, "", null, true, AiMonitor.NULL_MONITOR).result();

        // THEN
        assertThat(log.all()).hasSize(1);
        assertThat(log.all().getFirst()).isEqualTo(CapturingLog.Level.DEBUG);
        assertThat(result.status()).isEqualTo(CompactResult.Status.COMPACTED);
    }

    @Test
    void resultLogLevelMatchesStage() {
        // GIVEN — the same growing history at three budgets: stage 1 / stage 2 / final
        var stage1 = List.<ChatMessage>of(ai("a", "T".repeat(15000)));
        var stage2 = List.<ChatMessage>of(
                AiMessage.builder().text("a").thinking("T".repeat(12000))
                        .toolExecutionRequests(List.of(ToolExecutionRequest.builder().id("1").name("write")
                                .arguments("A".repeat(12000)).build())).build(),
                ToolExecutionResultMessage.from("id", "write", "R".repeat(12000)));
        var finalStage = List.<ChatMessage>of(ai("a", "T".repeat(12000)),
                ToolExecutionResultMessage.from("id", "write", "R".repeat(12000)),
                UserMessage.from("q " + "U".repeat(3000)));

        // WHEN/THEN — info for stage 1, warn for stage 2, error for the final stage (R-CIB-6)
        assertThat(resultLevel(stage1, 3000)).isSameAs(CapturingLog.Level.INFO);
        assertThat(resultLevel(stage2, 7000)).isSameAs(CapturingLog.Level.WARN);
        assertThat(resultLevel(finalStage, 1000)).isSameAs(CapturingLog.Level.ERROR);
    }

    /** Runs one compact with a capturing log; the level of the single "Compact result:" line (fails if missing/duplicated). */
    private static CapturingLog.Level resultLevel(List<ChatMessage> messages, int budget) {
        var log = new CapturingLog();
        var engine = engineWithSummary(log);
        engine.compact("dev", messages, budget, "", null, true, AiMonitor.NULL_MONITOR);
        var resultLines = log.allLines().stream().filter(l -> l.message().contains("Compact result:")).toList();
        assertThat(resultLines).hasSize(1);
        return resultLines.getFirst().level();
    }

    @Test
    void emptyResponse_failedEmptyWithCauseAndNumbers() {
        // GIVEN — the compressor answers with an empty text
        var log = new CapturingLog();
        var streamMock = new StreamMock();
        var cm = streamMock.buildMock(r -> ChatResponse.builder().aiMessage(AiMessage.aiMessage("")).build());
        var engine = new CompactService(new ConfiguredChatModel(LlmConfig.builder().model("test").build(), cm), log);
        var messages = List.<ChatMessage>of(UserMessage.from("Foo"), AiMessage.from("Bar"));

        // WHEN
        var result = engine.compact("dev-agent", messages, 100000, "", null, true, AiMonitor.NULL_MONITOR).result();

        // THEN — FAILED_EMPTY with the cause and the numbers of what was sent
        assertThat(result.status()).isEqualTo(CompactResult.Status.FAILED_EMPTY);
        assertThat(result.cause()).contains("dev-agent");
        assertThat(result.stats().messageCount()).isEqualTo(2);
        assertThat(result.stats().estimateBefore()).isPositive();
        assertThat(result.stats().resultChars()).isZero();

        // AND — the error-level result line carries the failure
        var errors = log.lines(CapturingLog.Level.ERROR);
        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst()).contains("compact failed");
    }

    // ---------- request payload (both directions) ----------

    @Test
    void sendsSystemPromptAndDedupedInput() {
        // GIVEN — StreamMock returns a summary; the last message duplicates the first
        var streamMock = new StreamMock();
        var cm = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: Test summary")).build());
        var engine = new CompactService(new ConfiguredChatModel(LlmConfig.builder().model("test").build(), cm),
                new CapturingLog());
        var monitor = new CapturingMonitor();

        // WHEN
        var result = engine.compact("dev-agent",
                List.of(UserMessage.from("Foo"), AiMessage.from("Bar"), UserMessage.from("Foo")),
                100000, "", null, true, monitor).result();

        // THEN — the request carries the COMPRESS_SYSTEM prompt from compressor.md
        assertThat(streamMock.getLastRequest()).isNotNull();
        var system = streamMock.getLast(SystemMessage.class).orElseThrow();
        assertThat(system.text()).contains("WHAT:");

        // AND — the compact UserMessage carries every distinct message once, with the dedup disclosure
        var compacted = streamMock.getLast(UserMessage.class).orElseThrow();
        assertThat(compacted.singleText()).contains("Foo").contains("Bar");
        assertThat(count(compacted.singleText(), "Foo")).isEqualTo(1);
        assertThat(compacted.singleText()).contains("session truncated").contains("duplicates collapsed: 1");

        // AND — the monitor (chat view) sees the same request and the summary as an AI message
        assertThat(monitor.chatRequests).hasSize(1);
        assertThat(monitor.chatRequests.getFirst().messages()).contains(system);
        assertThat(monitor.responses).anySatisfy(m -> {
            assertThat(m.role()).isEqualTo(SimpleMessage.Type.AI);
            assertThat(m.message()).isEqualTo("WHAT: Test summary");
        });

        // AND — the result carries the stats (dedup is in duplicatesCollapsed, not droppedChars)
        assertThat(result.status()).isEqualTo(CompactResult.Status.COMPACTED);
        assertThat(result.stats().messageCount()).isEqualTo(3);
        assertThat(result.stats().stage()).isEqualTo(CompactResult.Stage.NONE);
    }

    @Test
    void nullResponse_throwsIllegalStateException() {
        // GIVEN — ConfiguredChatModel returns null (simulates streaming failure); Mockito can't
        // mock concrete classes on Java 25, so an anonymous subclass
        var configuredModel = new ConfiguredChatModel(LlmConfig.newOpenAi("test-key")) {
            @Override
            public ChatResponse callBlocking(ChatRequest req, AgentConfig agent, AiMonitor monitor) {
                return null;
            }
        };
        var engine = new CompactService(configuredModel, new CapturingLog());

        // WHEN + THEN — Log OR throw: the throw stays in the call path (R-CC-3)
        assertThatThrownBy(() -> engine.compact("dev-agent", List.of(UserMessage.from("test")), 100000, "", null, true, AiMonitor.NULL_MONITOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI call returned null");
    }

    // ---------- COMPACT slot routing (migrated from the legacy compressor tests) ----------

    @Test
    @Timeout(10)
    void compactSlotRoutesCallToCompactConnection() {
        // GIVEN — base points at serverA; the COMPACT slot carries its own url/model/temperature
        var base = LlmConfig.builder()
                .providerType(AiProvider.OPEN_AI)
                .model("base-model")
                .url(server.getUrl())
                .apiKey("test-key")
                .build();
        var config = base.withModelConfig(AgentModelConfig.COMPACT,
                new AgentModelConfig(serverB.getUrl(), null, "compact-model", null, null, "0.2"));
        serverB.queueResponse("WHAT: compact briefing");
        var engine = new CompactService(new ConfiguredChatModel(config));

        // WHEN — one compact
        var result = engine.compact("dev-agent", List.of(UserMessage.from("Foo"), AiMessage.from("Bar")), 100000, "", null, true, AiMonitor.NULL_MONITOR).result();

        // THEN — the call landed at the COMPACT slot's URL with the slot's model and temperature
        assertThat(result.status()).isEqualTo(CompactResult.Status.COMPACTED);
        assertThat(serverB.getLastRequestBody()).isNotNull();
        var body = parse(serverB.getLastRequestBody());
        assertThat(body.path("model").asText()).isEqualTo("compact-model");
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.2);

        // AND — the base URL received no call
        assertThat(server.getLastRequestBody()).isNull();
    }

    @Test
    @Timeout(10)
    void emptyCompactSlotFallsBackToBaseConnection() {
        // GIVEN — base points at serverA, no COMPACT slot entry at all
        var config = LlmConfig.builder()
                .providerType(AiProvider.OPEN_AI)
                .model("base-model")
                .url(server.getUrl())
                .apiKey("test-key")
                .build();
        server.queueResponse("WHAT: base briefing");
        var engine = new CompactService(new ConfiguredChatModel(config));

        // WHEN
        var result = engine.compact("dev-agent", List.of(UserMessage.from("Foo"), AiMessage.from("Bar")), 100000, "", null, true, AiMonitor.NULL_MONITOR).result();

        // THEN — the call landed at the base URL with the base model
        assertThat(result.status()).isEqualTo(CompactResult.Status.COMPACTED);
        assertThat(server.getLastRequestBody()).isNotNull();
        assertThat(parse(server.getLastRequestBody()).path("model").asText()).isEqualTo("base-model");

        // AND — the second stub received nothing
        assertThat(serverB.getLastRequestBody()).isNull();
    }

    /** Characterization: the COMPACT slot's think value reaches the wire as the provider-specific parameter. */
    @Test
    @Timeout(10)
    void compactSlotThinkReachesTheWire() {
        // GIVEN — base points at serverA; the COMPACT slot carries its own url + a think level
        var base = LlmConfig.builder()
                .providerType(AiProvider.OPEN_AI)
                .model("base-model")
                .url(server.getUrl())
                .apiKey("test-key")
                .build();
        var config = base.withModelConfig(AgentModelConfig.COMPACT,
                new AgentModelConfig(serverB.getUrl(), null, "compact-model", "medium", null, null));
        serverB.queueResponse("WHAT: compact briefing");
        var engine = new CompactService(new ConfiguredChatModel(config));

        // WHEN
        var result = engine.compact("dev-agent", List.of(UserMessage.from("Foo")), 100000, "", null, true, AiMonitor.NULL_MONITOR).result();

        // THEN — the provider-specific think parameter is on the wire at the compact stub
        assertThat(result.status()).isEqualTo(CompactResult.Status.COMPACTED);
        assertThat(parse(serverB.getLastRequestBody()).path("reasoning_effort").asText()).isEqualTo("medium");

        // AND — the base URL received no call
        assertThat(server.getLastRequestBody()).isNull();
    }

    /** Characterization: an Anthropic-based COMPACT slot sends the thinking block on the wire. */
    @Test
    @Timeout(10)
    void compactSlotThinkAnthropicSendsThinkingBlock() {
        // GIVEN — Anthropic base points at serverA; the COMPACT slot carries its own url + generic-on think
        var base = LlmConfig.builder()
                .providerType(AiProvider.ANTHROPIC)
                .model("base-model")
                .url(server.getUrl())
                .apiKey("test-key")
                .build();
        var config = base.withModelConfig(AgentModelConfig.COMPACT,
                new AgentModelConfig(serverB.getUrl(), null, "claude-sonnet-4-5", "true", null, null));
        serverB.queueResponse("WHAT: compact briefing");
        var engine = new CompactService(new ConfiguredChatModel(config));

        // WHEN
        var result = engine.compact("dev-agent", List.of(UserMessage.from("Foo")), 100000, "", null, true, AiMonitor.NULL_MONITOR).result();

        // THEN — the Anthropic thinking block is on the wire at the compact stub
        assertThat(result.status()).isEqualTo(CompactResult.Status.COMPACTED);
        assertThat(parse(serverB.getLastRequestBody()).path("thinking").path("type").asText()).isEqualTo("enabled");

        // AND — the base URL received no call
        assertThat(server.getLastRequestBody()).isNull();
    }

    /** Characterization: the COMPACT slot's extra body — user body wins, reserved key stripped, no duplicate JSON key. */
    @Test
    @Timeout(10)
    void compactSlotExtraBodyMergesUserWinsAndStripsReserved() {
        // GIVEN — base points at serverA; the COMPACT slot carries url, a slot temperature and an
        // extra body colliding with the slot temperature and the reserved model key
        var base = LlmConfig.builder()
                .providerType(AiProvider.OPEN_AI)
                .model("base-model")
                .url(server.getUrl())
                .apiKey("test-key")
                .build();
        var config = base.withModelConfig(AgentModelConfig.COMPACT,
                new AgentModelConfig(serverB.getUrl(), null, "compact-model", null,
                        "{\"foo\":\"bar\",\"model\":\"hacked\",\"temperature\":0.9}", "0.2"));
        serverB.queueResponse("WHAT: compact briefing");
        var engine = new CompactService(new ConfiguredChatModel(config));

        // WHEN
        var result = engine.compact("dev-agent", List.of(UserMessage.from("Foo")), 100000, "", null, true, AiMonitor.NULL_MONITOR).result();

        // THEN — the body keys are on the wire, the user body wins, the reserved key is stripped
        assertThat(result.status()).isEqualTo(CompactResult.Status.COMPACTED);
        var body = parse(serverB.getLastRequestBody());
        assertThat(body.path("foo").asText()).isEqualTo("bar");
        assertThat(body.path("model").asText()).isEqualTo("compact-model");
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.9);
        assertThat(serverB.getLastRequestBody().split("\\\"temperature\\\"", -1)).hasSize(2);

        // AND — the base URL received no call
        assertThat(server.getLastRequestBody()).isNull();
    }

    // ---------- helpers ----------

    private static CompactService engineWithSummary(CapturingLog log) {
        var streamMock = new StreamMock();
        var cm = streamMock.buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.aiMessage("WHAT: Test summary")).build());
        return new CompactService(new ConfiguredChatModel(LlmConfig.builder().model("test").build(), cm), log);
    }

    private static AiMessage ai(String text, String thinking) {
        return AiMessage.builder().text(text).thinking(thinking).build();
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode parse(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse captured request body: " + body, e);
        }
    }

    private static int count(String haystack, String needle) {
        int count = 0;
        for (var i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) count++;
        return count;
    }

    /** Captures the levels and the slf4j-formatted messages of the engine. */
    static final class CapturingLog implements CompactLog {
        enum Level { DEBUG, INFO, WARN, ERROR }

        record Line(Level level, String message) {}

        private final List<Line> lines = new ArrayList<>();

        @Override public void debug(String m, Object... a) { lines.add(new Line(Level.DEBUG, format(m, a))); }
        @Override public void info(String m, Object... a) { lines.add(new Line(Level.INFO, format(m, a))); }
        @Override public void warn(String m, Object... a) { lines.add(new Line(Level.WARN, format(m, a))); }
        @Override public void error(String m, Object... a) { lines.add(new Line(Level.ERROR, format(m, a))); }

        List<String> lines(Level level) {
            return lines.stream().filter(l -> l.level() == level).map(Line::message).toList();
        }

        List<Level> all() {
            return lines.stream().map(Line::level).toList();
        }

        List<Line> allLines() {
            return lines;
        }

        static String format(String template, Object... args) {
            var result = template;
            for (var arg : args) {
                result = result.replaceFirst("\\{}", Matcher.quoteReplacement(String.valueOf(arg)));
            }
            return result;
        }
    }

    /** Monitor capturing what the chat view would see: the LLM request and the response messages. */
    static final class CapturingMonitor implements AiMonitor {
        final List<ChatRequest> chatRequests = new ArrayList<>();
        final List<SimpleMessage> responses = new ArrayList<>();

        @Override
        public void onChatResponse(SimpleMessage message) {
            responses.add(message);
        }

        @Override
        public void onChatMessage(int iteration, ChatRequest.Builder request) {
            chatRequests.add(request.build());
        }
    }
}
