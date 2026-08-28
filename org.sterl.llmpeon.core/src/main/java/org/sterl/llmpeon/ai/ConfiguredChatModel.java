package org.sterl.llmpeon.ai;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.provider.LlmProviders;
import org.sterl.llmpeon.streaming.StreamingBridge;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.Getter;

@Getter
public class ConfiguredChatModel {

    private final AtomicReference<StreamingChatModel> chatModel = new AtomicReference<>();
    private final ConcurrentHashMap<ConnectionIdentity, StreamingChatModel> agentConnections = new ConcurrentHashMap<>();
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
            chatModel.set(LlmProviders.of(config.getProviderType()).buildModel(config));
        }
        return chatModel.get();
    }

    /**
     * Resolves the {@link StreamingChatModel} for an agent's effective connection
     * (url/apiKey/extraBody per {@link EffectiveConnection}).
     * <p>
     * Agents without their own url/apiKey/extraBody inherit the base model instance
     * (no double build). Distinct effective identities get their own model, cached per
     * {@link ConnectionIdentity} — no eviction needed: the key space is the number of
     * agent configs, cheap per instance.
     */
    public StreamingChatModel modelFor(AgentConfig agent) {
        if (agent == null) {
            return getChatModel();
        }
        var ec = EffectiveConnection.of(config, agent);
        if (ec.isBase()) {
            return getChatModel();
        }
        return agentConnections.computeIfAbsent(ec.identity(),
                k -> LlmProviders.of(k.provider()).buildModel(ec.buildConfig()));
    }

    public List<AiModel> listAiModels() {
        // TODO caching?
        return LlmProviders.of(this.config.getProviderType()).listAiModels(config);
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
            agentConnections.clear(); // baked-in base model changed → agent build configs stale
            return true;
        }
    }

    public boolean withThinkSupported(boolean supported) {
        if (config.isThinkSupported() == supported) return false;
        config = config.toBuilder().thinkSupported(supported).build();
        chatModel.set(null); // rebuild (returnThinking is build-time)
        agentConnections.clear(); // build-time flag changed → agent build configs stale
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
            agentConnections.clear(); // base changed → agent build configs stale
        }
    }
    
    public void setChatModel(StreamingChatModel chatModel) {
        this.chatModel.set(chatModel);
    }
}
