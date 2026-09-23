package org.sterl.llmpeon.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.shared.ChatMessageUtil;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * R-CC-1 (docs/compact-context-counter.md, ADR-0055): the token counter measures the CONTEXT
 * SIZE of the last real prompt (provider input tokens) — never the COST (input + output).
 * Without provider usage the counter falls back to the chars×2/7 estimate.
 */
class ThreadSafeMemoryInputTokenCountTest {

    // R-CC-1
    @Test
    void countsInputNotTotal() {
        // GIVEN — a fresh memory and a provider response that reports input and total separately
        var memory = new ThreadSafeMemory();

        // WHEN — the response result is added
        memory.addResult(ChatResponse.builder()
                .aiMessage(AiMessage.from("done"))
                .tokenUsage(new TokenUsage(1000, 5000, 6000))
                .build(), List.of());

        // THEN — the counter holds the context size (input), not the cost (input + output)
        assertThat(memory.getTotalTokenUsed()).isEqualTo(1000);
    }

    // R-CC-1
    @Test
    void fallsBackToEstimate() {
        // GIVEN — a fresh memory and a response that reports no usage
        var memory = new ThreadSafeMemory();

        // WHEN
        memory.addResult(ChatResponse.builder()
                .aiMessage(AiMessage.from("A".repeat(3000)))
                .build(), List.of());

        // THEN — the counter falls back to the chars×2/7 estimate of the actual memory
        assertThat(memory.getTotalTokenUsed()).isEqualTo(ChatMessageUtil.estimateTokens(memory.getCopy()));
        assertThat(memory.getTotalTokenUsed()).isGreaterThan(0);
    }
}
