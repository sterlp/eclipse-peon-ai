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
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel;

/** GitHub Models marketplace — PAT-based, pay-per-use, models.github.ai (stateless singleton). */
public final class GithubModelsProvider implements LlmProvider {

    private static final String DEFAULT_BASE_URL    = "https://models.github.ai/inference";
    private static final String CATALOG_URL         = "https://models.github.ai/catalog/models";
    private static final String CATALOG_API_VERSION = "2026-03-10";

    private String baseUrl(LlmConfig c) {
        return (c.getUrl() != null && !c.getUrl().isBlank())
                ? c.getUrl().replaceAll("/+$", "")
                : DEFAULT_BASE_URL;
    }

    @Override
    public StreamingChatModel buildModel(LlmConfig c) {
        var builder = OpenAiOfficialResponsesStreamingChatModel.builder()
                .timeout(c.getTimeout())
                .baseUrl(baseUrl(c))
                .apiKey(c.getApiKey() != null && !c.getApiKey().isBlank() ? c.getApiKey() : "not-configured")
                .modelName(c.getModel())
                .isGitHubModels(true)
                .strictTools(true)
                .customHeaders(c.getHeaderParams());
        if (c.getMaxTokens() > 0) {
            builder.defaultRequestParameters(OpenAiOfficialResponsesChatRequestParameters.builder()
                    .maxOutputTokens(c.getMaxTokens()).build());
        }
        return builder.build();
    }

    @Override
    public ChatRequestParameters newRequestParameters(AgentConfig mc, List<ToolSpecification> tools) {
        return ProviderRequestSupport.openAiOfficialParameters(mc, tools);
    }

    // https://docs.github.com/en/rest/models/catalog?apiVersion=2026-03-10#list-all-models
    @Override
    public List<AiModel> listAiModels(LlmConfig c) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(CATALOG_URL))
                .header("Authorization", "Bearer " + c.getApiKey())
                .header("X-GitHub-Api-Version", CATALOG_API_VERSION);
        c.getHeaderParams().forEach(request::header);

        return SharedHttpClient.getModels(request, AiModelParser::parseGithubModels);
    }

    @Override
    public ExtraBodyMode extraBodyMode() {
        return ExtraBodyMode.NONE;
    }

    @Override
    public ThinkSupport thinkSupport() {
        return ProviderRequestSupport.openAiFamilyThinkSupport();
    }
}
