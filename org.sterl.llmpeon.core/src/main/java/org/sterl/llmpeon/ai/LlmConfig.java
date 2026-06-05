package org.sterl.llmpeon.ai;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sterl.llmpeon.ai.model.AiModel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString(of = {"providerType", "url", "model", "thinkingEnabled", "sendThinkingEnabled", "planTemperature", "devTemperature"})
public class LlmConfig {

    @Default
    @NonNull
    private final AiProvider providerType = AiProvider.OLLAMA;
    @Default
    private final String model = null;
    @Default
    private final String url = null;
    @Default
    private final int autoCompactAfter = 80000;
    @Default
    private final double planTemperature = 0.8;
    @Default
    private final double devTemperature = 0.3;
    @Default
    private final boolean thinkingEnabled = true;
    @Default
    private final boolean sendThinkingEnabled = true;
    @Default
    private final String apiKey = null;
    @Default
    private final String skillDirectory = null;
    @Default
    private final String commandDirectory = null;
    @Default
    private final boolean diskToolsEnabled = false;
    @Default
    private final boolean shellCommandConfirmationRequired = false;
    @Default
    private final boolean debugMode = false;
    @Default
    private final Map<String, String> queryParams = new LinkedHashMap<>();
    @Default
    private final Map<String, String> headerParams = new LinkedHashMap<>();
    
    /**
     * Some LLMs needs this some not
     * e.g. GOOGLE_GEMINI
     */
    public boolean shouldWeSendThinkingBackToLLM() {
        return thinkingEnabled && sendThinkingEnabled;
    }

    public static LlmConfig newConfig(String model, String url) {
        return LlmConfig.builder().model(model).url(url).build();
    }
    
    public static LlmConfig newOllama(String model) {
        return LlmConfig.builder().providerType(AiProvider.OLLAMA)
                .model(model).url("http://localhost:11434").build();
    }
    
    public static LlmConfig newLmStudio(String model) {
        return LlmConfig.builder().providerType(AiProvider.LM_STUDIO)
                .model(model).url("http://localhost:1234/v1").build();
    }
    
    public static LlmConfig newOpenAi(String model) {
        return newOpenAi(model, "http://localhost:1234/v1");
    }
    
    public static LlmConfig newOpenAi(String model, String url) {
        return LlmConfig.builder().providerType(AiProvider.OPEN_AI)
                .model(model).url(url).build();
    }

    public static LlmConfig newConfig(AiProvider provider, String model, String url) {
        return LlmConfig.builder()
                .providerType(provider)
                .model(model)
                .url(url)
                .build();
    }

    public ConfiguredModel build() {
        return new ConfiguredModel(this);
    }

    public LlmConfig withModel(String model) {
        return this.toBuilder().model(model).build();
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

    public static LlmConfigBuilder of(AiProvider provider) {
        return LlmConfig.builder().providerType(provider);
    }

    public List<AiModel> listAiModels() {
        return getProviderType().listAiModels(this);
    }
    
    public List<String> listModels() {
        return getProviderType().listModels(this);
    }
}
