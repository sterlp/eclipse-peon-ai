package org.sterl.llmpeon.ai;

import org.sterl.llmpeon.shared.StringUtil;

/**
 * Persists a per-agent {@link AgentModelConfig} to a {@link LlmConfigStore}. A blank field is
 * removed (the agent then inherits the base value / provider default); a set field is written
 * trimmed.
 *
 * <p>The dev model is the base model — it is written to {@code llm.model} (no separate dev model
 * key). The other agents write their model to {@code llm.agent.<id>.model}. The remaining fields
 * (url/apiKey/think/extraBody/temperature) always use the per-agent key scheme.</p>
 */
public final class LlmConfigSaver {

    private LlmConfigSaver() {
    }

    public static void saveAgentModelConfig(LlmConfigStore store, String agentId, AgentModelConfig record) {
        if (AgentModelConfig.DEV.equals(agentId)) {
            saveOrRemove(store, LlmConfigKeys.MODEL, record.model());
        } else {
            saveOrRemove(store, LlmConfigKeys.agentKey(agentId, LlmConfigKeys.AGENT_FIELD_MODEL), record.model());
        }
        saveOrRemove(store, LlmConfigKeys.agentKey(agentId, LlmConfigKeys.AGENT_FIELD_URL), record.url());
        saveOrRemove(store, LlmConfigKeys.agentKey(agentId, LlmConfigKeys.AGENT_FIELD_API_KEY), record.apiKey());
        saveOrRemove(store, LlmConfigKeys.agentKey(agentId, LlmConfigKeys.AGENT_FIELD_THINK), record.think());
        saveOrRemove(store, LlmConfigKeys.agentKey(agentId, LlmConfigKeys.AGENT_FIELD_EXTRA_BODY), record.extraBody());
        saveOrRemove(store, LlmConfigKeys.agentKey(agentId, LlmConfigKeys.AGENT_FIELD_TEMPERATURE), record.temperature());
    }

    private static void saveOrRemove(LlmConfigStore store, String key, String value) {
        if (StringUtil.hasValue(value)) {
            store.put(key, value.trim());
        } else {
            store.remove(key);
        }
    }
}
