package org.sterl.llmpeon.provider;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.SharedHttpClient;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.ai.model.AiModelParser;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

/** GitHub Copilot subscription provider (stateless singleton). */
// GitHub Copilot subscription — OAuth Device Flow, api.githubcopilot.com
public final class GithubCopilotProvider implements LlmProvider {

    private static final String DEFAULT_BASE_URL = "https://api.githubcopilot.com";
    // Impersonate the official Microsoft Copilot Eclipse plugin so the API does not
    // fall back to the "vscode-nl" integrator (which exposes only a tiny model whitelist).
    private static final String INTEGRATION_ID = "copilot-eclipse";

    private static Map<String, String> copilotHeaders() {
        String eclipseVersion = System.getProperty(
                "org.eclipse.platform.version", System.getProperty("osgi.framework.version", "4.36.0"));
        return Map.of(
                "Copilot-Integration-Id", INTEGRATION_ID,
                "Editor-Version",         "Eclipse/" + eclipseVersion,
                "Editor-Plugin-Version",  "copilot-eclipse/0.16.0");
    }

    private String baseUrl(LlmConfig c) {
        return (c.getUrl() != null && !c.getUrl().isBlank())
                ? c.getUrl().replaceAll("/+$", "")
                : DEFAULT_BASE_URL;
    }

    @Override
    public StreamingChatModel buildModel(LlmConfig c) {
        var headers = new HashMap<String, String>();
        headers.putAll(copilotHeaders());
        headers.putAll(c.getHeaderParams());

        var builder = OpenAiStreamingChatModel.builder()
                .timeout(c.getTimeout())
                .baseUrl(baseUrl(c))
                .apiKey(c.getApiKey() != null && !c.getApiKey().isBlank() ? c.getApiKey() : "not-configured")
                .modelName(c.getModel())

                .customHeaders(headers)
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
        var effort = ProviderRequestSupport.effortFor(mc);
        if (effort != null) b.reasoningEffort(effort);
        var custom = ProviderRequestSupport.mergeCustomParameters(null, mc);
        if (custom != null) b.customParameters(custom);
        return b.build();
    }

    @Override
    public List<AiModel> listAiModels(LlmConfig c) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(DEFAULT_BASE_URL + "/models"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + c.getApiKey());

        var headers = new HashMap<String, String>();
        headers.putAll(copilotHeaders());
        headers.putAll(c.getHeaderParams());

        headers.forEach(request::header);

        return SharedHttpClient.getModels(request, AiModelParser::parseCopilotApiModels);
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
