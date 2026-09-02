package org.sterl.llmpeon.provider;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;

import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.SharedHttpClient;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.ai.model.AiModelParser;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.mistralai.MistralAiStreamingChatModel;

/** Mistral provider (stateless singleton). */
public final class MistralProvider implements LlmProvider {

    private static final String MODELS_URL = "https://api.mistral.ai/v1/models";

    @Override
    public StreamingChatModel buildModel(LlmConfig c) {
        // TODO per-agent think: Mistral has no per-request thinking parameter subtype in this
        // langchain4j version, so thinking stays build-time via the global thinkingEnabled toggle.
        var builder = MistralAiStreamingChatModel.builder()
                .timeout(c.getTimeout())
                .modelName(c.getModel())
                .apiKey(c.getApiKey())
                .returnThinking(true)
                .sendThinking(c.shouldWeSendThinkingBackToLLM())
                .customHeaders(c.getHeaderParams())
                .logRequests(c.isDebugMode())
                .logResponses(c.isDebugMode());
        if (c.getMaxTokens() > 0) builder.maxTokens(c.getMaxTokens());
        return builder.build();
    }

    @Override
    public List<AiModel> listAiModels(LlmConfig c) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(MODELS_URL))
                .header("X-API-Key", c.getApiKey());
        c.getHeaderParams().forEach(request::header);
        return SharedHttpClient.getModels(request, AiModelParser::parseMistralModels);
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
