package org.sterl.llmpeon.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public record LlmConfig (
        AiProvider providerType,
        String model,
        String url,
        int tokenWindow,
        boolean thinkingEnabled,
        String apiKey,
        String skillDirectory) {
    
    public static LlmConfig newConfig(String model, String url) {
        return new LlmConfig(AiProvider.OLLAMA, model, url, 4000, false, null, null);
    }

    public ChatModel build() {
        if (AiProvider.OPEN_AI == providerType()) {
            return OpenAiChatModel.builder()
                    .timeout(Duration.ofMinutes(2))
                    .baseUrl(url())
                    .modelName(model())
                    .apiKey(apiKey())
                    .maxTokens(tokenWindow())
                    .build();
        } else if (AiProvider.GOOGLE_GEMINI == providerType()) {
            return GoogleAiGeminiChatModel.builder()
                    .apiKey(apiKey())
                    .modelName(model())
                    .maxOutputTokens(tokenWindow())
                    .build();
        } else {
            return OllamaChatModel.builder()
                    .timeout(Duration.ofMinutes(2))
                    .baseUrl(url())
                    .modelName(model())
                    .think(thinkingEnabled())
                    .build();
        }
    }
    
    public boolean skillFolderExisits() {
        return Files.isDirectory(Path.of(skillDirectory));
    }
}
