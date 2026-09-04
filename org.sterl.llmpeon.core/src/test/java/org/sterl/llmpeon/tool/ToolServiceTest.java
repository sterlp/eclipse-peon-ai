package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.model.SimpleMessage;
import org.sterl.llmpeon.tool.tools.WebFetchTool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
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

    /** A tool that cancels — used to prove cancellation aborts the loop immediately. */
    static class CancelTool implements SmartTool {
        @Tool("cancels immediately")
        public String cancel() { throw new CancellationException("user canceled"); }
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
}
