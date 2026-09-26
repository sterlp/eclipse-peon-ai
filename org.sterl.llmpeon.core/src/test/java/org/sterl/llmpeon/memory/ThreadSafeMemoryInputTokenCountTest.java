package org.sterl.llmpeon.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.shared.ChatMessageUtil;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
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

    // R-CC-10
    @Test
    void tokenDiagnosisCarriesAllThreeFieldsWithFlag() {
        // GIVEN — a provider response reporting input, then one more message (the counter drifts to an estimate)
        var memory = new ThreadSafeMemory();

        // WHEN — the reported result is added, then a further message marks the counter an estimate
        memory.addResult(ChatResponse.builder()
                .aiMessage(AiMessage.from("done"))
                .tokenUsage(new TokenUsage(80211, 0, 80211))
                .build(), List.of());
        memory.add(UserMessage.from("one more message"));

        // THEN — the diagnosis names all three sizes; memory now carries the estimate flag, model is exact
        var line = memory.tokenDiagnosis();
        assertThat(line).contains("memory=")
                .contains("(estimate=true)")
                .contains("model=80211")
                .contains(" estimate=");
    }

    // R-CC-10
    @Test
    void tokenDiagnosisModelIsNaWithoutProviderUsage() {
        // GIVEN — only an estimate-driven add, the provider never reported usage
        var memory = new ThreadSafeMemory();
        memory.add(UserMessage.from("hello"));

        // WHEN/THEN — model is n/a (never reported)
        assertThat(memory.tokenDiagnosis()).contains("model=n/a");
    }

    // R-CC-10
    @Test
    void tokenDiagnosisShowsMemoryFarAboveModel() {
        // GIVEN — a reported input of 80211, then large messages push the memory counter far above it
        var memory = new ThreadSafeMemory();
        memory.addResult(ChatResponse.builder()
                .aiMessage(AiMessage.from("done"))
                .tokenUsage(new TokenUsage(80211, 0, 80211))
                .build(), List.of());
        for (int i = 0; i < 3; i++) memory.add(AiMessage.from("X".repeat(50000)));

        // WHEN
        var line = memory.tokenDiagnosis();

        // THEN — the parsed memory value is above the (unchanged) model value, in the SAME line
        var matcher = Pattern.compile("memory=(\\d+)").matcher(line);
        assertThat(matcher.find()).isTrue();
        assertThat(Integer.parseInt(matcher.group(1))).isGreaterThan(80211);
        assertThat(line).contains("model=80211");
    }

    // R-CC-10
    @Test
    void tokenDiagnosisResetAfterClear() {
        // GIVEN — a reported usage, then the memory is cleared
        var memory = new ThreadSafeMemory();
        memory.addResult(ChatResponse.builder()
                .aiMessage(AiMessage.from("done"))
                .tokenUsage(new TokenUsage(80211, 0, 80211))
                .build(), List.of());
        memory.clear();

        // WHEN/THEN — memory is 0 (exact), model reset to n/a, estimate of the empty context is 0
        assertThat(memory.tokenDiagnosis()).isEqualTo(" | memory=0(estimate=false) model=n/a estimate=0");
    }
}
