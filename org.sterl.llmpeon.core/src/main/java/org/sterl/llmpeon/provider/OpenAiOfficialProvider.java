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

/** OpenAI-official (Responses API) provider (stateless singleton). */
public final class OpenAiOfficialProvider implements LlmProvider {

    @Override
    public StreamingChatModel buildModel(LlmConfig c) {
        var result = OpenAiOfficialResponsesStreamingChatModel.builder()
                .timeout(c.getTimeout())
                .baseUrl(c.getUrl())
                .modelName(c.getModel())
                .apiKey(c.getApiKey())
                .strictTools(true)
                .isMicrosoftFoundry(true)
                .customHeaders(c.getHeaderParams());

        // reasoning.effort is now set per request (see newRequestParameters); only the
        // maxOutputTokens default stays baked into the model.
        if (c.getMaxTokens() > 0) {
            result.defaultRequestParameters(OpenAiOfficialResponsesChatRequestParameters.builder()
                    .maxOutputTokens(c.getMaxTokens()).build());
        }
        return result.build();
    }

    @Override
    public ChatRequestParameters newRequestParameters(AgentConfig mc, List<ToolSpecification> tools) {
        return ProviderRequestSupport.openAiOfficialParameters(mc, tools);
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
        return ExtraBodyMode.NONE;
    }

    @Override
    public ThinkSupport thinkSupport() {
        return ProviderRequestSupport.openAiFamilyThinkSupport();
    }
}
