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
