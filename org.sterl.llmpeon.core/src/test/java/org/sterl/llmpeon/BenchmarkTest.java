package org.sterl.llmpeon;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;

/**
 * not a real benchmark of the server -- just checking stuff here.
 * https://github.com/ggml-org/llama.cpp/issues/22746#issuecomment-4629561290
 */
@Disabled
@Tag("integration")
public class BenchmarkTest {

    private final SystemMessage system = SystemMessage.from(
            PromptLoader.loadWithDefault("large-system-prompt.txt")
            );

    @Test
    void benchmark_llama() {
        runTest(LlmConfig.newConfig(AiProvider.OPEN_AI, 
                "Qwen3.6-27B-Uncensored-HauhauCS-Balanced-IQ4_XS.gguf", "http://0.0.0.0:1234"));
    }
    
    @Test
    void benchmark_lm_studio_qwen36() {
        runTest(LlmConfig.newLmStudio("qwen3.6-27b-claude-opus"));
    }
    
    @Test
    void benchmark_lm_studio_gemma() {
        runTest(LlmConfig.newLmStudio("google/gemma-4-12b"));
    }
    
    @Test
    void benchmark_lm_studio_gemma4_opus() {
        runTest(LlmConfig.newLmStudio("gemma-4-26b-a4b-it-claude-opus-distill"));
    }
    
    @Test
    void benchmark_lm_studio_gemma4() {
        runTest(LlmConfig.newLmStudio("google/gemma-4-26b-a4b"));
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
        var messages = new ArrayList<ChatMessage>();
        messages.add(system);
        messages.add(UserMessage.from(PromptLoader.load("large-prompt.txt")));

        messages.add(UserMessage.from("ping - return pong!"));
        var result = model.callBlocking(ChatRequest.builder().messages(messages).build(), null);
        messages.add(result.aiMessage());
        System.out.println("Loaded " + config.getProviderType() + " " + config.getModel());

        // WHEN check the conversation and see if the KV cache works too
        double time = System.currentTimeMillis();
        int token = 0;
        messages.add(UserMessage.from("Added tool - ignore it"));
        messages.add(UserMessage.from("""
                Zähle mir die letzten 5 Bundeskanzler der Bundesrepublik Deutschland auf und nenne zudem kurz in einer Tabelle, wie lange diese regiert haben und ihre am meisten gefeierte Leistung während ihrer Regierungszeit.
                Heute ist
                """ + Instant.now()));
        // ADDED tool
        result = model.callBlocking(ChatRequest.builder().messages(messages)
                .toolSpecifications(ToolSpecification.builder()
                        .name("test_tool")
                        .description("Just ignore me don't use me")
                        .addMetadata("arg1", "String")
                        .build())
                .build(), null);
        messages.add(result.aiMessage());
        token += result.tokenUsage().totalTokenCount();
        
        System.out.println("Sending question");
        messages.add(UserMessage.from("Removed tool - ignore it"));
        messages.add(UserMessage.from("Are you sure - check your response. Today is " + LocalDate.now()));
        result = model.callBlocking(ChatRequest.builder().messages(messages).build(), null);
        token += result.tokenUsage().totalTokenCount();
        messages.add(result.aiMessage());

        time = System.currentTimeMillis() - time;
        time = time / 1000;
        
        // THEN
        printStats(time, token);
    }

    private void printStats(double time, int token) {
        System.out.println(round(token / time) + " tok/sec");
        System.out.println(token + " tokens");
        System.out.println(round(time) + "s");
    }
    
    static double round(Number n) {
        return Math.round(n.doubleValue() * 100.0) / 100.0;
    }
}
