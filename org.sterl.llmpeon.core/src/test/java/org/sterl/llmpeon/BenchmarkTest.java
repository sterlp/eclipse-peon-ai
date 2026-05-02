package org.sterl.llmpeon;

import static org.mockito.ArgumentMatchers.contains;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

@Tag("integration")
public class BenchmarkTest {

    private final String message = """
            Zähle mir die letzten 5 Bundeskanzler der Bundesrepublik Deutschland auf und nenne zudem kurz in einer Tabelle, wie lange diese regiert haben und ihre am meisten gefeierte Leistung während ihrer Regierungszeit.
            Heute ist
            """ + Instant.now();
    
    @Test
    void benchmark_lm_studio_gemma4_opus() {
        runTest(AiProvider.LM_STUDIO, LlmConfig.newLmStudio("gemma-4-26b-a4b-it-claude-opus-distill"));
    }
    
    @Test
    void benchmark_lm_studio_gemma4() {
        runTest(AiProvider.LM_STUDIO, LlmConfig.newLmStudio("google/gemma-4-26b-a4b"));
    }
    
    @Test
    void benchmark_lm_studio_qwen36() {
        runTest(AiProvider.LM_STUDIO, LlmConfig.newLmStudio("qwen/qwen3.6-35b-a3b"));
    }
    
    @Test
    void benchmark_ollama_gemma4() {
        runTest(AiProvider.OLLAMA, LlmConfig.newOllama("gemma4:26b-a4b-it-q4_K_M"));
    }
    
    @Test
    void benchmark_ollama_qwen36() {
        runTest(AiProvider.OLLAMA, LlmConfig.newOllama("qwen3.6:35b-a3b"));
    }
    
    void runTest(AiProvider provider, LlmConfig config) {
        // GIVEN
        var model = provider.buildChatModel(config);
        model.chat(UserMessage.from("ping - return pong!"));
        System.out.println("Loaded " + provider + " " + config.getModel());

        // WHEN
        double time = System.currentTimeMillis();
        var result = model.chat(UserMessage.from(message));
        time = System.currentTimeMillis() - time;
        time = time / 1000;
        
        // THEN
        printStats(time, result);
    }

    private void printStats(double time, ChatResponse result) {
        System.out.println(round(result.tokenUsage().totalTokenCount() / time) + " tok/sec");
        System.out.println(result.tokenUsage().totalTokenCount() + " tokens");
        System.out.println(round(time) + "s");

        System.out.println(result.aiMessage());
    }
    
    static double round(Number n) {
        return Math.round(n.doubleValue() * 100.0) / 100.0;
    }
}
