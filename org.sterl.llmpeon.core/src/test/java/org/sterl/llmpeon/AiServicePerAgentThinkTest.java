package org.sterl.llmpeon;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.tool.ToolService;

import com.openai.models.ReasoningEffort;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatRequestParameters;

/**
 * End-to-end: the per-agent {@code think} value must reach the real {@link org.sterl.llmpeon.ai.AgentConfig}
 * -&gt; {@link AiProvider#newRequestParameters} path and land on the {@code ChatRequest}. Reproduces the
 * mixed-gateway bug: a non-reasoning dev model must send no {@code reasoning.effort}, while the plan
 * agent may request {@code high}.
 */
public class AiServicePerAgentThinkTest {

    private StreamMock streamMock;
    private ToolService toolService;

    @BeforeEach
    void beforeEach() {
        toolService = new ToolService();
        streamMock = new StreamMock();
    }

    private ConfiguredChatModel model(LlmConfig config) {
        var cm = streamMock.buildMock(r -> ChatResponse.builder().aiMessage(AiMessage.aiMessage("done")).build());
        return new ConfiguredChatModel(config, cm);
    }

    @Test
    void devAgentSendsNoReasoningWhenThinkUnsupported() {
        var config = LlmConfig.builder()
                .providerType(AiProvider.OPEN_AI_OFFICIAL)
                .model("kimi-k2")
                .timeout(Duration.ofSeconds(5))
                .modelConfigs(Map.of(AgentModelConfig.PLAN,
                        new AgentModelConfig(null, null, "gpt-5.5", "high", null, null)))
                .build();

        new AiDevAgent(model(config), toolService).call("test", null);

        var params = (OpenAiOfficialResponsesChatRequestParameters) streamMock.getLastRequest().parameters();
        assertThat(params.modelName()).isEqualTo("kimi-k2");
        assertThat(params.reasoningEffort()).as("dev must not send reasoning.effort").isNull();
    }

    @Test
    void planAgentSendsHighReasoning() {
        var config = LlmConfig.builder()
                .providerType(AiProvider.OPEN_AI_OFFICIAL)
                .model("kimi-k2")
                .modelConfigs(Map.of(AgentModelConfig.PLAN,
                        new AgentModelConfig(null, null, "gpt-5.5", "high", null, null)))
                .build();

        new AiPlanAgent(model(config), toolService).call("test", null);

        var params = (OpenAiOfficialResponsesChatRequestParameters) streamMock.getLastRequest().parameters();
        assertThat(params.modelName()).isEqualTo("gpt-5.5");
        assertThat(params.reasoningEffort()).isEqualTo(ReasoningEffort.of("high"));
    }

    @Test
    void devManualThinkValueApplies() {
        var config = LlmConfig.builder()
                .providerType(AiProvider.OPEN_AI_OFFICIAL)
                .timeout(Duration.ofSeconds(5))
                .model("gpt-5.5")
                .modelConfigs(Map.of(AgentModelConfig.DEV,
                        new AgentModelConfig(null, null, null, "medium", null, null)))
                .build();

        new AiDevAgent(model(config), toolService).call("test", null);

        var params = (OpenAiOfficialResponsesChatRequestParameters) streamMock.getLastRequest().parameters();
        assertThat(params.reasoningEffort()).isEqualTo(ReasoningEffort.of("medium"));
    }

    @Test
    void customAgent_manualOnString_disablesHeuristic() {
        var cfg = LlmConfig.builder().providerType(AiProvider.OPEN_AI).model("deepseek-chat").build();
        // supported + on="minimal" -> verbatim, no heuristic
        assertThat(cfg.customAgentConfig(AgentModelConfig.empty().withModel("deepseek-chat"), "custom", true, "minimal", "").getThink()).isEqualTo("minimal");
        // both empty + supported -> auto marker
        assertThat(cfg.customAgentConfig(AgentModelConfig.empty().withModel("deepseek-chat"), "custom", true, "", "").getThink()).isEqualTo("true");
        // unsupported + off="false" -> verbatim off
        assertThat(cfg.customAgentConfig(AgentModelConfig.empty().withModel("deepseek-chat"), "custom", false, "", "false").getThink()).isEqualTo("false");
    }
}
