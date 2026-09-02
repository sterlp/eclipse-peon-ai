package org.sterl.llmpeon.provider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.SharedHttpClient;
import org.sterl.llmpeon.ai.ThinkResolver;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.ai.model.AiModelParser;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

/** LM Studio provider (stateless singleton). */
// model URL /api/v1/models
public final class LmStudioProvider implements LlmProvider {

    @Override
    public StreamingChatModel buildModel(LlmConfig c) {
        var http1 = JdkHttpClient.builder()
                .httpClientBuilder(HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1));
        var builder = OpenAiStreamingChatModel.builder()
                .timeout(c.getTimeout())
                .baseUrl(c.getUrl())
                .modelName(c.getModel())
                .apiKey(StringUtil.hasValue(c.getApiKey()) ? c.getApiKey() : "lm-studio")
                .httpClientBuilder(http1)
                .returnThinking(true)
                .sendThinking(c.shouldWeSendThinkingBackToLLM())
                .customHeaders(c.getHeaderParams())
                .customQueryParams(c.getQueryParams())
                .logRequests(c.isDebugMode())
                .logResponses(c.isDebugMode());
        if (c.getMaxTokens() > 0) builder.maxCompletionTokens(c.getMaxTokens());
        return builder.build();
    }

    @Override
    public ChatRequestParameters newRequestParameters(AgentConfig mc, List<ToolSpecification> tools) {
        var b = OpenAiChatRequestParameters.builder();
        ProviderRequestSupport.applyBase(b, mc, tools);
        Map<String, Object> reasoning = null;
        if (StringUtil.hasValue(mc.getThink())) {
            reasoning = Map.of("reasoning", ThinkResolver.toReasoning(mc.getThink()));
        }
        var custom = ProviderRequestSupport.mergeCustomParameters(reasoning, mc);
        if (custom != null) b.customParameters(custom);
        return b.build();
    }

    @Override
    public List<AiModel> listAiModels(LlmConfig c) {
        var url = c.getUrl().replace("/v1", "/api/v1");
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url + "/models"));
        c.getHeaderParams().forEach(request::header);
        return SharedHttpClient.getModels(request, AiModelParser::parseLmStudioModels);
    }

    @Override
    public ExtraBodyMode extraBodyMode() {
        return ExtraBodyMode.PER_REQUEST;
    }

    @Override
    public ThinkSupport thinkSupport() {
        return new ThinkSupport.FreeString();
    }
}
