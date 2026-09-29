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

    // UC-THINK-11
    @Test
    void reasoningEffortOf_unknownValuesAreLenient() {
        // ADR-0064 evidence: the OpenAI SDK's ReasoningEffort.of() is a lenient enum — unknown
        // values are preserved as-is (asString), not rejected. This pins the SDK contract the
        // verbatim OpenAI-family channel relies on.
        assertThat(ReasoningEffort.of("true").asString()).isEqualTo("true");
        assertThat(ReasoningEffort.of("banana").asString()).isEqualTo("banana");
    }

    // UC-THINK-7
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

    // UC-THINK-1
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

    // UC-THINK-11
    @Test
    void openAiOfficialGenericOnSendsLiteralTrue() {
        // verbatim (ADR-0064): a generic "true" is sent as-is, no model mapping
        var params = (OpenAiOfficialResponsesChatRequestParameters)
                params(AiProvider.OPEN_AI_OFFICIAL, mc(AiProvider.OPEN_AI_OFFICIAL, "gpt-5.5", "true"));
        assertThat(params.reasoningEffort()).isEqualTo(ReasoningEffort.of("true"));
    }

    // UC-THINK-11
    @Test
    void openAiPlainVerbatim() {
        // verbatim (ADR-0064): the stored value is sent as-is — no mapping, no omission
        var none = (OpenAiChatRequestParameters) params(AiProvider.OPEN_AI, mc(AiProvider.OPEN_AI, "none"));
        assertThat(none.reasoningEffort()).isEqualTo("none");

        var on = (OpenAiChatRequestParameters)
                params(AiProvider.OPEN_AI, mc(AiProvider.OPEN_AI, "gpt-5.5", "true"));
        assertThat(on.reasoningEffort()).isEqualTo("true");

        var xhigh = (OpenAiChatRequestParameters) params(AiProvider.OPEN_AI, mc(AiProvider.OPEN_AI, "xhigh"));
        assertThat(xhigh.reasoningEffort()).isEqualTo("xhigh");

        var medium = (OpenAiChatRequestParameters) params(AiProvider.OPEN_AI, mc(AiProvider.OPEN_AI, "medium"));
        assertThat(medium.reasoningEffort()).isEqualTo("medium");

        var banana = (OpenAiChatRequestParameters) params(AiProvider.OPEN_AI, mc(AiProvider.OPEN_AI, "banana"));
        assertThat(banana.reasoningEffort()).isEqualTo("banana");

        // blank = unset = nothing sent
        var blank = (OpenAiChatRequestParameters) params(AiProvider.OPEN_AI, mc(AiProvider.OPEN_AI, "   "));
        assertThat(blank.reasoningEffort()).isNull();
    }

    // UC-THINK-3
    @Test
    void lmStudioReasoning_blankOmits_offTokensSendOff_genericOnSendsOn_levelPassesThrough() {
        // unset/blank -> omit
        for (var unset : new String[] {null, "", "  "}) {
            var p = (OpenAiChatRequestParameters) params(AiProvider.LM_STUDIO, mc(AiProvider.LM_STUDIO, unset));
            assertThat(p.customParameters()).as("unset %s", (Object) unset).isNullOrEmpty();
        }
        // every explicit off-token -> reasoning:off (manual off, not silence)
        for (var off : new String[] {"false", "FALSE", "none", "no", "off", " Off "}) {
            var p = (OpenAiChatRequestParameters) params(AiProvider.LM_STUDIO, mc(AiProvider.LM_STUDIO, off));
            assertThat(p.customParameters()).as("off %s", off).containsEntry("reasoning", "off");
        }
        // generic on -> reasoning:on
        for (var on : new String[] {"true", "on", "yes"}) {
            var p = (OpenAiChatRequestParameters) params(AiProvider.LM_STUDIO, mc(AiProvider.LM_STUDIO, on));
            assertThat(p.customParameters()).as("on %s", on).containsEntry("reasoning", "on");
        }
        // explicit level passes through
        var level = (OpenAiChatRequestParameters)
                params(AiProvider.LM_STUDIO, mc(AiProvider.LM_STUDIO, "high"));
        assertThat(level.customParameters()).containsEntry("reasoning", "high");
    }

    // UC-THINK-1
    @Test
    void ollamaThinkFlag_unsetOrBlankOmits() {
        for (var unset : new String[] {null, "", "  "}) {
            var p = (OllamaChatRequestParameters) params(AiProvider.OLLAMA, mc(AiProvider.OLLAMA, unset));
            assertThat(p.think()).as("unset %s", (Object) unset).isNull();
        }
    }

    // UC-THINK-3
    @Test
    void ollamaThinkFlag_offTokensSendFalse_onSendsTrue() {
        for (var off : new String[] {"false", "FALSE", "none", "no", "off", " Off "}) {
            var p = (OllamaChatRequestParameters) params(AiProvider.OLLAMA, mc(AiProvider.OLLAMA, off));
            assertThat(p.think()).as("off %s", off).isFalse();
        }
        for (var on : new String[] {"true", "high", "on", "yes"}) {
            var p = (OllamaChatRequestParameters) params(AiProvider.OLLAMA, mc(AiProvider.OLLAMA, on));
            assertThat(p.think()).as("on %s", on).isTrue();
        }
    }

    // UC-THINK-2
    @Test
    void ollamaDevThinkOff_sendsThinkFalse() {
        // GIVEN an explicit off think value on the dev record
        var cfg = LlmConfig.builder()
                .providerType(AiProvider.OLLAMA)
                .model("gemma4:12b")
                .modelConfigs(Map.of(AgentModelConfig.DEV,
                        new AgentModelConfig(null, null, null, "false", null, null)))
                .build();

        assertThat(cfg.devAgentConfig().getThink()).isEqualTo("false");
        var params = (OllamaChatRequestParameters) cfg.devAgentConfig().newRequestParameters(List.of());
        assertThat(params.think()).isFalse();
    }

    // UC-THINK-7
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

    // UC-THINK-9
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


    // UC-THINK-8
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
