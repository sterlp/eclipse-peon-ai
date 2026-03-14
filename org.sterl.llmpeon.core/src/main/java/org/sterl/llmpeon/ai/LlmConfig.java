package org.sterl.llmpeon.ai;

import java.nio.file.Files;
import java.nio.file.Path;

import dev.langchain4j.model.chat.ChatModel;

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
        return providerType().buildChatModel(this);
    }

    public LlmConfig withModel(String model) {
        return new LlmConfig(providerType, model, url, tokenWindow, thinkingEnabled, apiKey, skillDirectory);
    }

    public boolean skillFolderExisits() {
        return Files.isDirectory(Path.of(skillDirectory));
    }
}
