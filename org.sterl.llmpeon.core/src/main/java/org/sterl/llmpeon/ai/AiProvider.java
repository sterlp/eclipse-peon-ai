package org.sterl.llmpeon.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.ai.model.AiModelParser;
import org.sterl.llmpeon.shared.StringUtil;

import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.catalog.ModelType;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GeminiThinkingConfig.GeminiThinkingLevel;
import dev.langchain4j.model.googleai.GoogleAiGeminiModelCatalog;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.model.mistralai.MistralAiStreamingChatModel;
import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModels;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum AiProvider {

    OLLAMA {
        @Override
        StreamingChatModel buildModel(LlmConfig c) {
            return OllamaStreamingChatModel.builder()
                    .timeout(TIMEOUT)
                    .baseUrl(c.getUrl())
                    .modelName(c.getModel())
                    .think(c.isThinkingEnabled())
                    .returnThinking(c.isThinkingEnabled())
                    .customHeaders(c.getHeaderParams())
                    .logRequests(c.isDebugMode())
                    .logResponses(c.isDebugMode())
                    .build();
        }

        @Override
        public List<AiModel> listAiModels(LlmConfig c) {
            var models = OllamaModels.builder()
                    .baseUrl(c.getUrl())
                    .timeout(MODEL_TIMEOUT)
                    .build()
                    .availableModels()
                    .content();
            var result = new ArrayList<AiModel>(models.size());
            for (OllamaModel m : models) {
                result.add(AiModel.builder().id(m.getName()).name(m.getModel()).build());
            }
            Collections.sort(result, (a, b) -> a.getId().compareTo(b.getId()));
            return result;
        }
    },

    OPEN_AI {
        @Override
        StreamingChatModel buildModel(LlmConfig c) {
            return OpenAiStreamingChatModel.builder()
                    .timeout(TIMEOUT)
                    .baseUrl(c.getUrl())
                    .modelName(c.getModel())
                    .apiKey(c.getApiKey())
                    .strictJsonSchema(true)
                    .returnThinking(c.isThinkingEnabled())
                    .sendThinking(c.shouldWeSendThinkingBackToLLM())
                    .customHeaders(c.getHeaderParams())
                    .customQueryParams(c.getQueryParams())
                    .logRequests(c.isDebugMode())
                    .logResponses(c.isDebugMode())
                    .reasoningEffort(c.isThinkingEnabled() ? "high" : "low")
                    .build();
        }

        @Override
        public List<AiModel> listAiModels(LlmConfig c) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(c.getUrl() + "/models"))
                    .header("Authorization", "Bearer " + c.getApiKey());
            c.getHeaderParams().forEach(request::header);
            
            return SharedHttpClient.cancelAndGet(request, AiModelParser::parseOpenApiModels);
        }
    },
    
    OPEN_AI_OFFICIAL {
        @Override
        StreamingChatModel buildModel(LlmConfig c) {
            var result = OpenAiOfficialResponsesStreamingChatModel.builder()
                    .timeout(TIMEOUT)
                    .baseUrl(c.getUrl())
                    .modelName(c.getModel())
                    .apiKey(c.getApiKey())
                    .strictTools(true)
                    .isMicrosoftFoundry(true)
                    .customHeaders(c.getHeaderParams());
            
            if (c.isThinkingEnabled()) {
                result.reasoningEffort(ReasoningEffort.HIGH);
                result.reasoningSummary(Reasoning.Summary.DETAILED);
                result.defaultRequestParameters(OpenAiOfficialResponsesChatRequestParameters.builder()
                        .reasoningEffort(ReasoningEffort.HIGH)
                        .reasoningSummary(Reasoning.Summary.DETAILED).build()
                );
            }
            return result.build();
        }

        @Override
        public List<AiModel> listAiModels(LlmConfig c) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(c.getUrl() + "/models"))
                    .header("Authorization", "Bearer " + c.getApiKey());
            c.getHeaderParams().forEach(request::header);
            
            return SharedHttpClient.cancelAndGet(request, AiModelParser::parseOpenApiModels);
        }
    },

    // model URL /api/v1/models
    LM_STUDIO {
        @Override
        StreamingChatModel buildModel(LlmConfig c) {
            var http1 = JdkHttpClient.builder()
                    .httpClientBuilder(HttpClient.newBuilder()
                            .version(HttpClient.Version.HTTP_1_1));
            return OpenAiStreamingChatModel.builder()
                    .timeout(TIMEOUT)
                    .baseUrl(c.getUrl())
                    .modelName(c.getModel())
                    .apiKey(StringUtil.hasValue(c.getApiKey()) ? c.getApiKey() : "lm-studio")
                    .httpClientBuilder(http1)
                    .returnThinking(c.isThinkingEnabled())
                    .sendThinking(c.shouldWeSendThinkingBackToLLM())
                    .customHeaders(c.getHeaderParams())
                    .customQueryParams(c.getQueryParams())
                    .logRequests(c.isDebugMode())
                    .logResponses(c.isDebugMode())
                    .build();
        }

        @Override
        public List<AiModel> listAiModels(LlmConfig c) {
            var url = c.getUrl().replace("/v1", "/api/v1");
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/models"));
            c.getHeaderParams().forEach(request::header);
            return SharedHttpClient.cancelAndGet(request, AiModelParser::parseLmStudioModels);
        }
    },

    GOOGLE_GEMINI {
        @Override
        StreamingChatModel buildModel(LlmConfig c) {
            var result = GoogleAiGeminiStreamingChatModel.builder()
                    .apiKey(c.getApiKey())
                    .modelName(c.getModel());
            // returnThinking + sendThinking are always required: preview models return a
            // thought_signature even when thinking is "disabled". Without sendThinking(true),
            // the thought_signature is not re-sent with tool results -> INVALID_ARGUMENT error.
            result.returnThinking(Boolean.TRUE).sendThinking(Boolean.TRUE);
            if (c.isThinkingEnabled()) {
                result.thinkingConfig(GeminiThinkingConfig.builder()
                        .thinkingLevel(GeminiThinkingLevel.HIGH)
                        .build());
            } else {
                result.thinkingConfig(GeminiThinkingConfig.builder()
                        .thinkingBudget(0)
                        .build());
            }
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
    },

    MISTRAL {
        private static final String MODELS_URL = "https://api.mistral.ai/v1/models";

        @Override
        StreamingChatModel buildModel(LlmConfig c) {
            return MistralAiStreamingChatModel.builder()
                    .timeout(TIMEOUT)
                    .modelName(c.getModel())
                    .apiKey(c.getApiKey())
                    .returnThinking(c.isThinkingEnabled())
                    .sendThinking(c.shouldWeSendThinkingBackToLLM())
                    .customHeaders(c.getHeaderParams())
                    .logRequests(c.isDebugMode())
                    .logResponses(c.isDebugMode())
                    .build();
        }

        @Override
        public List<AiModel> listAiModels(LlmConfig c) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(MODELS_URL))
                    .header("X-API-Key", c.getApiKey());
            c.getHeaderParams().forEach(request::header);
            return SharedHttpClient.cancelAndGet(request, AiModelParser::parseMistralModels);
        }
    },

    // Previously wrapped to fix missing modelName + temperature=1.0 for thinking (langchain4j <1.13);
    // verify still needed if Anthropic calls regress.
    ANTHROPIC {
        private static final String MODELS_URL = "https://api.anthropic.com/v1/models";
        private static final String ANTHROPIC_VERSION = "2023-06-01";

        @Override
        StreamingChatModel buildModel(LlmConfig c) {
            var builder = AnthropicStreamingChatModel.builder()
                    .timeout(TIMEOUT)
                    .modelName(c.getModel())
                    .apiKey(c.getApiKey());
            if (c.getUrl() != null && c.getUrl().length() > 4) {
                builder.baseUrl(c.getUrl());
            }
            if (c.isThinkingEnabled()) {
                builder.thinkingType("enabled");
            }
            return builder
                    .customHeaders(c.getHeaderParams())
                    .logRequests(c.isDebugMode())
                    .logResponses(c.isDebugMode())
                    .build();
        }

        @Override
        public List<AiModel> listAiModels(LlmConfig c) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(MODELS_URL))
                    .header("x-api-key", c.getApiKey())
                    .header("anthropic-version", ANTHROPIC_VERSION);
            c.getHeaderParams().forEach(request::header);

            return SharedHttpClient.cancelAndGet(request, AiModelParser::parseAnthropicModels);
        }
    },

    // GitHub Models marketplace — PAT-based, pay-per-use, models.github.ai
    GITHUB_MODELS {
        private static final String DEFAULT_BASE_URL    = "https://models.github.ai/inference";
        private static final String CATALOG_URL         = "https://models.github.ai/catalog/models";
        private static final String CATALOG_API_VERSION = "2026-03-10";

        private String baseUrl(LlmConfig c) {
            return (c.getUrl() != null && !c.getUrl().isBlank())
                    ? c.getUrl().replaceAll("/+$", "")
                    : DEFAULT_BASE_URL;
        }

        @Override
        StreamingChatModel buildModel(LlmConfig c) {
            return OpenAiOfficialResponsesStreamingChatModel.builder()
                    .timeout(TIMEOUT)
                    .baseUrl(baseUrl(c))
                    .apiKey(c.getApiKey() != null && !c.getApiKey().isBlank() ? c.getApiKey() : "not-configured")
                    .modelName(c.getModel())
                    .isGitHubModels(true)
                    .strictTools(true)
                    .customHeaders(c.getHeaderParams())
                    .build();
        }

        // https://docs.github.com/en/rest/models/catalog?apiVersion=2026-03-10#list-all-models
        @Override
        public List<AiModel> listAiModels(LlmConfig c) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(CATALOG_URL))
                    .header("Authorization", "Bearer " + c.getApiKey())
                    .header("X-GitHub-Api-Version", CATALOG_API_VERSION);
            c.getHeaderParams().forEach(request::header);

            return SharedHttpClient.cancelAndGet(request, AiModelParser::parseGithubModels);
        }
    },

    // GitHub Copilot subscription — OAuth Device Flow, api.githubcopilot.com
    GITHUB_COPILOT {
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
        StreamingChatModel buildModel(LlmConfig c) {
            var headers = new HashMap<String, String>();
            headers.putAll(copilotHeaders());
            headers.putAll(c.getHeaderParams());
            
            return OpenAiStreamingChatModel.builder()
                    .timeout(TIMEOUT)
                    .baseUrl(baseUrl(c))
                    .apiKey(c.getApiKey() != null && !c.getApiKey().isBlank() ? c.getApiKey() : "not-configured")
                    .modelName(c.getModel())
                    
                    .customHeaders(headers)
                    .customQueryParams(c.getQueryParams())
                    .logRequests(c.isDebugMode())
                    .logResponses(c.isDebugMode())

                    .build();
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

            return SharedHttpClient.cancelAndGet(request, AiModelParser::parseCopilotApiModels);
        }
    };

    // Streaming only needs to cover time-to-first-token (connect + model warmup), not the full response duration.
    private static final Duration TIMEOUT = Duration.ofMinutes(3);
    private static final Duration MODEL_TIMEOUT = SharedHttpClient.MODEL_TIMEOUT;

    // --- Public API ---

    /** Builds the {@link StreamingChatModel} for this provider using the given config. */
    abstract StreamingChatModel buildModel(LlmConfig config);

    /**
     * Returns a list of available {@link AiModel}s with metadata.
     * For providers that expose capability data (Copilot, LM Studio, Mistral), only
     * tool-callable models are returned. Other providers return all known models.
     * Default: wraps the configured model ID as a single-element list.
     */
    public List<AiModel> listAiModels(LlmConfig config) {
        if (StringUtil.hasNoValue(config.getModel())) return Collections.emptyList();
        return List.of(AiModel.builder().name(config.getModel()).id(config.getModel()).build());
    }

    /**
     * Returns a sorted list of available model IDs.
     * Delegates to {@link #listAiModels(LlmConfig)} — override {@code listAiModels} instead.
     */
    public final List<String> listModels(LlmConfig config) {
        return listAiModels(config).stream().map(AiModel::getId).toList();
    }

    public static AiProvider parse(String string) {
        try {
            return AiProvider.valueOf(string);
        } catch (Exception e) {
            System.err.println("AiProvider: unknown " + string + " using " + OLLAMA);
            return OLLAMA;
        }
    }
}
