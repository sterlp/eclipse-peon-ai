package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.sterl.llmpeon.provider.LlmProviders;

import com.openai.models.ReasoningEffort;

import dev.langchain4j.model.anthropic.AnthropicChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatRequestParameters;

/**
 * Verifies {@link org.sterl.llmpeon.provider.LlmProvider#newRequestParameters(AgentConfig, java.util.List)}
 * maps the per-agent {@code think} value into the correct provider-specific request parameter via the
 * 3-stage schema: off -> provider-specific off/omit, concrete level -> verbatim, generic on ->
 * {@link ThinkModelMapping} (no known model -> nothing).
 */
class AiProviderRequestParametersTest {

    private AgentConfig mc(AiProvider p, String think) {
        return mc(p, "m", think);
    }

    private AgentConfig mc(AiProvider p, String model, String think) {
        return AgentConfig.builder().provider(p).model(model).think(think).temperature(0.3).build();
    }

    private ChatRequestParameters params(AiProvider p, AgentConfig mc) {
        return LlmProviders.of(p).newRequestParameters(mc, List.of());
    }

    @Test
    void devAndPlan_thinkSupportResolveIndependently() {
        // GIVEN only the plan record carries a think value
        var cfg = LlmConfig.builder()
                .providerType(AiProvider.OPEN_AI).model("gpt-5.5")
                .modelConfigs(Map.of(
                        AgentModelConfig.PLAN, new AgentModelConfig(null, null, null, "high", null, null)))
                .build();
        assertThat(cfg.devAgentConfig().getThink()).isNull();
        assertThat(cfg.planAgentConfig().getThink()).isEqualTo("high");
        assertThat(cfg.compactAgentConfig().getThink()).isNull();
        assertThat(cfg.searchAgentConfig().getThink()).isNull();
    }

    @Test
    void openAiOfficialOmitsReasoningWhenOffOrUnsetOrFalse() {
        for (var think : new String[] {null, "", "false", "none", "off"}) {
            var params = (OpenAiOfficialResponsesChatRequestParameters)
                    params(AiProvider.OPEN_AI_OFFICIAL, mc(AiProvider.OPEN_AI_OFFICIAL, think));
            assertThat(params.reasoningEffort()).as("think=%s", think).isNull();
            assertThat(params.modelName()).isEqualTo("m");
            assertThat(params.temperature()).isEqualTo(0.3);
        }
    }

    @Test
    void openAiOfficialConcreteLevelPassesThrough() {
        var high = (OpenAiOfficialResponsesChatRequestParameters)
                params(AiProvider.OPEN_AI_OFFICIAL, mc(AiProvider.OPEN_AI_OFFICIAL, "high"));
        assertThat(high.reasoningEffort()).isEqualTo(ReasoningEffort.of("high"));
    }

    @Test
    void openAiOfficialGenericOnUsesModelMapping() {
        // known reasoning model -> mapped to high
        var known = (OpenAiOfficialResponsesChatRequestParameters)
                params(AiProvider.OPEN_AI_OFFICIAL, mc(AiProvider.OPEN_AI_OFFICIAL, "gpt-5.5", "true"));
        assertThat(known.reasoningEffort()).isEqualTo(ReasoningEffort.of("high"));

        // unknown model + generic on -> send nothing
        var unknown = (OpenAiOfficialResponsesChatRequestParameters)
                params(AiProvider.OPEN_AI_OFFICIAL, mc(AiProvider.OPEN_AI_OFFICIAL, "kimi-k2", "true"));
        assertThat(unknown.reasoningEffort()).isNull();
    }

    @Test
    void openAiPlainUsesStringEffort() {
        var off = (OpenAiChatRequestParameters)
                params(AiProvider.OPEN_AI, mc(AiProvider.OPEN_AI, "false"));
        assertThat(off.reasoningEffort()).isNull();

        var on = (OpenAiChatRequestParameters)
                params(AiProvider.OPEN_AI, mc(AiProvider.OPEN_AI, "medium"));
        assertThat(on.reasoningEffort()).isEqualTo("medium");

        // generic on + unknown model -> nothing
        var genericUnknown = (OpenAiChatRequestParameters)
                params(AiProvider.OPEN_AI, mc(AiProvider.OPEN_AI, "true"));
        assertThat(genericUnknown.reasoningEffort()).isNull();
    }

    @Test
    void openAiPlainGenericOnKnownModelMapsToHigh() {
        var on = (OpenAiChatRequestParameters)
                params(AiProvider.OPEN_AI, mc(AiProvider.OPEN_AI, "gpt-5.5", "true"));
        assertThat(on.reasoningEffort()).isEqualTo("high");
    }

    @Test
    void lmStudioReasoning_emptyOmits_explicitOffSendsOff_onSendsOn() {
        // empty -> omit
        var empty = (OpenAiChatRequestParameters)
                params(AiProvider.LM_STUDIO, mc(AiProvider.LM_STUDIO, ""));
        assertThat(empty.customParameters()).isNullOrEmpty();

        // explicit off-token -> reasoning:off (manual off, not silence)
        var off = (OpenAiChatRequestParameters)
                params(AiProvider.LM_STUDIO, mc(AiProvider.LM_STUDIO, "false"));
        assertThat(off.customParameters()).containsEntry("reasoning", "off");

        var on = (OpenAiChatRequestParameters)
                params(AiProvider.LM_STUDIO, mc(AiProvider.LM_STUDIO, "high"));
        assertThat(on.customParameters()).containsEntry("reasoning", "high");
    }

    @Test
    void ollamaThinkFlag_unsetOmits_offSendsFalse_onSendsTrue() {
        var unset = (OllamaChatRequestParameters)
                params(AiProvider.OLLAMA, mc(AiProvider.OLLAMA, null));
        assertThat(unset.think()).isNull();

        var off = (OllamaChatRequestParameters)
                params(AiProvider.OLLAMA, mc(AiProvider.OLLAMA, ""));
        assertThat(off.think()).isFalse();

        var on = (OllamaChatRequestParameters)
                params(AiProvider.OLLAMA, mc(AiProvider.OLLAMA, "true"));
        assertThat(on.think()).isTrue();
    }

    @Test
    void ollamaDevThinkOff_sendsThinkFalse() {
        // GIVEN an explicit off think value on the dev record
        var cfg = LlmConfig.builder()
                .providerType(AiProvider.OLLAMA)
                .model("gemma4:12b")
                .modelConfigs(Map.of(AgentModelConfig.DEV,
                        new AgentModelConfig(null, null, null, "", null, null)))
                .build();

        assertThat(cfg.devAgentConfig().getThink()).isEqualTo("");
        var params = (OllamaChatRequestParameters) cfg.devAgentConfig().newRequestParameters(List.of());
        assertThat(params.think()).isFalse();
    }

    @Test
    void ollamaUnsetStillOmitsForCompactAndSearch() {
        var cfg = LlmConfig.builder()
                .providerType(AiProvider.OLLAMA)
                .model("gemma4:12b")
                .build();

        var compact = (OllamaChatRequestParameters) cfg.compactAgentConfig().newRequestParameters(List.of());
        var search = (OllamaChatRequestParameters) cfg.searchAgentConfig().newRequestParameters(List.of());
        assertThat(compact.think()).isNull();
        assertThat(search.think()).isNull();
    }

    @Test
    void openAiThinkSupportedFalse_emptyOffOmitsReasoning() {
        var cfg = LlmConfig.builder()
                .providerType(AiProvider.OPEN_AI_OFFICIAL)
                .model("kimi-k2")
                .thinkSupported(false)
                .build();

        var params = (OpenAiOfficialResponsesChatRequestParameters) cfg.devAgentConfig().newRequestParameters(List.of());
        assertThat(params.reasoningEffort()).isNull();
    }

    @Test
    void sendThinkingTransportIndependentFromThinkValue() {
        // GIVEN an explicit off think value, but send-thinking enabled
        var cfg = LlmConfig.builder()
                .providerType(AiProvider.OPEN_AI)
                .model("m")
                .modelConfigs(Map.of(AgentModelConfig.DEV,
                        new AgentModelConfig(null, null, null, "", null, null)))
                .sendThinkingEnabled(true)
                .build();

        assertThat(cfg.shouldWeSendThinkingBackToLLM()).isTrue();
        assertThat(cfg.devAgentConfig().getThink()).isEqualTo("");
    }


    @Test
    void anthropicGenericOnUsesModelMapping() {
        var opus = (AnthropicChatRequestParameters)
                params(AiProvider.ANTHROPIC, mc(AiProvider.ANTHROPIC, "claude-opus-4-8", "true"));
        assertThat(opus.thinkingType()).isEqualTo("adaptive");

        var sonnet = (AnthropicChatRequestParameters)
                params(AiProvider.ANTHROPIC, mc(AiProvider.ANTHROPIC, "claude-sonnet-4-5", "true"));
        assertThat(sonnet.thinkingType()).isEqualTo("enabled");

        var off = (AnthropicChatRequestParameters)
                params(AiProvider.ANTHROPIC, mc(AiProvider.ANTHROPIC, "claude-sonnet-4-5", "false"));
        assertThat(off.thinkingType()).isNull();
    }

    @Test
    void geminiNeverCarriesThinking() {
        var params = params(AiProvider.GOOGLE_GEMINI, mc(AiProvider.GOOGLE_GEMINI, "high"));
        // generic params only — no provider-specific thinking subtype
        assertThat(params.getClass().getSimpleName()).isEqualTo("DefaultChatRequestParameters");
        assertThat(params.modelName()).isEqualTo("m");
    }
}
