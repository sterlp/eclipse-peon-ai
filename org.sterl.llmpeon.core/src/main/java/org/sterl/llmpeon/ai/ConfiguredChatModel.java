package org.sterl.llmpeon.ai;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.streaming.StreamingBridge;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.Getter;

@Getter
public class ConfiguredChatModel {

    private final AtomicReference<StreamingChatModel> chatModel = new AtomicReference<>();
    private volatile LlmConfig config;
    
    public ConfiguredChatModel(LlmConfig config) {
        updateConfig(config);
    }
    
    public ConfiguredChatModel(LlmConfig config, StreamingChatModel model) {
        updateConfig(config);
        this.chatModel.set(model);
    }
    
    public ChatResponse callBlocking(ChatMessage req) {
        return callBlocking(ChatRequest.builder().messages(req).build(), null);
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

    public List<AiModel> listAiModels() {
        // TODO caching?
        return this.config.getProviderType().listAiModels(config);
    }
    
    /**
     * @return <code>true</code> if changed, otherwise <code>false</code>
     */
    public boolean withModel(String aiModelId) {
        if (StringUtil.hasNoValue(aiModelId)) {
            return false;
        } else if (aiModelId.equals(config.getModel())) {
            return false;
        } else {
            config = config.toBuilder().model(aiModelId).build();
            return true;
        }
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
    @Deprecated
    public boolean resolveModel(List<AiModel> models) {
        if (models.isEmpty()) return false;
        var model = config.getModel();
        if (StringUtil.hasNoValue(model)) {
            return withModel(models.getFirst().getId());
        } else {
            var effective = models.stream()
                    .filter(m -> model.equals(m.getId()) || model.equalsIgnoreCase(m.getName()))
                    .findFirst()
                    .orElse(models.get(0));
            return withModel(effective.getId());
        }
    }
    
    public void updateConfig(LlmConfig newConfig) {
        if (newConfig == null) throw new NullPointerException("LlmConfig cannot be null!");
        if (this.config == null || !this.config.equals(newConfig)) {
            this.config = newConfig;
            chatModel.set(null); // rebuild
        }
    }
}
