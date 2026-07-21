package org.sterl.llmpeon.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.TokenStats;
import org.sterl.llmpeon.tool.model.SimpleMessage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;

/**
 * The session token totals are fed from the single {@code StreamingBridge} choke point via
 * {@link AiMonitor#onTokenUsage}. See docs/token-usage.md R1/R2.
 */
class TokenUsageAccumulationTest {

    final ToolService subject = new ToolService();

    /** Monitor that mirrors {@code AIChatView}: accumulate every real usage into a TokenStats. */
    static final class CapturingMonitor implements AiMonitor {
        final TokenStats stats = new TokenStats();
        @Override public void onChatResponse(SimpleMessage message) {}
        @Override public void onTokenUsage(TokenUsage usage) { stats.add(usage); }
    }

    @Test
    void accumulates_across_tool_loop_iterations() {
        // GIVEN two LLM rounds (think, then answer), each carrying real usage
        var think = new AtomicBoolean(true);
        var cm = mockWithHandler(req -> {
            if (think.getAndSet(false)) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.builder().thinking("hmm").build())
                        .tokenUsage(new TokenUsage(100, 20, 120)).build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("done"))
                    .tokenUsage(new TokenUsage(150, 30, 180)).build();
        });
        var monitor = new CapturingMonitor();
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("Hello"));

        // WHEN
        subject.executeLoop(ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), cm))
                .monitor(monitor)
                .build());

        // THEN sent/received sum across both rounds
        assertThat(monitor.stats.getSent()).isEqualTo(250);
        assertThat(monitor.stats.getReceived()).isEqualTo(50);
    }

    @Test
    void ignores_responses_without_usage() {
        // GIVEN a single response with no token usage
        var cm = mockWithHandler(req -> ChatResponse.builder().aiMessage(AiMessage.from("hi")).build());
        var monitor = new CapturingMonitor();
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("Hello"));

        // WHEN
        subject.executeLoop(ToolLoopRequest.builder()
                .memory(memory)
                .chatModel(new ConfiguredChatModel(LlmConfig.newOpenAi("foo"), cm))
                .monitor(monitor)
                .build());

        // THEN totals stay empty — no estimate
        assertThat(monitor.stats.isEmpty()).isTrue();
    }

    private StreamingChatModel mockWithHandler(Function<ChatRequest, ChatResponse> fn) {
        var cm = mock(StreamingChatModel.class);
        doAnswer(inv -> {
            ChatRequest req = inv.getArgument(0, ChatRequest.class);
            ChatResponse cr = fn.apply(req);
            inv.getArgument(1, StreamingChatResponseHandler.class).onCompleteResponse(cr);
            return null;
        }).when(cm).chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));
        return cm;
    }
}
