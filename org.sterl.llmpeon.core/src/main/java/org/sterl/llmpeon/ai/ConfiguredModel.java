package org.sterl.llmpeon.ai;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.streaming.StreamingBridge;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.Getter;

@Getter
public class ConfiguredModel {

    private AtomicReference<StreamingChatModel> chatModel = new AtomicReference<>();
    private volatile LlmConfig config;
    
    public ConfiguredModel(LlmConfig config) {
        updateConfig(config);
    }
    
    public ConfiguredModel(LlmConfig config, StreamingChatModel model) {
        updateConfig(config);
        this.chatModel.set(model);
    }

    public ChatResponse callBlocking(ChatRequest req, AiMonitor monitor) {
        return new StreamingBridge().call(getChatModel(), req, monitor);
    }
    
    public StreamingChatModel getChatModel() {
        if (chatModel.get() == null) {
            chatModel.set(config.getProviderType().buildModel(config));
        }
        return chatModel.get();
    }
    
    public String getModel() {
        return config.getModel();
    }

    public List<AiModel> listAiModels() {
        // TODO caching?
        return this.config.getProviderType().listAiModels(config);
    }

    /**
     * Returns a new config with the given model applied.
     * 
     * @return <code>true</code> if changed, otherwise <code>false</code>
     */
    public boolean withModel(AiModel aiModel) {
        if (aiModel.getId().equals(config.getModel())) return false;
        
        var builder = config.toBuilder().model(aiModel.getId());
        if (aiModel.getMaxInputTokens()  != null) {
            int max = (int)(aiModel.getMaxInputTokens() * 0.9);
            if (max < config.getAutoCompactAfter()) {
                builder.autoCompactAfter(max);
            }
        }

        config = builder.build();
        chatModel.set(null); // rebuild
        return true;
    }

    public boolean withThinking(boolean enabled) {
        if (config.isThinkingEnabled() == enabled) return false;
        config = config.toBuilder().thinkingEnabled(enabled).build();
        chatModel.set(null); // rebuild
        return true;
    }

    /**
     * Selects the best model from the list:
     * - the currently configured model if present in the list, or
     * - the first model in the list if the current model is null/missing.
     * 
     * @return <code>true</code> if config changed otherwise <code>false</code>
     */
    public boolean resolveModel(List<AiModel> models) {
        if (models.isEmpty()) return false;
        var model = config.getModel();
        if (StringUtil.hasNoValue(model)) {
            return withModel(models.getFirst());
        } else {
            var effective = models.stream()
                    .filter(m -> model.equals(m.getId()) || model.equalsIgnoreCase(m.getName()))
                    .findFirst()
                    .orElse(models.get(0));
            return withModel(effective);
        }
    }
    
    public int getAutoCompactAfter() {
        return config.getAutoCompactAfter();
    }

    public void updateConfig(LlmConfig newConfig) {
        if (newConfig == null) throw new NullPointerException("LlmConfig cannot be null!");
        if (this.config == null || !this.config.equals(newConfig)) {
            this.config = newConfig;
            chatModel.set(null); // rebuild
        }
    }
}
