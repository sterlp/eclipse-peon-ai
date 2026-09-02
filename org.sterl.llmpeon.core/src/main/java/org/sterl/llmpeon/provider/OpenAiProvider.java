package org.sterl.llmpeon.provider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.SharedHttpClient;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.ai.model.AiModelParser;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

/** OpenAI-compatible provider (stateless singleton). */
public final class OpenAiProvider implements LlmProvider {

    /** Top-level request-body field for explicit prompt caching (Azure/OpenAI, GPT-5.6+). */
    private static final String PROMPT_CACHE_KEY = "prompt_cache_key";
    /** Model prefix (case-insensitive) that gets the per-agent default cache key (R8). */
    private static final String GPT5_PREFIX = "gpt-5";
    /** Default cache key prefix — one stable key per agent (R8). */
    private static final String DEFAULT_CACHE_KEY_PREFIX = "peon-ai-";

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

        var custom = ProviderRequestSupport.mergeCustomParameters(defaultCacheKeyEntries(mc), mc);
        if (custom != null) b.customParameters(custom);

        return b.build();
    }

    /**
     * R8 default: gpt-5* models (case-insensitive prefix) get a stable per-agent
     * {@code prompt_cache_key} so long shared prefixes hit the provider KV-cache. A
     * non-blank user value in the extra body wins (merge); no model or no agent id → no key.
     */
    private Map<String, Object> defaultCacheKeyEntries(AgentConfig mc) {
        if (!StringUtil.hasValue(mc.getModel()) || !StringUtil.hasValue(mc.getId())) return null;
        if (!mc.getModel().toLowerCase(Locale.ROOT).startsWith(GPT5_PREFIX)) return null;
        return Map.of(PROMPT_CACHE_KEY, DEFAULT_CACHE_KEY_PREFIX + mc.getId());
    }

    @Override
    public List<AiModel> listAiModels(LlmConfig c) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(c.getUrl() + "/models"))
                .header("Authorization", "Bearer " + c.getApiKey());
        c.getHeaderParams().forEach(request::header);

        return SharedHttpClient.getModels(request, AiModelParser::parseOpenApiModels);
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
