package org.sterl.llmpeon;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.LlmConfig;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

@Tag("integration")
public class BenchmarkTest {

    private final String message = """
            Zähle mir die letzten 5 Bundeskanzler der Bundesrepublik Deutschland auf und nenne zudem kurz in einer Tabelle, wie lange diese regiert haben und ihre am meisten gefeierte Leistung während ihrer Regierungszeit.
            Heute ist
            """ + Instant.now();
    
    @Test
    void benchmark_lm_studio_gemma4_opus() {
        runTest(LlmConfig.newLmStudio("gemma-4-26b-a4b-it-claude-opus-distill"));
    }
    
    @Test
    void benchmark_lm_studio_gemma4() {
        runTest(LlmConfig.newLmStudio("google/gemma-4-26b-a4b"));
    }
    
    @Test
    void benchmark_lm_studio_qwen36() {
        runTest(LlmConfig.newLmStudio("qwen/qwen3.6-35b-a3b"));
    }
    
    @Test
    void benchmark_ollama_gemma4() {
        runTest(LlmConfig.newOllama("gemma4:26b-a4b-it-q4_K_M"));
    }
    
    @Test
    void benchmark_ollama_qwen36() {
        runTest(LlmConfig.newOllama("qwen3.6:35b-a3b"));
    }
    
    void runTest(LlmConfig config) {
        // GIVEN
        var model = config.build();
        model.callBlocking(ChatRequest.builder().messages(UserMessage.from("ping - return pong!")).build(), null);
        System.out.println("Loaded " + config.getProviderType() + " " + config.getModel());

        // WHEN
        double time = System.currentTimeMillis();
        var result = model.callBlocking(ChatRequest.builder().messages(UserMessage.from(message)).build(), null);
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
