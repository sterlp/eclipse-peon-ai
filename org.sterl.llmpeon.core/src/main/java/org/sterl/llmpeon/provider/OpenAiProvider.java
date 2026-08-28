package org.sterl.llmpeon.provider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.SharedHttpClient;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.ai.model.AiModelParser;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

/** OpenAI-compatible provider (stateless singleton). */
public final class OpenAiProvider implements LlmProvider {

    @Override
    public StreamingChatModel buildModel(LlmConfig c) {
        var http1 = JdkHttpClient.builder()
                .httpClientBuilder(HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1));

        var builder = OpenAiStreamingChatModel.builder()
                .timeout(c.getTimeout())
                .baseUrl(c.getUrl())
                .modelName(c.getModel())
                .apiKey(c.getApiKey())
                .httpClientBuilder(http1)
                .strictJsonSchema(true)
                .returnThinking(true)
                .sendThinking(c.shouldWeSendThinkingBackToLLM())
                .customHeaders(c.getHeaderParams())
                .customQueryParams(c.getQueryParams())
                .logRequests(c.isDebugMode())
                .logResponses(c.isDebugMode());

        // reasoning.effort is now set per request (see newRequestParameters).
        if (c.getMaxTokens() > 0) builder.maxCompletionTokens(c.getMaxTokens());
        return builder.build();
    }

    @Override
    public ChatRequestParameters newRequestParameters(AgentConfig mc, List<ToolSpecification> tools) {
        var b = OpenAiChatRequestParameters.builder();
        ProviderRequestSupport.applyBase(b, mc, tools);
        var effort = ProviderRequestSupport.effortFor(mc);
        if (effort != null) b.reasoningEffort(effort);

        if (mc.getModel() != null && mc.getModel().startsWith("claude")) {
            b.customParameters(Map.of("cache_control", Map.of("type", "ephemeral")));
        }
        // TODO for "gpt" based on the agent prompt_cache_key

        return b.build();
    }

    @Override
    public List<AiModel> listAiModels(LlmConfig c) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(c.getUrl() + "/models"))
                .header("Authorization", "Bearer " + c.getApiKey());
        c.getHeaderParams().forEach(request::header);

        return SharedHttpClient.cancelAndGet(request, AiModelParser::parseOpenApiModels);
    }

    @Override
    public ExtraBodyMode extraBodyMode() {
        return ExtraBodyMode.PER_REQUEST;
    }

    @Override
    public ThinkSupport thinkSupport() {
        return ProviderRequestSupport.openAiFamilyThinkSupport();
    }
}
