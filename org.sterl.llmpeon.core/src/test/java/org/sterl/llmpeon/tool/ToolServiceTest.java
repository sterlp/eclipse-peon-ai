package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.TerminationProbe;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.tools.ShellTool;
import org.sterl.llmpeon.tool.web.WebFetchTool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

class ToolServiceTest {

    final ToolService subject = new ToolService();

    @Test
    void test_getTool() {
        var tool = subject.getTool(WebFetchTool.class);
        assertTrue(tool.get() instanceof WebFetchTool);
    }

    @Test
    void testLoopEndsWithTextMessageOnly() {
        // GIVEN
        var cm = mock(StreamingChatModel.class);
        var answer = ChatResponse.builder().aiMessage(AiMessage.from("Hello")).build();
        doAnswer(inv -> {
            inv.getArgument(1, StreamingChatResponseHandler.class).onCompleteResponse(answer);
            return null;
        }).when(cm).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        var memory = new ThreadSafeMemory();
        // WHEN
        memory.add(UserMessage.from("Hello"));
        var response = subject.executeLoop(
                ToolLoopRequest.builder().memory(memory).chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), cm)).build());

        // THEN
        assertEquals("Hello", response.aiMessage().text());
        // AND
        verify(cm, times(1)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
    }

    @Test
    void testLoopsOn_result_think_only() {
        // GIVEN
        var requestRef  = new AtomicReference<ChatRequest>();
        var aiThinkMsg = AiMessage.builder().thinking("I think").build();
        var aiResponse = AiMessage.from("Hello User");
        var think = new AtomicBoolean(true);
        var cm = mockWithHandler(req -> {
            requestRef.set(req);
            if (think.getAndSet(false)) return ChatResponse.builder().aiMessage(aiThinkMsg).build();
            return ChatResponse.builder().aiMessage(aiResponse).build();
        });
        var memory = new ThreadSafeMemory();
        // WHEN
        memory.add(UserMessage.from("Hello"));
        subject.executeLoop(ToolLoopRequest.builder().memory(memory).chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), cm)).build());

        // THEN
        verify(cm, times(2)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        // AND
        var messages = memory.getCopy();
        assertThat(messages.get(1)).isEqualTo(AiMessage.from("I think"));
        assertThat(((UserMessage)messages.get(2)).singleText()).contains("ask a clarifying question");
        assertThat(messages.get(3)).isEqualTo(aiResponse);
        // AND last request contains all our messages
        // 1 is the Hello
        assertThat(requestRef.get().messages().get(1)).isEqualTo(AiMessage.from("I think"));
        // 2 is the ask a question
    }

    @Test
    void test_adds_user_message_and_one_system_message() {
        // GIVEN
        var requestRef  = new AtomicReference<ChatRequest>();
        var sys1        =  SystemMessage.from("sys1");
        var sys2        =  SystemMessage.from("sys2");
        var aiMessage   =  AiMessage.from("Hello from AI");
        var userMessage = UserMessage.from("Hello");
        var cm = mockWithHandler(req -> {
            requestRef.set(req);
            return ChatResponse.builder().aiMessage(aiMessage).build();
        });
        
        var memory = new ThreadSafeMemory();
        memory.add(userMessage);
        // WHEN
        subject.executeLoop(ToolLoopRequest.builder().memory(memory).chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), cm))
                .staticMessages(Arrays.asList(sys1, sys2))
                .build());

        // THEN
        verify(cm, times(1)).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        assertThat(requestRef.get().messages().get(1)).isEqualTo(userMessage);
        assertThat(((SystemMessage)requestRef.get().messages().get(0)).text()).contains("sys1", "sys2");
        assertThat(requestRef.get().messages().stream().filter(SystemMessage.class::isInstance)).hasSize(1);
        // AND
        var messages = memory.getCopy();
        assertThat(messages.get(0)).isEqualTo(userMessage);
        assertThat(messages.get(1)).isEqualTo(aiMessage);
    }
    
    /** A tool whose execution always throws — used to prove tool errors don't trigger the AI retry. */
    static class BoomTool implements SmartTool {
        @Tool("always fails")
        public String boom() { throw new RuntimeException("tool boom"); }
        @Override public void withToolRequest(ToolLoopRequest request) {}
    }

    /** A tool failing like the real case in org.sterl.llmpeon.core/docs/tool-output-disclosure.md: class, message, cause. */
    static class FailingTool implements SmartTool {
        @Tool("always fails with a cause")
        public String boom() {
            throw new IllegalStateException("closed", new IOException("Connection reset"));
        }
        @Override public void withToolRequest(ToolLoopRequest request) {}
    }

    /** A tool whose message carries a stack trace — the chat line must still stay one clean line. */
    static class StackMessageTool implements SmartTool {
        @Tool("fails with a stack in its message")
        public String boom() {
            throw new RuntimeException("""
                    build failed
                    \tat org.sterl.Fake.call(Fake.java:1)
                    \tat org.sterl.Fake.main(Fake.java:9)\
                    """);
        }
        @Override public void withToolRequest(ToolLoopRequest request) {}
    }

    /** A tool that cancels — used to prove cancellation aborts the loop immediately. */
    static class CancelTool implements SmartTool {
        @Tool("cancels immediately")
        public String cancel() { throw new CancellationException("user canceled"); }
        @Override public void withToolRequest(ToolLoopRequest request) {}
    }

    /** A tool whose denial is wrapped two levels deep — proves the executor finds it in the cause chain. */
    static class NestedDenialTool implements SmartTool {
        @Tool("denies the write")
        public String deny() {
            throw new IllegalStateException("Tool failed - check why",
                    new ExecutionException(new IllegalArgumentException(
                            "Write denied: '/ui-automate/config.toml' is outside this agent's allowed paths")));
        }
        @Override public void withToolRequest(ToolLoopRequest request) {}
    }

    /** A tool that cancels while carrying a denial cause — cancel must win over the denial (D9). */
    static class CancelWithDenialTool implements SmartTool {
        @Tool("cancels with a denial cause")
        public String cancelDenied() {
            var cancel = new CancellationException("user canceled");
            cancel.initCause(new IllegalArgumentException("Write denied"));
            throw cancel;
        }
        @Override public void withToolRequest(ToolLoopRequest request) {}
    }

    /**
     * A throwing tool goes through the tool-loop feedback path (onProblem + error result fed back to
     * the model), NOT through {@link org.sterl.llmpeon.streaming.ApiRetry} — the retry wraps only the
     * AI call. So the model is called once per loop round, never re-called to "retry" the tool.
     */
    @Test
    @Timeout(10)
    void tool_error_is_not_retried() {
        // GIVEN — round 1: model asks for the boom tool; round 2: model answers
        var boom = new BoomTool();
        subject.replaceTool(boom);
        var round = new AtomicInteger();
        var cm = mockWithHandler(req -> {
            if (round.incrementAndGet() == 1) {
                return ChatResponse.builder().aiMessage(AiMessage.builder()
                        .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                                .id("1").name("boom").arguments("{}").build()))
                        .build()).build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
        });
        var problems = new ArrayList<String>();
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onProblem(String message) { problems.add(message); }
        };
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("go"));

        // WHEN
        var response = subject.executeLoop(ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), cm))
                .monitor(monitor)
                .build());

        // THEN — loop recovered to a text answer; tool failure surfaced via the feedback path
        assertThat(response.aiMessage().text()).isEqualTo("done");
        assertThat(problems).anyMatch(p -> p.contains("boom failed"));
        // AND — NOT retried: no ApiRetry message, and exactly two model rounds (tool-call + answer)
        assertThat(problems).noneMatch(p -> p.contains("API error — attempt"));
        assertThat(round.get()).isEqualTo(2);
    }

    /**
     * R-OD-8: the human line names the tool, the local time, the exception class + message and the whole
     * cause chain. The wrapper langchain4j puts around tool exceptions only restates its cause, so it is
     * not repeated as a link of its own.
     */
    // UC-OD-11
    @Test
    @Timeout(10)
    void toolFailureReportsFullException() {
        // GIVEN — the boom tool fails with a two-link cause chain
        var problems = new ArrayList<String>();

        // WHEN
        runFailingToolLoop(new FailingTool(), problems, new ArrayList<>());

        // THEN — the doc example, exactly, with the machine's own clock time
        assertThat(failureLine(problems)).matches("^boom failed \\(\\d{2}:\\d{2}\\): IllegalStateException: closed"
                + " ← IOException: Connection reset — details logged\\.$");
    }

    /**
     * R-OD-8 bullet 3: a message that carries stack frames is still reported as ONE line without them —
     * the stack stays in the log and in the tool result, the chat does not flood.
     */
    // UC-OD-11
    @Test
    @Timeout(10)
    void toolFailureLine_hasNoStackFrames() {
        // GIVEN — a tool whose message embeds a stack trace
        var problems = new ArrayList<String>();

        // WHEN
        runFailingToolLoop(new StackMessageTool(), problems, new ArrayList<>());

        // THEN — one line, the prose of the message, no frame anywhere
        var line = failureLine(problems);
        assertThat(line).contains("build failed");
        assertThat(line).doesNotContain("\n", "\r", "\tat ", "at org.sterl");
        assertThat(line.lines()).hasSize(1);
    }

    /**
     * R-OD-8 (LLM-Sink): the tool result fed back to the model keeps carrying the full stack — the chat
     * line got richer, the model did not get poorer.
     */
    // UC-OD-11
    @Test
    @Timeout(10)
    void toolResult_stillCarriesFullStack() {
        // GIVEN
        var problems = new ArrayList<String>();
        var requests = new ArrayList<ChatRequest>();

        // WHEN — the second round's request is the one carrying the tool result
        runFailingToolLoop(new FailingTool(), problems, requests);

        // THEN — model-facing text keeps the marker AND the stack frames
        var toolResult = requests.get(requests.size() - 1).messages().stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .findFirst().orElseThrow();
        assertThat(toolResult.text()).contains("Tool failed - check why and inform user");
        assertThat(toolResult.text()).contains("\tat ");
    }

    /** The one tool-failure problem line reported to the monitor. */
    private static String failureLine(List<String> problems) {
        return problems.stream().filter(p -> p.startsWith("boom failed")).findFirst().orElseThrow();
    }

    /**
     * The real loop with a failing tool in round 1 and a plain text answer in round 2 — collecting every
     * problem line and every ChatRequest sent.
     */
    private void runFailingToolLoop(SmartTool tool, List<String> problems, List<ChatRequest> requests) {
        subject.replaceTool(tool);
        var round = new AtomicInteger();
        var cm = mockWithHandler(req -> {
            requests.add(req);
            if (round.incrementAndGet() == 1) {
                return ChatResponse.builder().aiMessage(AiMessage.builder()
                        .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                                .id("1").name("boom").arguments("{}").build()))
                        .build()).build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
        });
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("go"));

        subject.executeLoop(ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), cm))
                .monitor(new AiMonitor() {
                    @Override public void onChatResponse(SimpleMessage m) {}
                    @Override public void onProblem(String message) { problems.add(message); }
                })
                .build());
    }


    /**
     * A canceling tool aborts the entire loop — the exception propagates through SmartToolExecutor
     * (which detects it via ExceptionUtil.isCanceled) and ToolService.runAllTools (same check),
     * so the model is called only once and no error result is fed back.
     */
    @Test
    @Timeout(10)
    void tool_cancellation_aborts_loop() {
        // GIVEN — model asks for the cancel tool; a second round would never happen
        var cancel = new CancelTool();
        subject.replaceTool(cancel);
        var round = new AtomicInteger();
        var cm = mockWithHandler(req -> {
            round.incrementAndGet();
            return ChatResponse.builder().aiMessage(AiMessage.builder()
                    .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                            .id("1").name("cancel").arguments("{}").build()))
                    .build()).build();
        });
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("go"));

        // WHEN + THEN — ToolExecutionException wraps the CancellationException and propagates
        assertThatThrownBy(() -> subject.executeLoop(ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), cm))
                .build()))
                .isInstanceOf(dev.langchain4j.exception.ToolExecutionException.class)
                .hasCauseInstanceOf(CancellationException.class);
        // AND — only one model round; the loop did NOT continue after the cancel
        assertThat(round.get()).isEqualTo(1);
    }

    /**
     * R5 bullet 2 (org.sterl.llmpeon.core/docs/write-path-validator.md): an IllegalArgumentException ANYWHERE in the
     * ToolExecutionException cause chain becomes the model-facing message — verbatim, not the
     * generic "Tool failed - check why" fallback.
     */
    @Test
    @Timeout(10)
    void tool_nested_denial_returns_the_validator_message_to_the_model() {
        // GIVEN — round 1: model asks for the denial tool; round 2: model answers
        subject.replaceTool(new NestedDenialTool());
        var round = new AtomicInteger();
        var requests = new ArrayList<ChatRequest>();
        var cm = mockWithHandler(req -> {
            requests.add(req);
            if (round.incrementAndGet() == 1) {
                return ChatResponse.builder().aiMessage(AiMessage.builder()
                        .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                                .id("1").name("deny").arguments("{}").build()))
                        .build()).build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
        });
        var problems = new ArrayList<String>();
        var monitor = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) {}
            @Override public void onProblem(String message) { problems.add(message); }
        };
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("go"));

        // WHEN
        var response = subject.executeLoop(ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), cm))
                .monitor(monitor)
                .build());

        // THEN — the loop recovered; the model got the denial verbatim in the round-2 payload
        assertThat(response.aiMessage().text()).isEqualTo("done");
        var resultMessage = requests.get(1).messages().stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(resultMessage.text()).contains("Write denied");
        assertThat(resultMessage.text()).doesNotContain("Tool failed - check why");
        // AND — the problem surfaced to the monitor; the loop continued (no throw, no abort)
        assertThat(problems).anyMatch(p -> p.contains("Write denied"));
        assertThat(round.get()).isEqualTo(2);
    }

    /**
     * D9: cancellation wins over a nested denial — a CancellationException carrying an
     * IllegalArgumentException cause must still abort the loop, not feed the denial back.
     *
     * Mechanism: the named mutation ("IAE check before the cancel check") makes executeLoop
     * spin non-interruptibly — JUnit's @Timeout would mark the test failed, but the engine
     * still waits for the executor thread and the whole suite hangs. The daemon probe keeps
     * the suite terminable (red in ~10 s instead of a hang). Same pattern as the
     * ExceptionUtilTest cycle guards.
     */
    @Test
    void tool_cancel_wins_over_a_nested_illegal_argument() {
        // GIVEN — model asks for the cancel-with-denial tool; a second round would never happen
        subject.replaceTool(new CancelWithDenialTool());
        var round = new AtomicInteger();
        var cm = mockWithHandler(req -> {
            round.incrementAndGet();
            return ChatResponse.builder().aiMessage(AiMessage.builder()
                    .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                            .id("1").name("cancelDenied").arguments("{}").build()))
                    .build()).build();
        });
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("go"));

        // WHEN — bounded daemon probe (TerminationProbe), so a spinning loop cannot hang the suite
        var probe = TerminationProbe.run("cancel-probe", 10_000, () -> subject.executeLoop(ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), cm))
                .build()));

        // THEN — the loop aborts with the CancellationException, as before the denial mapping
        assertTrue(probe.terminated(), "the loop must abort on cancellation, not spin");
        assertThat(probe.thrown()).isInstanceOf(ToolExecutionException.class)
                .hasCauseInstanceOf(CancellationException.class);
        // AND — only one model round; the nested IAE did NOT turn the cancel into a tool error
        assertThat(round.get()).isEqualTo(1);
    }

    public StreamingChatModel mockWithHandler(Function<ChatRequest, ChatResponse> fn) {
        var cm = mock(StreamingChatModel.class);
        doAnswer(inv -> {
            ChatRequest req = inv.getArgument(0, ChatRequest.class);
            ChatResponse cr = fn.apply(req);
            var handler = inv.getArgument(1, StreamingChatResponseHandler.class);
            handler.onCompleteResponse(cr);
            return null;
        }).when(cm).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        return cm;
    }

    @Test
    void executeLoop_throws_on_null_response() {
        // GIVEN — streaming fails without a response (onError instead of onCompleteResponse)
        var cm = mock(StreamingChatModel.class);
        doAnswer(inv -> {
            inv.getArgument(1, StreamingChatResponseHandler.class)
                    .onError(new RuntimeException("simulated streaming failure"));
            return null;
        }).when(cm).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("Hello"));
        var req = ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), cm))
                .build();

        // WHEN + THEN
        assertThatThrownBy(() -> subject.executeLoop(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated streaming failure");
    }

    /**
     * Bug proof: the compact hint path dereferenced req.getAgent().getName() — NPE when the
     * @Nullable agent is null (think-only AI, forced hint) — fixed via null-safe agent name, inc-1 8b2431e.
     */
    @Test
    @Timeout(30)
    void bug_compactHintCrashesWhenAgentIsNull() {
        // GIVEN — a think-only AI (never a final answer), memory pre-filled to >= 10 messages,
        // no agent on the request
        var cm = new StreamMock().buildMock(r -> ChatResponse.builder()
                .aiMessage(AiMessage.builder().thinking("still thinking").build())
                .build());
        var memory = new ThreadSafeMemory();
        for (int i = 0; i < 5; i++) {
            memory.add(UserMessage.from("user " + i));
            memory.add(AiMessage.from("ai " + i));
        }
        var req = ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), cm))
                .build();

        // WHEN — the loop reaches the forced compact hint (stuck > MAX_STUCK_ITERATIONS - 2)
        // THEN — it must not crash on req.getAgent().getName() (HandleToolLoopCompactComponent)
        assertThatNoException().isThrownBy(() -> subject.executeLoop(req));
    }

    /**
     * R-ATC-8 preview source: activeNames filters the built-in executors via classFilter and
     * applies nameFilter to the names — the single composition the agent preview and the
     * runtime filter share (inc-8: resolveActiveTools dedup).
     */
    @Test
    void activeNames_filtersBuiltinsByNameAndSorts() {
        // GIVEN — default tools: webFetchAsMarkdown, searchAgent, readOperationSystemInformation,
        //         shellRunCommand, compactSession
        // WHEN
        var names = subject.activeNames(e -> true, n -> n.startsWith("s"));
        // THEN — only the s-prefixed built-ins, sorted
        assertThat(names).containsExactly("searchAgent", "shellRunCommand");
    }

    @Test
    void activeNames_classFilterAppliesToExecutors() {
        // GIVEN — classFilter drops the whole ShellTool (both executors); nameFilter keeps s-prefixed
        // WHEN
        var names = subject.activeNames(e -> !(e.getTool() instanceof ShellTool), n -> n.startsWith("s"));
        // THEN — searchAgent survives both filters; shellRunCommand is gone via classFilter
        assertThat(names).containsExactly("searchAgent");
    }

    // ADR-0078 (D1): executeLoop is THE loop stamp — one place, every line of the loop
    @Test
    @Timeout(20)
    void defaultLoopIsNotStamped() {
        // GIVEN — a monitor recording every chat line the loop emits
        var lines = new ArrayList<SimpleMessage>();
        AiMonitor capture = new AiMonitor() {
            @Override public void onChatResponse(SimpleMessage m) { lines.add(m); }
        };

        // WHEN — a bare loop (LoopOwner.NONE, the shape of a test or a voice call) answers
        runAnsweringLoop(capture, LoopOwner.NONE);

        // THEN — the line is there, and nothing invented an owner for it
        assertThat(lines).isNotEmpty();
        assertThat(lines).allSatisfy(line -> assertThat(line.origin()).isNull());

        // WHEN — the same loop with a named, agent-less owner (the shape of the search sub-loop)
        lines.clear();
        runAnsweringLoop(capture, LoopOwner.named("Search", LlmConfig::searchAgentConfig));

        // THEN — every line of that loop carries the owner's name
        assertThat(lines).isNotEmpty();
        assertThat(lines).allSatisfy(line -> assertThat(line.origin()).isEqualTo("Search"));
    }

    /** One answering round through the real loop, on the given monitor with the given owner. */
    private void runAnsweringLoop(AiMonitor monitor, LoopOwner owner) {
        var cm = mockWithHandler(req -> ChatResponse.builder().aiMessage(AiMessage.from("Hello")).build());
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("Hello"));
        subject.executeLoop(ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), cm))
                .monitor(monitor)
                .owner(owner)
                .build());
    }
}

