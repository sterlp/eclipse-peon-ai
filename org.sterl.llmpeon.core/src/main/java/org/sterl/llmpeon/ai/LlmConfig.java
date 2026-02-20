package org.sterl.llmpeon.ai;

import java.time.Duration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public record LlmConfig(
    AiProvider providerType,
    String model,
    String url,
    int tokenWindow,
    boolean thinkingEnabled,
    String apiKey,
    String skillDirectory
) {
    public ChatModel build() {
        if (AiProvider.OPEN_AI == providerType()) {
            return OpenAiChatModel.builder()
                    .timeout(Duration.ofMinutes(2))
                    .baseUrl(url())
                    .modelName(model())
                    .apiKey(apiKey())
                    .maxTokens(tokenWindow())
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
}
