package org.sterl.llmpeon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.openai.models.ReasoningEffort;

import dev.langchain4j.model.anthropic.AnthropicChatRequestParameters;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatRequestParameters;

/**
 * Verifies {@link AiProvider#newRequestParameters(AgentConfig, java.util.List)} maps the per-agent
 * {@code think} value into the correct provider-specific request parameter via the 3-stage schema:
 * off -> nothing, concrete level -> verbatim, generic on -> {@link ThinkModelMapping} (no known
 * model -> nothing).
 */
class AiProviderRequestParametersTest {

    private AgentConfig mc(AiProvider p, String think) {
        return mc(p, "m", think);
    }

    private AgentConfig mc(AiProvider p, String model, String think) {
        return AgentConfig.builder().provider(p).model(model).think(think).temperature(0.3).build();
    }

    @Test
    void devAndPlan_resolveIndependently() {
        var cfg = LlmConfig.builder()
                .providerType(AiProvider.OPEN_AI).model("gpt-5.5")
                .thinkEnabled(false)                                   // dev off
                .planThinkEnabled(true).planThinkOnString("high")      // plan on, manual "high"
                .build();
        assertThat(cfg.devAgentConfig().getThink()).isEqualTo("");     // off -> ""
        assertThat(cfg.planAgentConfig().getThink()).isEqualTo("high");// manual on -> "high"
        assertThat(cfg.compactAgentConfig().getThink()).isNull();
        assertThat(cfg.searchAgentConfig().getThink()).isNull();
    }

    @Test
    void openAiOfficialOmitsReasoningWhenOffOrUnsetOrFalse() {
        for (var think : new String[] {null, "", "false", "none", "off"}) {
            var params = (OpenAiOfficialResponsesChatRequestParameters)
                    AiProvider.OPEN_AI_OFFICIAL.newRequestParameters(mc(AiProvider.OPEN_AI_OFFICIAL, think), List.of());
            assertThat(params.reasoningEffort()).as("think=%s", think).isNull();
            assertThat(params.modelName()).isEqualTo("m");
            assertThat(params.temperature()).isEqualTo(0.3);
        }
    }

    @Test
    void openAiOfficialConcreteLevelPassesThrough() {
        var high = (OpenAiOfficialResponsesChatRequestParameters)
                AiProvider.OPEN_AI_OFFICIAL.newRequestParameters(mc(AiProvider.OPEN_AI_OFFICIAL, "high"), List.of());
        assertThat(high.reasoningEffort()).isEqualTo(ReasoningEffort.of("high"));
    }

    @Test
    void openAiOfficialGenericOnUsesModelMapping() {
        // known reasoning model -> mapped to high
        var known = (OpenAiOfficialResponsesChatRequestParameters)
                AiProvider.OPEN_AI_OFFICIAL.newRequestParameters(mc(AiProvider.OPEN_AI_OFFICIAL, "gpt-5.5", "true"), List.of());
        assertThat(known.reasoningEffort()).isEqualTo(ReasoningEffort.of("high"));

        // unknown model + generic on -> send nothing
        var unknown = (OpenAiOfficialResponsesChatRequestParameters)
                AiProvider.OPEN_AI_OFFICIAL.newRequestParameters(mc(AiProvider.OPEN_AI_OFFICIAL, "kimi-k2", "true"), List.of());
        assertThat(unknown.reasoningEffort()).isNull();
    }

    @Test
    void openAiPlainUsesStringEffort() {
        var off = (OpenAiChatRequestParameters)
                AiProvider.OPEN_AI.newRequestParameters(mc(AiProvider.OPEN_AI, "false"), List.of());
        assertThat(off.reasoningEffort()).isNull();

        var on = (OpenAiChatRequestParameters)
                AiProvider.OPEN_AI.newRequestParameters(mc(AiProvider.OPEN_AI, "medium"), List.of());
        assertThat(on.reasoningEffort()).isEqualTo("medium");

        // generic on + unknown model -> nothing
        var genericUnknown = (OpenAiChatRequestParameters)
                AiProvider.OPEN_AI.newRequestParameters(mc(AiProvider.OPEN_AI, "true"), List.of());
        assertThat(genericUnknown.reasoningEffort()).isNull();
    }

    @Test
    void openAiPlainGenericOnKnownModelMapsToHigh() {
        var on = (OpenAiChatRequestParameters)
                AiProvider.OPEN_AI.newRequestParameters(mc(AiProvider.OPEN_AI, "gpt-5.5", "true"), List.of());
        assertThat(on.reasoningEffort()).isEqualTo("high");
    }

    @Test
    void lmStudioReasoning_emptyOmits_explicitOffSendsOff_onSendsOn() {
        // empty -> omit
        var empty = (OpenAiChatRequestParameters)
                AiProvider.LM_STUDIO.newRequestParameters(mc(AiProvider.LM_STUDIO, ""), List.of());
        assertThat(empty.customParameters()).isNullOrEmpty();

        // explicit off-token -> reasoning:off (manual off, not silence)
        var off = (OpenAiChatRequestParameters)
                AiProvider.LM_STUDIO.newRequestParameters(mc(AiProvider.LM_STUDIO, "false"), List.of());
        assertThat(off.customParameters()).containsEntry("reasoning", "off");

        var on = (OpenAiChatRequestParameters)
                AiProvider.LM_STUDIO.newRequestParameters(mc(AiProvider.LM_STUDIO, "high"), List.of());
        assertThat(on.customParameters()).containsEntry("reasoning", "on");
    }

    @Test
    void ollamaThinkFlag_emptyOmits_explicitFalseSendsFalse_onSendsTrue() {
        var empty = (OllamaChatRequestParameters)
                AiProvider.OLLAMA.newRequestParameters(mc(AiProvider.OLLAMA, null), List.of());
        assertThat(empty.think()).isNull();

        // explicit off-token -> think:false (manual off, not silence)
        var off = (OllamaChatRequestParameters)
                AiProvider.OLLAMA.newRequestParameters(mc(AiProvider.OLLAMA, "false"), List.of());
        assertThat(off.think()).isFalse();

        var on = (OllamaChatRequestParameters)
                AiProvider.OLLAMA.newRequestParameters(mc(AiProvider.OLLAMA, "true"), List.of());
        assertThat(on.think()).isTrue();
    }

    @Test
    void anthropicGenericOnUsesModelMapping() {
        var opus = (AnthropicChatRequestParameters)
                AiProvider.ANTHROPIC.newRequestParameters(mc(AiProvider.ANTHROPIC, "claude-opus-4-8", "true"), List.of());
        assertThat(opus.thinkingType()).isEqualTo("adaptive");

        var sonnet = (AnthropicChatRequestParameters)
                AiProvider.ANTHROPIC.newRequestParameters(mc(AiProvider.ANTHROPIC, "claude-sonnet-4-5", "true"), List.of());
        assertThat(sonnet.thinkingType()).isEqualTo("enabled");

        var off = (AnthropicChatRequestParameters)
                AiProvider.ANTHROPIC.newRequestParameters(mc(AiProvider.ANTHROPIC, "claude-sonnet-4-5", "false"), List.of());
        assertThat(off.thinkingType()).isNull();
    }

    @Test
    void geminiNeverCarriesThinking() {
        var params = AiProvider.GOOGLE_GEMINI.newRequestParameters(mc(AiProvider.GOOGLE_GEMINI, "high"), List.of());
        // generic params only — no provider-specific thinking subtype
        assertThat(params.getClass().getSimpleName()).isEqualTo("DefaultChatRequestParameters");
        assertThat(params.modelName()).isEqualTo("m");
    }
}
