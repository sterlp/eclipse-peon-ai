package org.sterl.llmpeon.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class LlmConfig {
    
    private AiProvider providerType = AiProvider.OLLAMA;
    private String model;
    private String url;
    private int tokenWindow = 4000;
    boolean thinkingEnabled = false;
    private String apiKey;
    private String skillDirectory;
    
    public LlmConfig(String model, String url) {
        super();
        this.model = model;
        this.url = url;
    }

    public LlmConfig(AiProvider providerType, String model, String url, int tokenWindow, boolean thinkingEnabled,
            String apiKey, String skillDirectory) {
        super();
        this.providerType = providerType;
        this.model = model;
        this.url = url;
        this.tokenWindow = tokenWindow;
        this.thinkingEnabled = thinkingEnabled;
        this.apiKey = apiKey;
        this.skillDirectory = skillDirectory;
        if (tokenWindow < 1000) tokenWindow = 1000;
    }

    public ChatModel build() {
        if (AiProvider.OPEN_AI == getProviderType()) {
            return OpenAiChatModel.builder()
                    .timeout(Duration.ofMinutes(2))
                    .baseUrl(getUrl())
                    .modelName(getModel())
                    .apiKey(getApiKey())
                    .maxTokens(getTokenWindow())
                    .build();
        } else {
            return OllamaChatModel.builder()
                    .timeout(Duration.ofMinutes(2))
                    .baseUrl(getUrl())
                    .modelName(getModel())
                    .think(isThinkingEnabled())
                    .build();
        }
    }
    
    public boolean skillFolderExisits() {
        return Files.isDirectory(Path.of(skillDirectory));
    }

    public AiProvider getProviderType() {
        return providerType;
    }

    public void setProviderType(AiProvider providerType) {
        this.providerType = providerType;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getTokenWindow() {
        return tokenWindow;
    }

    public void setTokenWindow(int tokenWindow) {
        this.tokenWindow = tokenWindow;
    }

    public boolean isThinkingEnabled() {
        return thinkingEnabled;
    }

    public void setThinkingEnabled(boolean thinkingEnabled) {
        this.thinkingEnabled = thinkingEnabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getSkillDirectory() {
        return skillDirectory;
    }

    public void setSkillDirectory(String skillDirectory) {
        this.skillDirectory = skillDirectory;
    }
}
