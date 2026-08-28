package org.sterl.llmpeon.provider;

import java.util.ArrayList;
import java.util.List;

import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.model.AiModel;

import dev.langchain4j.model.catalog.ModelType;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GeminiThinkingConfig.GeminiThinkingLevel;
import dev.langchain4j.model.googleai.GoogleAiGeminiModelCatalog;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;

/** Google Gemini provider (stateless singleton). */
public final class GoogleGeminiProvider implements LlmProvider {

    @Override
    public StreamingChatModel buildModel(LlmConfig c) {
        var result = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(c.getApiKey())
                .modelName(c.getModel());
        // returnThinking + sendThinking are always required: preview models return a
        // thought_signature even when thinking is "disabled". Without sendThinking(true),
        // the thought_signature is not re-sent with tool results -> INVALID_ARGUMENT error.
        result.returnThinking(Boolean.TRUE).sendThinking(Boolean.TRUE);
        // TODO per-agent think: Gemini has no per-request thinking parameter subtype in this
        // langchain4j version, so thinking stays build-time via default model support.
        if (c.isThinkSupported()) {
            var think = GeminiThinkingConfig.builder()
                .thinkingLevel(GeminiThinkingLevel.HIGH);
            result.thinkingConfig(think.build());
        }
        if (c.getMaxTokens() > 0) result.maxOutputTokens(c.getMaxTokens());
        return result
                .customHeaders(c.getHeaderParams())
                .logRequests(c.isDebugMode())
                .logResponses(c.isDebugMode())
                .build();
    }

    @Override
    public List<AiModel> listAiModels(LlmConfig config) {
        var models = GoogleAiGeminiModelCatalog.builder()
            .apiKey(config.getApiKey())
            .build()
            .listModels()
            .stream().filter(m -> m.type() == ModelType.CHAT || m.type() == ModelType.OTHER)
            .toList();

        var result = new ArrayList<AiModel>();
        for (var m : models) {
            result.add(AiModel.builder()
                    .id(m.name())
                    .name(m.displayName())
                    .maxInputTokens(m.maxInputTokens())
                    .build());
        }
        return result;
    }

    @Override
    public ExtraBodyMode extraBodyMode() {
        return ExtraBodyMode.NONE;
    }

    @Override
    public ThinkSupport thinkSupport() {
        return ThinkSupport.NONE;
    }
}
