package org.sterl.llmpeon;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;

/**
 * not a real benchmark of the server -- just checking stuff here.
 */
@Tag("integration")
public class BenchmarkPrivatTest {

    private final SystemMessage system = SystemMessage.from("""
            Response style:
            - No preamble, no summary, no postamble
            - Skip "I will...", "Here is...", "Based on...", "Done." intros and outros
            - Answer directly; be concise — 1-4 lines unless detail is explicitly required
            - Do not repeat what was already said in the conversation
            - Remove every word that does not add meaning to keep the context small
            """);
    private final String message = """
            Zähle mir die letzten 5 Bundeskanzler der Bundesrepublik Deutschland auf und nenne zudem kurz in einer Tabelle, wie lange diese regiert haben und ihre am meisten gefeierte Leistung während ihrer Regierungszeit.
            Heute ist
            """ + Instant.now();
    
    @Test
    void benchmark_opus() {
        runTest(LlmConfig.builder()
                .providerType(AiProvider.OPEN_AI_OFFICIAL)
                .model("claude-opus-4-6")
                .url("https://gateway.integration-apihub.enbw-az.cloud/genai/legacy/anthropic/v1")
                .headerParams(Map.of("Ocp-Apim-Subscription-Key", "632537c94cc04bbb90e120313bbaedf1"))
                .apiKey("632537c94cc04bbb90e120313bbaedf1")
                .debugMode(true)
                .thinkingEnabled(false)
                .build());
    }
    
    @Test
    void benchmark_gpt() {
        runTest(LlmConfig.builder()
                .providerType(AiProvider.OPEN_AI_OFFICIAL)
                .model("gpt-5.5")
                .url("https://gateway.integration-apihub.enbw-az.cloud/genai/v1")
                .headerParams(Map.of("Ocp-Apim-Subscription-Key", "9a4d3d6d06f64b18a41dff7baea57d66"))
                .apiKey("9a4d3d6d06f64b18a41dff7baea57d66")
                .debugMode(true)
                .thinkingEnabled(true)
                .build());
    }
    
    void runTest(LlmConfig config) {
        // GIVEN
        var model = config.build();
        var messages = new ArrayList<ChatMessage>();
        messages.add(system);
        
        messages.add(UserMessage.from("ping - return pong!"));
        var result = model.callBlocking(ChatRequest.builder().messages(messages).build(), null);
        messages.add(result.aiMessage());
        System.out.println("Loaded " + config.getProviderType() + " " + config.getModel());

        // WHEN check the conversation and see if the KV cache works too
        double time = System.currentTimeMillis();
        int token = 0;
        result = model.callBlocking(ChatRequest.builder().messages(UserMessage.from(message)).build(), null);
        messages.add(result.aiMessage());
        token += result.tokenUsage().totalTokenCount();

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
