package org.sterl.llmpeon.ai;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
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
        return newConfig(AiProvider.OLLAMA, model, url);
    }
    
    public static LlmConfig newConfig(AiProvider provider, String model, String url) {
        return new LlmConfig(provider, model, url, 4000, false, null, null);
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

    public boolean isReachable(int timeoutMs) {
        if (url == null || url.isBlank()) return false;
        try {
            var uri = URI.create(url);
            int port = uri.getPort() > 0 ? uri.getPort()
                     : "https".equals(uri.getScheme()) ? 443 : 80;
            try (var socket = new Socket()) {
                socket.connect(new InetSocketAddress(uri.getHost(), port), timeoutMs);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
