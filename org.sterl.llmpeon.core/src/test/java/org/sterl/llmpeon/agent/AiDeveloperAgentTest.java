package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.agent.model.TaggedResult;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.compact.CompactFeedback;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.context.SimpleContextItem;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.SmartTool;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.tools.CompactSessionTool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;

public class AiDeveloperAgentTest {

    final ToolService toolService = new ToolService();
    AiDevAgent subject;
    StreamingChatModel cm;
    final AtomicReference<Function<ChatRequest, ChatResponse>> fn = new AtomicReference<>();
    
    public static final AiMessage CALL_ME = AiMessage.builder()
            .toolExecutionRequests(Arrays.asList(ToolExecutionRequest.builder().name(CompactSessionTool.NAME).build()))
            .build();
    /*
    class ClearMemoryTestTool implements SmartTool {
        public static final AiMessage CALL_ME = AiMessage.builder()
                .toolExecutionRequests(Arrays.asList(ToolExecutionRequest.builder().name("clearMemory").build()))
                .build();
        ToolLoopRequest request;
        @Tool(name = "clearMemory")
        public String hello() {
            request.getMemory().clear();
            return "Hello from clearMemory";
        }

        @Override
        public void withToolRequest(ToolLoopRequest request) {
            this.request = request;
        }
    }*/

    /** A harmless tool to drive a loop round without touching network or filesystem. */
    static class ProbeTool implements SmartTool {
        @Tool(name = "probe")
        public String probe() { return "probed"; }
        @Override public void withToolRequest(ToolLoopRequest request) {}
    }

    /**
     * Captures the tool lines of a turn: below an agent entry they arrive as TOOL chat lines, because
     * an {@link org.sterl.llmpeon.shared.OwnerMonitor} builds them there (ADR-0078). Both channels
     * feed one list, so the assertions read as before.
     */
    static class CapturingMonitor implements AiMonitor {
        final List<String> toolLines = new ArrayList<>();
        @Override public void onChatResponse(SimpleMessage m) { if (m.role() == SimpleMessage.Type.TOOL) toolLines.add(m.message()); }
        @Override public void onTool(String message) { toolLines.add(message); }
    }
    
    @BeforeEach
    void beforeEach() {
        toolService.replaceTool(new CompactSessionTool());
        cm = mockWithHandler();
        subject = new AiDevAgent(new ConfiguredChatModel(LlmConfig.newOllama("foo"), cm), toolService);
    }
    
    @Test
    void test_simple_call() {
        // GIVEN
        var aiMessage = AiMessage.aiMessage("Okay thats good");
        var requestRef  = new AtomicReference<ChatRequest>();
        
        fn.set(req -> {
            requestRef.set(req);
            return ChatResponse.builder().aiMessage(aiMessage).build();
        });
        
        // WHEN
        subject.call("Foo", null);

        // THEN
        verify(cm, times(1)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        var mem = subject.getMemory().getCopy();
        assertThat(((UserMessage)mem.get(0)).singleText()).contains("Foo");
        assertThat(mem.get(1)).isEqualTo(aiMessage);
        // AND
        assertThat(requestRef.get().messages().get(0).getClass()).isEqualTo(SystemMessage.class);
    }
    
    @Test
    void test_clear_memory() {
        // GIVEN
        for (int i = 0; i < 5; i++) {
            subject.addMessage(UserMessage.from("Foo " + i));
            subject.addMessage(AiMessage.from("Foo " + i));
        }
        var requestRef  = new AtomicReference<ChatRequest>();
        var clear = new AtomicBoolean(true);

        fn.set(req -> {
            requestRef.set(req);
            if (clear.getAndSet(false)) return ChatResponse.builder()
                    .aiMessage(CALL_ME)
                    .build();

            return ChatResponse.builder().aiMessage(AiMessage.aiMessage("Okay thats good")).build();
        });

        // WHEN
        subject.call("Foo", null);

        // THEN
        verify(cm, times(3)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        // AND — memory structure after the delegated compact (R-CC-9, ADR-0076):
        // [0] UserMessage — the one re-seed: marker block "Session compacted:" + the compressor's summary
        // [1] CALL_ME (tool request, added by the tool loop once the tool ran)
        // [2] ToolExecutionResultMessage (tool result — marker only, no summary; the summary lives
        //     exclusively in the re-seed at [0], SOLL 2026-09-10)
        // [3] AiMessage "Okay thats good" (final response, second iteration)
        var mem = subject.getMemory().getCopy();
        assertThat(((UserMessage)mem.get(0)).singleText())
                .startsWith("Session compacted:")
                .contains("Okay thats good");
        assertThat(mem.get(1)).isEqualTo(CALL_ME);
        assertThat(mem.get(2)).isInstanceOf(ToolExecutionResultMessage.class);
        // no-preserve marker: present, and NOT a duplicate of the resume text (SOLL 2026-09-11);
        // the stats line (R-CIB-6) precedes it
        assertThat(((ToolExecutionResultMessage)mem.get(2)).text()).contains("(nothing preserved)");
        // AND — the compact marker text appears EXACTLY ONCE over ALL messages (SOLL 2026-09-11):
        // only the re-seed UserMessage carries "Session compacted"; the no-preserve tool result
        // must be a non-colliding marker, never a duplicate of the resume text.
        long compactMarkerCount = mem.stream()
                .filter(m -> textOf(m).contains("Session compacted"))
                .count();
        assertThat(compactMarkerCount).isOne();
    }
    
    @Test
    void test_inLoopCompact_tokenCounterResetsBelowThreshold() {
        // GIVEN — a tiny budget (1000) so the stale pre-compact usage (9600) sits far above it
        var config = LlmConfig.newOllama("foo").toBuilder().autoCompactAfter(1000).build();
        subject = new AiDevAgent(new ConfiguredChatModel(config, cm), toolService);
        for (int i = 0; i < 5; i++) {
            subject.addMessage(UserMessage.from("Foo " + i));
            subject.addMessage(AiMessage.from("Bar " + i));
        }
        var first = new AtomicBoolean(true);
        fn.set(req -> {
            if (first.getAndSet(false)) {
                return ChatResponse.builder()
                        .aiMessage(CALL_ME)
                        .tokenUsage(new TokenUsage(9500, 100, 9600))
                        .build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.aiMessage("Okay thats good")).build();
        });

        // WHEN — the model compacts in-loop
        subject.call("Foo", null);

        // THEN — the counter reflects the NEW small memory, not the pre-compact request usage
        assertThat(subject.getMemory().getTotalTokenUsed()).isLessThan(1000);
        // AND — no compact hint was injected right after the compact
        assertThat(subject.getMemory().containsUserMessage("CONTEXT LIMIT WARNING")).isFalse();
    }

    @Test
    void test_inLoopCompact_noHintAfterCompact() {
        // GIVEN — realistic budget (80k); the model compacts in-loop and the post-compact counter stays small
        var config = LlmConfig.newOllama("foo").toBuilder().autoCompactAfter(80000).build();
        subject = new AiDevAgent(new ConfiguredChatModel(config, cm), toolService);
        for (int i = 0; i < 5; i++) {
            subject.addMessage(UserMessage.from("Foo " + i));
            subject.addMessage(AiMessage.from("Bar " + i));
        }
        var first = new AtomicBoolean(true);
        fn.set(req -> {
            if (first.getAndSet(false)) {
                return ChatResponse.builder()
                        .aiMessage(CALL_ME)
                        .tokenUsage(new TokenUsage(76000, 1000, 77000))
                        .build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.aiMessage("Okay thats good")).build();
        });

        // WHEN — turn 1 compacts in-loop; turn 2 must NOT trigger a phantom pre-turn auto-compact
        subject.call("Foo", null);
        subject.call("Bar", null);

        // THEN — no compact hint was injected
        assertThat(subject.getMemory().containsUserMessage("CONTEXT LIMIT WARNING")).isFalse();
        // AND — the compressor ran exactly once (turn 1: request, compact, final; turn 2: final)
        verify(cm, times(4)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
    }

    /**
     * Queued follow-up after a high-context turn: the removed 0.7 pre-turn gate no longer compacts
     * before the request — the in-loop hint catches the 95 % band instead.
     */
    @Test
    void queuedFollowUp_noPreTurnCompact_inLoopHintCatches() {
        // GIVEN — realistic budget (80k); turn 1 ends below the 95 % hint band (72k)
        var config = LlmConfig.newOllama("foo").toBuilder().autoCompactAfter(80000).build();
        subject = new AiDevAgent(new ConfiguredChatModel(config, cm), toolService);
        toolService.addTool(new ProbeTool());
        var monitor = new CapturingMonitor();
        var callCount = new AtomicInteger(0);
        fn.set(req -> {
            switch (callCount.incrementAndGet()) {
                case 1: // turn 1 — final, context at 72k (below the 76k band)
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.aiMessage("First task done"))
                            .tokenUsage(new TokenUsage(72000, 0, 72000))
                            .build();
                case 2: // turn 2 round 1 — probe tool call, context at 77k (inside the 95 % band)
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.builder()
                                    .toolExecutionRequests(List.of(ToolExecutionRequest.builder().name("probe").build()))
                                    .build())
                            .tokenUsage(new TokenUsage(77000, 0, 77000))
                            .build();
                default: // turn 2 round 2 — final
                    return ChatResponse.builder().aiMessage(AiMessage.aiMessage("Okay thats good")).build();
            }
        });

        // WHEN — turn 1 runs; a queued follow-up (turn 2) crosses into the hint band
        subject.call("first task", monitor);
        subject.call("follow-up", monitor);

        // THEN — the in-loop hint fired (context inside the 95 % band after the tool round)
        assertThat(subject.getMemory().containsUserMessage("CONTEXT LIMIT WARNING")).isTrue();
        // AND — no pre-turn auto-compact ran (the old 0.7 gate is gone; turn-1 text is intact)
        assertThat(monitor.toolLines).noneMatch(l -> l.contains("Auto Compact before execution"));
        assertThat(subject.getMemory().containsMessage("first task")).isTrue();
        // AND — no direct in-loop compact (77k < 80k budget — the band only hints)
        assertThat(monitor.toolLines).noneMatch(l -> l.contains(CompactFeedback.START_PREFIX));
    }

    @Test
    void test_inLoopCompact_systemMessageIsRebuilt() {
        // GIVEN — a ContextItem whose render() returns a version counter (SOLL R-ST4)
        var version = new AtomicInteger(0);
        subject.setStaticContext(List.of(() -> new TaggedResult("context", "CTX-VERSION-" + version.incrementAndGet())));
        for (int i = 0; i < 5; i++) {
            subject.addMessage(UserMessage.from("Foo " + i));
            subject.addMessage(AiMessage.from("Bar " + i));
        }
        // Capture system texts from every ChatRequest (compressor call is in there too)
        var systemTexts = new ArrayList<String>();
        var first = new AtomicBoolean(true);
        fn.set(req -> {
            req.messages().stream()
                    .filter(m -> m instanceof SystemMessage)
                    .forEach(m -> systemTexts.add(((SystemMessage) m).text()));
            if (first.getAndSet(false)) {
                return ChatResponse.builder()
                        .aiMessage(CALL_ME)
                        .tokenUsage(new TokenUsage(9500, 100, 9600))
                        .build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.aiMessage("Okay thats good")).build();
        });

        // WHEN — the model compacts in-loop
        subject.call("Foo", null);

        // THEN — the LLM request AFTER the compact carries a NEW system prompt (R-ST4)
        // Filter to only the main agent's system texts (compressor has its own prompt)
        var agentSystemTexts = systemTexts.stream()
                .filter(t -> t.contains("CTX-VERSION-"))
                .collect(Collectors.toList());
        assertThat(agentSystemTexts).hasSizeGreaterThanOrEqualTo(2);
        var pre = agentSystemTexts.get(0);
        var post = agentSystemTexts.get(agentSystemTexts.size() - 1);
        var preVersion = pre.lines().filter(l -> l.startsWith("CTX-VERSION-")).findFirst().orElseThrow();
        var postVersion = post.lines().filter(l -> l.startsWith("CTX-VERSION-")).findFirst().orElseThrow();
        assertThat(postVersion).isNotEqualTo(preVersion);
        assertThat(post).doesNotContain(preVersion);
    }

    @Test
    void test_context_injection() {
        // GIVEN — turn context is restored after compact, not injected on first call
        var requestRef  = new AtomicReference<ChatRequest>();
        fn.set(req -> {
            requestRef.set(req);
            return ChatResponse.builder().aiMessage(AiMessage.aiMessage("Okay thats good")).build();
        });

        // WHEN — set turn context via turnContextSupplier
        subject.setTurnContextSupplier(() -> List.of(
                new SimpleContextItem("We are all doomed!"),
                new ContextItem() {
                    @Override
                    public String dedupKey() { return TaggedResult.PREFIX + "context: foo/path/bar.txt"; }
                    @Override
                    public TaggedResult render() {
                        return new TaggedResult("context: foo/path/bar.txt", "some nice file text");
                    }
                },
                new ContextItem() {
                    @Override
                    public String dedupKey() { return TaggedResult.PREFIX + "context: foo/null.txt"; }
                    @Override
                    public TaggedResult render() { return null; }
                }
            )
        );
        subject.call("Foo 1", null);

        // THEN — on first call, turn context is NOT injected (only restored after compact)
        verify(cm, times(1)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        // AND — only the explicit user message is in the request
        var userTexts = String.join(
            System.lineSeparator(),
            requestRef.get().messages().stream()
                .map(ChatMessageUtil::toString)
                .toList());

        assertThat(userTexts).contains("Foo 1");
        assertThat(userTexts).doesNotContain("null");
        // AND
        assertThat(userTexts).contains("foo/path/bar.txt");
        assertThat(userTexts).contains("some nice file text");
        // AND
        assertThat(userTexts).doesNotContain("foo/null.txt");
        assertThat(userTexts).doesNotContain("null");

        // AND — turn context survives via turnContextSupplier for compact restore
        assertThat(userTexts).contains("We are all doomed!");
        
        // WHEN
        subject.call("Foo 2", null);
        subject.call("Foo 3", null);
        
        userTexts = String.join(
                System.lineSeparator(),
                requestRef.get().messages().stream()
                    .filter(m -> m instanceof UserMessage)
                    .map(ChatMessageUtil::toString)
                    .toList());
        // THEN
        assertThat(userTexts).contains("Foo 2");
        assertThat(userTexts).contains("Foo 3");
        assertThat(requestRef.get().messages().stream()
                .filter(m -> m instanceof UserMessage)
                .map(ChatMessageUtil::toString)
                .filter(m -> m.contains("some nice file text"))
                .count()).isOne();
    }
    
    @Test
    void test_command_as_standing_order() {
        // GIVEN — a command body set as turn context; compact restores it
        var requestRef = new AtomicReference<ChatRequest>();
        AtomicInteger callCount = new AtomicInteger();
        fn.set(req -> {
            requestRef.set(req);
            callCount.incrementAndGet();
            return ChatResponse.builder()
                    .aiMessage(AiMessage.aiMessage("Review complete — no issues found."))
                    .build();
        });

        // WHEN — set turn context and call
        subject.setTurnContextSupplier(() -> List.of(new SimpleContextItem("Review the code and report any issues.")));
        subject.call("Refactor this class", null);

        // THEN — on first call, turn context is NOT injected (only restored after compact)
        verify(cm, times(1)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        var userMsg = ChatMessageUtil.toString(subject.getMemory().get(0));
        assertThat(userMsg).contains("Refactor this class");
        // AND — turn context survives via turnContextSupplier for compact restore
        assertThat(ChatMessageUtil.readChatMessage(requestRef.get().messages()))
            .contains("Review the code and report any issues.");
    }

    @Test
    void test_send_no_message() {
        // GIVEN
        var aiMessage = AiMessage.aiMessage("Okay thats good");
        var userMessage = UserMessage.from("Some message");

        var requestRef  = new AtomicReference<ChatRequest>();
        subject.getMemory().add(UserMessage.from("Some message"));
        fn.set(req -> {
            requestRef.set(req);
            return ChatResponse.builder().aiMessage(aiMessage).build();
        });
        
        // WHEN
        subject.call(null, null);
        subject.call(null, null);

        // THEN
        verify(cm, times(2)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        // AND
        var mem = subject.getMemory().getCopy();
        assertThat(mem.size()).isEqualTo(3);
        assertThat(mem.get(0)).isEqualTo(userMessage);
        assertThat(mem.get(1)).isEqualTo(aiMessage);
        assertThat(mem.get(2)).isEqualTo(aiMessage);
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

    private StreamingChatModel mockWithHandler() {
        var cm = mock(StreamingChatModel.class);
        doAnswer(inv -> {
            ChatRequest req = inv.getArgument(0, ChatRequest.class);
            ChatResponse cr = fn.get().apply(req);
            var handler = inv.getArgument(1, StreamingChatResponseHandler.class);
            handler.onCompleteResponse(cr);
            return null;
        }).when(cm).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        return cm;
    }
}
