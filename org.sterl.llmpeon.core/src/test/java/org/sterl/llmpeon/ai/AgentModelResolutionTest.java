package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.openai.OpenAiChatRequestParameters;

class AgentModelResolutionTest {

    @Test
    void poIndependentOfPlan() {
        var config = config(Map.of(
                AgentModelConfig.PO, record(null, null, "claude-x", null, null),
                AgentModelConfig.PLAN, record(null, null, "gpt-5", null, null)));

        assertThat(config.poAgentConfig().getModel()).isEqualTo("claude-x");
        assertThat(config.planAgentConfig().getModel()).isEqualTo("gpt-5");
    }

    @Test
    void poFallsBackToBase() {
        var config = config(Map.of(AgentModelConfig.PO, AgentModelConfig.empty()));

        var po = config.poAgentConfig();

        assertThat(po.getUrl()).isEqualTo("http://base/v1");
        assertThat(po.getApiKey()).isEqualTo("base-key");
        assertThat(po.getModel()).isEqualTo("base-model");
    }

    @Test
    void poIgnoresPlanSlot() {
        var config = config(Map.of(
                AgentModelConfig.PO, AgentModelConfig.empty(),
                AgentModelConfig.PLAN, record("http://plan/v1", null, "gpt-5", null, null)));

        var po = config.poAgentConfig();

        assertThat(po.getModel()).isEqualTo("base-model");
        assertThat(po.getUrl()).isEqualTo("http://base/v1");
    }

    @Test
    void poOwnUrlAndKeyGiveOwnConnection() {
        var config = config(Map.of(AgentModelConfig.PO,
                record("http://po/v1", "po-key", "claude-x", null, null)));

        var connection = EffectiveConnection.of(config, config.poAgentConfig());

        assertThat(connection.isBase()).isFalse();
        assertThat(connection.identity().url()).isEqualTo("http://po/v1");
        assertThat(connection.identity().apiKey()).isEqualTo("po-key");
    }

    @Test
    void poExtraBodyMergesLikeOtherAgents() {
        // Characterization: PO uses the existing agent-neutral extra-body merge path.
        var config = config(Map.of(AgentModelConfig.PO,
                record(null, null, "claude-x", null, "{\"foo\":\"bar\",\"model\":\"hacked\"}")));

        var params = (OpenAiChatRequestParameters) config.poAgentConfig().newRequestParameters(List.of());

        assertThat(params.customParameters()).containsEntry("foo", "bar").doesNotContainKey("model");
        assertThat(params.modelName()).isEqualTo("claude-x");
    }

    @Test
    void poThinkResolvesIndependently() {
        var config = config(Map.of(
                AgentModelConfig.PO, record(null, null, null, "high", null),
                AgentModelConfig.PLAN, AgentModelConfig.empty()));

        assertThat(config.poAgentConfig().getThink()).isEqualTo("high");
        assertThat(config.planAgentConfig().getThink()).isNull();
    }


    // UC-DEF-1
    @Test
    void emptyPlanSlotInheritsBaseModel() {
        // GIVEN a base model and an empty plan slot
        var config = config(Map.of(AgentModelConfig.PLAN, AgentModelConfig.empty()));

        // WHEN / THEN — the plan agent inherits the base model
        assertThat(config.planAgentConfig().getModel()).isEqualTo("base-model");
    }

    // UC-DEF-1
    @Test
    void emptyCompactSlotInheritsBaseModel() {
        // GIVEN a base model and an empty compact slot
        var config = config(Map.of(AgentModelConfig.COMPACT, AgentModelConfig.empty()));

        // WHEN / THEN — the compactor inherits the base model
        assertThat(config.compactAgentConfig().getModel()).isEqualTo("base-model");
    }

    // UC-DEF-1
    @Test
    void emptySearchSlotInheritsBaseModel() {
        // GIVEN a base model and an empty search slot
        var config = config(Map.of(AgentModelConfig.SEARCH, AgentModelConfig.empty()));

        // WHEN / THEN — the search agent inherits the base model
        assertThat(config.searchAgentConfig().getModel()).isEqualTo("base-model");
    }

    // UC-DEF-1
    @Test
    void ownModelBeatsInheritance() {
        // GIVEN slots that pin their own models
        var config = config(Map.of(
                AgentModelConfig.PLAN, record(null, null, "plan-own", null, null),
                AgentModelConfig.COMPACT, record(null, null, "compact-own", null, null),
                AgentModelConfig.SEARCH, record(null, null, "search-own", null, null)));

        // WHEN / THEN — the own model wins over the base model (no regression)
        assertThat(config.planAgentConfig().getModel()).isEqualTo("plan-own");
        assertThat(config.compactAgentConfig().getModel()).isEqualTo("compact-own");
        assertThat(config.searchAgentConfig().getModel()).isEqualTo("search-own");
    }


    private LlmConfig config(Map<String, AgentModelConfig> modelConfigs) {
        return LlmConfig.builder()
                .providerType(AiProvider.OPEN_AI)
                .model("base-model")
                .url("http://base/v1")
                .apiKey("base-key")
                .modelConfigs(modelConfigs)
                .build();
    }

    private AgentModelConfig record(String url, String apiKey, String model, String think, String extraBody) {
        return new AgentModelConfig(url, apiKey, model, think, extraBody, null);
    }
}
