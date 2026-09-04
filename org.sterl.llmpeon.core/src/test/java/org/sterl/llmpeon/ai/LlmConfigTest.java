package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class LlmConfigTest {

    @Test
    void emptySlotYieldsNullTemperature() {
        var config = LlmConfig.builder().build();

        assertThat(Map.of(
                AgentModelConfig.DEV, config.devAgentConfig(),
                AgentModelConfig.PO, config.poAgentConfig(),
                AgentModelConfig.PLAN, config.planAgentConfig(),
                AgentModelConfig.SEARCH, config.searchAgentConfig(),
                AgentModelConfig.COMPACT, config.compactAgentConfig()))
                .allSatisfy((id, agent) -> assertThat(agent.getTemperature()).as(id).isNull());
    }

    @Test
    void slotTemperatureAppliesToEveryCoreAgentConfig() {
        for (var id : AgentModelConfig.CORE_IDS) {
            var record = new AgentModelConfig(null, null, null, null, null, "0.4");
            var config = LlmConfig.builder().modelConfigs(Map.of(id, record)).build();

            var actual = switch (id) {
                case AgentModelConfig.DEV -> config.devAgentConfig();
                case AgentModelConfig.PO -> config.poAgentConfig();
                case AgentModelConfig.PLAN -> config.planAgentConfig();
                case AgentModelConfig.SEARCH -> config.searchAgentConfig();
                case AgentModelConfig.COMPACT -> config.compactAgentConfig();
                default -> throw new AssertionError(id);
            };
            assertThat(actual.getTemperature()).as(id).isEqualTo(0.4);
        }
    }


    @Test
    void planFactoryPicksUpRecord() {
        // GIVEN a plan record with its own model, think and url
        var config = LlmConfig.of(AiProvider.OPEN_AI).model("gpt-4o")
                .url("http://base:1234/v1").apiKey("base-key")
                .modelConfigs(Map.of(AgentModelConfig.PLAN,
                        new AgentModelConfig("http://plan:5678/v1", "plan-key", "opus", "high", null, null)))
                .build();

        // WHEN
        var plan = config.planAgentConfig();

        // THEN the record's model/think/url flow into the AgentConfig; provider stays base
        assertThat(plan.getModel()).isEqualTo("opus");
        assertThat(plan.getThink()).isEqualTo("high");
        assertThat(plan.getUrl()).isEqualTo("http://plan:5678/v1");
        assertThat(plan.getApiKey()).isEqualTo("plan-key");
        assertThat(plan.getProvider()).isEqualTo(AiProvider.OPEN_AI);
    }

    @Test
    void devThinkValueDirect() {
        // GIVEN a dev record with a concrete think value (no supported/on/off strings)
        var config = LlmConfig.of(AiProvider.OPEN_AI).model("gpt-4o")
                .modelConfigs(Map.of(AgentModelConfig.DEV,
                        new AgentModelConfig(null, null, null, "medium", null, null)))
                .build();

        // WHEN / THEN — think is taken verbatim (no effectiveThink)
        assertThat(config.devAgentConfig().getThink()).isEqualTo("medium");
    }

    @Test
    void devAlwaysUsesBaseModel() {
        // GIVEN a dev record that (erroneously) carries no model of its own
        var config = LlmConfig.of(AiProvider.OPEN_AI).model("base-model")
                .modelConfigs(Map.of(AgentModelConfig.DEV,
                        new AgentModelConfig(null, null, null, "true", null, null)))
                .build();

        // WHEN / THEN — the dev agent always runs the base model
        assertThat(config.devAgentConfig().getModel()).isEqualTo("base-model");
    }

    @Test
    void agentUrlFlowsIntoEffectiveConnection() {
        // GIVEN a plan record with its own url
        var base = LlmConfig.of(AiProvider.OPEN_AI).model("gpt-4o")
                .url("http://base:1234/v1").apiKey("base-key")
                .modelConfigs(Map.of(AgentModelConfig.PLAN,
                        new AgentModelConfig("http://plan:5678/v1", null, null, null, null, null)))
                .build();

        // WHEN
        var effective = EffectiveConnection.of(base, base.planAgentConfig());

        // THEN the agent url wins and the connection is not the base one
        assertThat(effective.isBase()).isFalse();
        assertThat(effective.identity().url()).isEqualTo("http://plan:5678/v1");
    }

    @Test
    void modelConfigForMissingAgentReturnsEmpty() {
        // GIVEN / WHEN
        var config = LlmConfig.of(AiProvider.OPEN_AI).model("gpt-4o").build();

        // THEN
        assertThat(config.modelConfigFor(AgentModelConfig.PLAN)).isEqualTo(AgentModelConfig.empty());
    }

    @Test
    void withModelConfigReplacesEntry() {
        // GIVEN
        var config = LlmConfig.of(AiProvider.OPEN_AI).model("gpt-4o").build();

        // WHEN
        var updated = config.withModelConfig(AgentModelConfig.PLAN,
                new AgentModelConfig(null, null, "opus", null, null, null));

        // THEN the original is untouched, the copy carries the new record
        assertThat(config.modelConfigFor(AgentModelConfig.PLAN).model()).isNull();
        assertThat(updated.modelConfigFor(AgentModelConfig.PLAN).model()).isEqualTo("opus");
    }
}
