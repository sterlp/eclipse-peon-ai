package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    // UC-THINK-10
    @ParameterizedTest
    @ValueSource(strings = {AgentModelConfig.PO, AgentModelConfig.PLAN, AgentModelConfig.SEARCH, AgentModelConfig.COMPACT})
    void emptyAgentThinkInheritsBaseThink(String agentId) {
        // GIVEN the base (DEV record) carries a think value, the agent slot is empty
        var config = LlmConfig.of(AiProvider.OLLAMA).model("base-model")
                .modelConfigs(Map.of(
                        AgentModelConfig.DEV, new AgentModelConfig(null, null, null, "true", null, null),
                        agentId, AgentModelConfig.empty()))
                .build();

        // WHEN
        var agent = switch (agentId) {
            case AgentModelConfig.PO -> config.poAgentConfig();
            case AgentModelConfig.PLAN -> config.planAgentConfig();
            case AgentModelConfig.SEARCH -> config.searchAgentConfig();
            case AgentModelConfig.COMPACT -> config.compactAgentConfig();
            default -> throw new AssertionError(agentId);
        };

        // THEN the agent inherits the base default
        assertThat(agent.getThink()).as(agentId).isEqualTo("true");
    }

    // UC-THINK-10
    @Test
    void explicitOffWinsOverBaseDefault() {
        // GIVEN the base think is set, the agent carries an explicit off
        var config = LlmConfig.of(AiProvider.OLLAMA).model("base-model")
                .modelConfigs(Map.of(
                        AgentModelConfig.DEV, new AgentModelConfig(null, null, null, "true", null, null),
                        AgentModelConfig.SEARCH, new AgentModelConfig(null, null, null, "false", null, null)))
                .build();

        // WHEN / THEN — an explicit off wins, no fallback to the base default
        assertThat(config.searchAgentConfig().getThink()).isEqualTo("false");
    }

    // UC-THINK-10
    @Test
    void bothEmptyStaysUnset() {
        // GIVEN neither the base (DEV) nor the agent slots carry a think value
        var config = LlmConfig.of(AiProvider.OLLAMA).model("base-model").build();

        // WHEN / THEN — unset stays unset (null) for every non-dev slot
        assertThat(config.poAgentConfig().getThink()).isNull();
        assertThat(config.planAgentConfig().getThink()).isNull();
        assertThat(config.searchAgentConfig().getThink()).isNull();
        assertThat(config.compactAgentConfig().getThink()).isNull();
    }

    // UC-THINK-10
    @Test
    void devDoesNotInheritItself() {
        // GIVEN the DEV record has no think value
        var config = LlmConfig.of(AiProvider.OLLAMA).model("base-model").build();

        // WHEN / THEN — dev stays verbatim (no self-fallback)
        assertThat(config.devAgentConfig().getThink()).isNull();
    }



    // UC-THINK-5
    @Test
    void thinkSupportedIsDerivedFromDevThinkValue() {
        // GIVEN a dev record with a generic on value
        var on = LlmConfig.of(AiProvider.OLLAMA).model("base-model")
                .modelConfigs(Map.of(AgentModelConfig.DEV,
                        new AgentModelConfig(null, null, null, "true", null, null)))
                .build();
        assertThat(on.isThinkSupported()).isTrue();

        // WHEN the dev think value is unset or an explicit off token
        var unset = LlmConfig.of(AiProvider.OLLAMA).model("base-model").build();
        var off = LlmConfig.of(AiProvider.OLLAMA).model("base-model")
                .modelConfigs(Map.of(AgentModelConfig.DEV,
                        new AgentModelConfig(null, null, null, "off", null, null)))
                .build();

        // THEN there is no separate capability flag — off (incl. unset) means not supported
        assertThat(unset.isThinkSupported()).isFalse();
        assertThat(off.isThinkSupported()).isFalse();
    }

    // UC-THINK-5
    @Test
    void legacyThinkingEnabledKeyIsIgnoredOnLoad() {
        // GIVEN a store with the removed base key (old base checkbox)
        var store = new MapLlmConfigStore();
        store.put("llm.thinkingEnabled", "true");

        // WHEN loaded
        var config = LlmConfigLoader.load(store);

        // THEN the key is ignored — support derives from the (unset) dev think value
        assertThat(config.isThinkSupported()).isFalse();
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

    /** ADR-0041 R2: the headless core default state directory is configDir/state. */
    @Test
    void stateDirectory_isConfigDirState() {
        // GIVEN
        var config = LlmConfig.builder().configDir(java.nio.file.Path.of("/home/user/.peon")).build();

        // WHEN / THEN
        assertThat(config.stateDirectory()).isEqualTo(java.nio.file.Path.of("/home/user/.peon/state"));
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
