package org.sterl.llmpeon.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GeminiThinkingConfig.GeminiThinkingLevel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
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
                    .strictJsonSchema(true)
                    .modelName(model())
                    .apiKey(apiKey())
                    .maxTokens(tokenWindow())
                    .build();
        } else if (AiProvider.MISTRAL == providerType()) {
            return MistralAiChatModel.builder()
                    .timeout(Duration.ofMinutes(2))
                    .modelName(model())
                    .apiKey(apiKey())
                    .build();
        } else if (AiProvider.GOOGLE_GEMINI == providerType()) {
            var result = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey())
                .modelName(model());

            // returnThinking + sendThinking are always required: preview models return a
            // thought_signature even when thinking is "disabled". Without sendThinking(true),
            // the thought_signature is not re-sent with tool results → INVALID_ARGUMENT error.
            result.returnThinking(Boolean.TRUE).sendThinking(Boolean.TRUE);

            if (thinkingEnabled) {
                result.thinkingConfig(GeminiThinkingConfig.builder()
                    .thinkingLevel(GeminiThinkingLevel.MEDIUM)
                    .build());
            } else {
                result.thinkingConfig(GeminiThinkingConfig.builder()
                    .thinkingBudget(0)
                    .build());
            }

            return result.build();
        } else if (AiProvider.GITHUB_COPILOT == providerType()) {
            // api.githubcopilot.com accepts the GitHub OAuth token directly as Bearer.
            // The copilot_internal/v2/token exchange is VS Code's internal mechanism
            // and produces a semicolon-delimited token that the chat API does not accept.
            return OpenAiChatModel.builder()
                    .timeout(Duration.ofMinutes(2))
                    .baseUrl("https://api.githubcopilot.com")
                    .apiKey(apiKey() != null && !apiKey().isBlank() ? apiKey() : "not-configured")
                    .modelName(model())
                    .maxTokens(tokenWindow())
                    .customHeaders(java.util.Map.of("Copilot-Integration-Id", "eclipse-peon-ai"))
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
