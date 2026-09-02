package org.sterl.llmpeon.provider;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;

import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.SharedHttpClient;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.ai.model.AiModelParser;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.anthropic.AnthropicChatRequestParameters;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;

// Previously wrapped to fix missing modelName + temperature=1.0 for thinking (langchain4j <1.13);
// verify still needed if Anthropic calls regress.
public final class AnthropicProvider implements LlmProvider {

    private static final String MODELS_URL = "https://api.anthropic.com/v1/models";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Override
    public StreamingChatModel buildModel(LlmConfig c) {
        var builder = AnthropicStreamingChatModel.builder()
                .timeout(c.getTimeout())
                .modelName(c.getModel())
                .apiKey(c.getApiKey());

        if (c.getUrl() != null && c.getUrl().length() > 4) {
            builder.baseUrl(c.getUrl());
        }
        if (c.getMaxTokens() > 0) {
            builder.maxTokens(c.getMaxTokens());
        }
        var extraBody = ExtraBody.parse(c.getExtraBody());
        if (extraBody != null) {
            builder.customParameters(extraBody);
        }
        // thinkingType/budget are now set per request (see newRequestParameters).
        return builder
                .customHeaders(c.getHeaderParams())
                .logRequests(c.isDebugMode())
                .logResponses(c.isDebugMode())
                .build();
    }

    @Override
    public ChatRequestParameters newRequestParameters(AgentConfig mc, List<ToolSpecification> tools) {
        var b = AnthropicChatRequestParameters.builder();
        ProviderRequestSupport.applyBase(b, mc, tools);
        var type = ProviderRequestSupport.anthropicThinkingType(mc);
        if (type != null) {
            if ("adaptive".equals(type)) {
                b.thinkingType("adaptive");
            } else {
                b.thinkingType(type).thinkingBudgetTokens(8000);
            }
            b.sendThinking(Boolean.TRUE).returnThinking(Boolean.TRUE);
        }
        return b.build();
    }

    @Override
    public List<AiModel> listAiModels(LlmConfig c) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(MODELS_URL))
                .header("x-api-key", c.getApiKey())
                .header("anthropic-version", ANTHROPIC_VERSION);
        c.getHeaderParams().forEach(request::header);

        return SharedHttpClient.getModels(request, AiModelParser::parseAnthropicModels);
    }

    /**
     * Anthropic consumes extra body fields <b>build-time only</b>: the parsed body is baked into
     * the model's {@code customParameters} (there is no per-request field).
     */
    @Override
    public ExtraBodyMode extraBodyMode() {
        return ExtraBodyMode.BUILD_TIME;
    }

    @Override
    public ThinkSupport thinkSupport() {
        return new ThinkSupport.Values(List.of("adaptive", "enabled"));
    }
}
