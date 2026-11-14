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
        saveAgentModel(store, agentId, record.model());
        saveOrRemove(store, LlmConfigKeys.agentKey(agentId, LlmConfigKeys.AGENT_FIELD_URL), record.url());
        saveOrRemove(store, LlmConfigKeys.agentKey(agentId, LlmConfigKeys.AGENT_FIELD_API_KEY), record.apiKey());
        saveOrRemove(store, LlmConfigKeys.agentKey(agentId, LlmConfigKeys.AGENT_FIELD_THINK), record.think());
        saveOrRemove(store, LlmConfigKeys.agentKey(agentId, LlmConfigKeys.AGENT_FIELD_EXTRA_BODY), record.extraBody());
        saveOrRemove(store, LlmConfigKeys.agentKey(agentId, LlmConfigKeys.AGENT_FIELD_TEMPERATURE), record.temperature());
    }

    /**
     * Persists only the model of one agent slot — {@code llm.model} for {@link AgentModelConfig#DEV}
     * (the base model), {@code llm.agent.<id>.model} for the others. A blank value removes the key:
     * the agent then inherits the base model (dev: the provider default). Used by the chat's model
     * selector (R-ML5), which must not touch the slot's other fields.
     */
    public static void saveAgentModel(LlmConfigStore store, String agentId, String model) {
        saveOrRemove(store, modelKey(agentId), model);
    }

    /** The store key that holds the model of the given slot. */
    public static String modelKey(String agentId) {
        return AgentModelConfig.DEV.equals(agentId)
                ? LlmConfigKeys.MODEL
                : LlmConfigKeys.agentKey(agentId, LlmConfigKeys.AGENT_FIELD_MODEL);
    }

    /**
     * Persists the raw CSV tool allowlist for one agent slot
     * ({@code llm.agent.<id>.tools}). A blank value removes the key (clean break: key gone =
     * no filter = all tools).
     */
    public static void saveAgentTools(LlmConfigStore store, String agentId, String raw) {
        saveOrRemove(store, LlmConfigKeys.agentKey(agentId, LlmConfigKeys.AGENT_FIELD_TOOLS), raw);
    }

    private static void saveOrRemove(LlmConfigStore store, String key, String value) {
        if (StringUtil.hasValue(value)) {
            store.put(key, value.trim());
        } else {
            store.remove(key);
        }
    }
}
