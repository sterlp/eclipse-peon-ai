package org.sterl.llmpeon.provider;

import java.util.Collections;
import java.util.List;

import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;

/**
 * One LLM provider (provider.md R1). Behaviour previously embedded in the {@code AiProvider}
 * enum constants now lives in one class per provider, resolved via {@link LlmProviders}.
 *
 * <p>Implementations are stateless singletons (thread-safety invariant: no fields).</p>
 */
public interface LlmProvider {

    /** Builds the {@link StreamingChatModel} for this provider using the given config. */
    StreamingChatModel buildModel(LlmConfig config);

    /**
     * Returns a list of available {@link AiModel}s with metadata.
     * For providers that expose capability data (Copilot, LM Studio, Mistral), only
     * tool-callable models are returned. Other providers return all known models.
     * Default: wraps the configured model ID as a single-element list.
     */
    default List<AiModel> listAiModels(LlmConfig config) {
        if (StringUtil.hasNoValue(config.getModel())) return Collections.emptyList();
        return List.of(AiModel.builder().name(config.getModel()).id(config.getModel()).build());
    }

    /**
     * Returns a sorted list of available model IDs.
     * Delegates to {@link #listAiModels(LlmConfig)} — override {@code listAiModels} instead.
     */
    default List<String> listModels(LlmConfig config) {
        return listAiModels(config).stream().map(AiModel::getId).toList();
    }

    /**
     * Builds the per-request {@link ChatRequestParameters} for this provider from a {@link AgentConfig}.
     * This is where the per-agent {@code think} value becomes a real request parameter.
     *
     * <p>Default: only the neutral fields (modelName, temperature, toolSpecifications) — no thinking.
     * Providers whose langchain4j version supports per-request thinking override this. Gemini and
     * Mistral have no per-request thinking parameter subtype, so they use this default and keep
     * thinking build-time (see the TODOs in their {@code buildModel}).</p>
     */
    default ChatRequestParameters newRequestParameters(AgentConfig mc, List<ToolSpecification> tools) {
        var b = ChatRequestParameters.builder();
        ProviderRequestSupport.applyBase(b, mc, tools);
        return b.build();
    }

    /**
     * How this provider consumes extra body parameters (provider.md R3): per-request
     * {@code customParameters} where the langchain4j model supports it, build-time for
     * providers that only offer it at model-build time, or {@link ExtraBodyMode#NONE}.
     */
    ExtraBodyMode extraBodyMode();

    /** Whether this provider can carry extra body parameters (provider.md R3). */
    default boolean supportsExtraBody() {
        return extraBodyMode() != ExtraBodyMode.NONE;
    }

    /** The form of the per-agent think input this provider can consume (provider.md R5). */
    ThinkSupport thinkSupport();
}
