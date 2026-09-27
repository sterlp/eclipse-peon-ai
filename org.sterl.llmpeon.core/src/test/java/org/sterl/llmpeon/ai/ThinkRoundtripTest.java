package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.ollama.OllamaChatRequestParameters;

/**
 * Persistence round-trip guard for the per-agent think value (Issue #149): an explicit off must
 * survive Saver → Store → Loader → {@link AgentConfig} and reach the Ollama request as
 * {@code think:false}; blank stays unset.
 */
class ThinkRoundtripTest {

    // UC-THINK-2
    @Test
    void explicitOffSurvivesSaverStoreLoaderAndReachesOllamaRequest() {
        // GIVEN a plan record with an explicit off think value
        var store = new MapLlmConfigStore();
        LlmConfigSaver.saveAgentModelConfig(store, AgentModelConfig.PLAN,
                new AgentModelConfig(null, null, "gemma4:12b", "false", null, null));

        // WHEN loaded and an Ollama request is built
        var cfg = LlmConfigLoader.load(store);
        var params = (OllamaChatRequestParameters) cfg.planAgentConfig().newRequestParameters(List.of());

        // THEN the off value never collapsed to null — Ollama receives think:false
        assertThat(cfg.modelConfigFor(AgentModelConfig.PLAN).think()).isEqualTo("false");
        assertThat(cfg.planAgentConfig().getThink()).isEqualTo("false");
        assertThat(params.think()).isFalse();
    }

    // UC-THINK-2
    @Test
    void emptyThinkIsUnsetAndRemovedBySaver() {
        // GIVEN a store with an existing think value
        var store = new MapLlmConfigStore();
        var key = LlmConfigKeys.agentKey(AgentModelConfig.PLAN, LlmConfigKeys.AGENT_FIELD_THINK);
        store.put(key, "high");

        // WHEN saved with a blank think value
        LlmConfigSaver.saveAgentModelConfig(store, AgentModelConfig.PLAN,
                new AgentModelConfig(null, null, null, "", null, null));

        // THEN unset stays unset: the key is removed and the agent config carries no think
        assertThat(store.asMap()).doesNotContainKey(key);
        assertThat(LlmConfigLoader.load(store).planAgentConfig().getThink()).isNull();
    }
}
