package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LlmConfigSaverTest {

    @Test
    void savesTemperatureUnderAgentKey() {
        var store = new MapLlmConfigStore();
        var key = LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_TEMPERATURE);

        LlmConfigSaver.saveAgentModelConfig(store, AgentModelConfig.PLAN,
                new AgentModelConfig(null, null, null, null, null, " 0.4 "));
        assertThat(store.asMap()).containsEntry(key, "0.4");

        LlmConfigSaver.saveAgentModelConfig(store, AgentModelConfig.PLAN,
                new AgentModelConfig(null, null, null, null, null, " "));
        assertThat(store.asMap()).doesNotContainKey(key);
    }


    @Test
    void saverWritesSetFieldsTrimmed() {
        // GIVEN a plan record with all fields set (with surrounding whitespace)
        var store = new MapLlmConfigStore();
        var record = new AgentModelConfig(" http://plan:5678/v1 ", " plan-key ", " opus ", " high ", "{\"a\":1}", null);

        // WHEN
        LlmConfigSaver.saveAgentModelConfig(store, AgentModelConfig.PLAN, record);

        // THEN every field is written trimmed under the per-agent key scheme
        assertThat(store.asMap()).containsEntry(LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_MODEL), "opus");
        assertThat(store.asMap()).containsEntry(LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_URL), "http://plan:5678/v1");
        assertThat(store.asMap()).containsEntry(LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_API_KEY), "plan-key");
        assertThat(store.asMap()).containsEntry(LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_THINK), "high");
        assertThat(store.asMap()).containsEntry(LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_EXTRA_BODY), "{\"a\":1}");
    }

    @Test
    void saverRemovesBlankKeys() {
        // GIVEN a store that already has values, and a record with blank url/key
        var store = new MapLlmConfigStore();
        store.put(LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_URL), "http://old/v1");
        store.put(LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_API_KEY), "old-key");
        var record = new AgentModelConfig("   ", null, "opus", "high", null, null);

        // WHEN
        LlmConfigSaver.saveAgentModelConfig(store, AgentModelConfig.PLAN, record);

        // THEN the blank url/key are removed; the set fields remain
        assertThat(store.asMap()).doesNotContainKey(LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_URL));
        assertThat(store.asMap()).doesNotContainKey(LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_API_KEY));
        assertThat(store.asMap()).containsEntry(LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_MODEL), "opus");
        assertThat(store.asMap()).containsEntry(LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_THINK), "high");
    }

    @Test
    void saverDevModelUsesBaseKey() {
        // GIVEN a dev record with a model
        var store = new MapLlmConfigStore();
        var record = new AgentModelConfig(null, null, "gpt-4o", "true", null, null);

        // WHEN
        LlmConfigSaver.saveAgentModelConfig(store, AgentModelConfig.DEV, record);

        // THEN the model goes to the base key llm.model (not llm.agent.dev.model)
        assertThat(store.asMap()).containsEntry(LlmConfigKeys.MODEL, "gpt-4o");
        assertThat(store.asMap()).doesNotContainKey(LlmConfigKeys.agentKey(AgentModelConfig.DEV, LlmConfigKeys.AGENT_FIELD_MODEL));
        // the dev think still uses the per-agent key
        assertThat(store.asMap()).containsEntry(LlmConfigKeys.agentKey(AgentModelConfig.DEV, LlmConfigKeys.AGENT_FIELD_THINK), "true");
    }

    @Test
    void saverNonDevModelUsesAgentKey() {
        // GIVEN a compact record with a model
        var store = new MapLlmConfigStore();
        var record = new AgentModelConfig(null, null, "compact-model", null, null, null);

        // WHEN
        LlmConfigSaver.saveAgentModelConfig(store, AgentModelConfig.COMPACT, record);

        // THEN the model goes to llm.agent.compact.model (base key untouched)
        assertThat(store.asMap()).containsEntry(LlmConfigKeys.agentKey(AgentModelConfig.COMPACT, LlmConfigKeys.AGENT_FIELD_MODEL), "compact-model");
        assertThat(store.asMap()).doesNotContainKey(LlmConfigKeys.MODEL);
    }

    @Test
    void savesPoUnderPoAgentKeys() {
        // Characterization: the generic saver must preserve the PO key schema.
        var store = new MapLlmConfigStore();
        var record = new AgentModelConfig("http://po/v1", "po-key", "claude-x", "high", "{\"foo\":1}", null);

        LlmConfigSaver.saveAgentModelConfig(store, AgentModelConfig.PO, record);

        assertThat(store.asMap())
                .containsEntry(LlmConfigKeys.agentKey(AgentModelConfig.PO, LlmConfigKeys.AGENT_FIELD_MODEL), "claude-x")
                .containsEntry(LlmConfigKeys.agentKey(AgentModelConfig.PO, LlmConfigKeys.AGENT_FIELD_URL), "http://po/v1")
                .containsEntry(LlmConfigKeys.agentKey(AgentModelConfig.PO, LlmConfigKeys.AGENT_FIELD_API_KEY), "po-key")
                .containsEntry(LlmConfigKeys.agentKey(AgentModelConfig.PO, LlmConfigKeys.AGENT_FIELD_THINK), "high")
                .containsEntry(LlmConfigKeys.agentKey(AgentModelConfig.PO, LlmConfigKeys.AGENT_FIELD_EXTRA_BODY), "{\"foo\":1}")
                .doesNotContainKey(LlmConfigKeys.MODEL);
    }

    @Test
    void roundtripStable() {
        // GIVEN a fully-set plan record
        var record = new AgentModelConfig("http://plan:5678/v1", "plan-key", "opus", "high", "{\"a\":1}", null);

        // WHEN saved and reloaded
        var store = new MapLlmConfigStore();
        LlmConfigSaver.saveAgentModelConfig(store, AgentModelConfig.PLAN, record);
        var reloaded = LlmConfigLoader.load(store).modelConfigFor(AgentModelConfig.PLAN);

        // THEN the reloaded record equals the original
        assertThat(reloaded).isEqualTo(record);
    }

    @Test
    void roundtripDevStable() {
        // GIVEN a fully-set dev record (model lands on the base key)
        var record = new AgentModelConfig("http://dev:1234/v1", "dev-key", "gpt-4o", "medium", null, null);

        // WHEN saved and reloaded
        var store = new MapLlmConfigStore();
        LlmConfigSaver.saveAgentModelConfig(store, AgentModelConfig.DEV, record);
        var reloaded = LlmConfigLoader.load(store).modelConfigFor(AgentModelConfig.DEV);

        // THEN the reloaded dev record equals the original (model via base key)
        assertThat(reloaded).isEqualTo(record);
        assertThat(LlmConfigLoader.load(store).getModel()).isEqualTo("gpt-4o");
    }
}
