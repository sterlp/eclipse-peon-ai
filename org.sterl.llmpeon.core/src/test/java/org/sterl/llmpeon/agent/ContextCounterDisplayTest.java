package org.sterl.llmpeon.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.agent.AiAgentStatusModel.Row;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * R-CC-6 (docs/compact-context-counter.md): the displayed context counter must DISCLOSE whether
 * it is an estimate — estimate values render as {@code ~N (estimate)}, real provider values
 * render plain. One disclosure format for every surface (PoDelegateTool, roster).
 */
class ContextCounterDisplayTest {

    // R-CC-6
    @Test
    void estimateIsDisclosed() {
        // GIVEN — a fresh memory; adding a message drives the counter with the chars×2/7 estimate
        var memory = new ThreadSafeMemory();
        memory.add(new UserMessage("some text"));

        // THEN — the counter knows it is an estimate, and the format discloses it
        assertThat(memory.isTokenEstimate()).isTrue();
        assertThat(StringUtil.estimateAware(memory.isTokenEstimate(), "34210")).isEqualTo("~34210 (estimate)");

        // WHEN — a provider response reports real input tokens
        memory.addResult(ChatResponse.builder()
                .aiMessage(AiMessage.from("done"))
                .tokenUsage(new TokenUsage(1234, 50, 1284))
                .build(), List.of());

        // THEN — the counter is exact: no tilde
        assertThat(memory.isTokenEstimate()).isFalse();
        assertThat(StringUtil.estimateAware(memory.isTokenEstimate(), String.valueOf(memory.getTotalTokenUsed())))
                .isEqualTo("1234");

        // WHEN — a re-derivation (e.g. after a compact) turns the value back into an estimate
        memory.reevaluateTokens();

        // THEN — the estimate is disclosed again
        assertThat(memory.isTokenEstimate()).isTrue();

        // GIVEN — roster rows: one estimate row, one exact row
        var estimated = AiAgentStatusModel.build(List.of(new Row("Da Mek", 15_000, false, true)));
        var exact = AiAgentStatusModel.build(List.of(new Row("Da Mek", 15_000, false, false)));

        // THEN — the label format follows the SOLL: `~N (estimate)` vs plain
        assertThat(estimated.get(0).text()).isEqualTo("Da Mek (~15k (estimate))");
        assertThat(exact.get(0).text()).isEqualTo("Da Mek (15k)");
    }
}
