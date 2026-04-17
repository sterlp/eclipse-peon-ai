package org.sterl.llmpeon.ai;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.model.chat.ChatModel;
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
@ToString(of = {"providerType", "url", "model"})
public class LlmConfig {

    @Default
    @NonNull
    private final AiProvider providerType = AiProvider.OLLAMA;
    @Default
    private final String model = null;
    @Default
    private final String url = null;
    @Default
    private final int tokenWindow = 16000;
    @Default
    private final boolean thinkingEnabled = true;
    @Default
    private final String apiKey = null;
    @Default
    private final String skillDirectory = null;
    @Default
    private final boolean diskToolsEnabled = false;

    public static LlmConfig newConfig(String model, String url) {
        return LlmConfig.builder().model(model).url(url).build();
    }

    public static LlmConfig newConfig(AiProvider provider, String model, String url) {
        return LlmConfig.builder()
                .providerType(provider)
                .model(model)
                .url(url)
                .build();
    }

    public ChatModel build() {
        return getProviderType().buildChatModel(this);
    }

    public LlmConfig withModel(String model) {
        return this.toBuilder().model(model).build();
    }

    /**
     * Returns a new config with the given model applied.
     * If the model carries maxInputTokens, it is used as the tokenWindow.
     */
    public LlmConfig withModel(AiModel model) {
        var builder = this.toBuilder().model(model.getId());
        if (model.getMaxInputTokens() != null) builder.tokenWindow(model.getMaxInputTokens());
        return builder.build();
    }

    public LlmConfig withThinking(boolean enabled) {
        return this.toBuilder().thinkingEnabled(enabled).build();
    }

    /**
     * Selects the best model from the list:
     * - the currently configured model if present in the list, or
     * - the first model in the list if the current model is null/missing.
     * Returns this config unchanged if the list is empty.
     */
    public LlmConfig resolveModel(List<AiModel> models) {
        if (models.isEmpty()) return this;
        if (StringUtil.hasNoValue(model)) return this;

        var effective = models.stream()
                .filter(m -> model.equals(m.getId()) || model.equalsIgnoreCase(m.getName()))
                .findFirst()
                .orElse(models.get(0));
        return withModel(effective);
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
