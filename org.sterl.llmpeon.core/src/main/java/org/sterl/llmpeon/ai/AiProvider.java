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

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.anthropic.AnthropicChatRequestParameters;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.catalog.ModelType;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GeminiThinkingConfig.GeminiThinkingLevel;
import dev.langchain4j.model.googleai.GoogleAiGeminiModelCatalog;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.model.mistralai.MistralAiStreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModels;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum AiProvider {

    OLLAMA {
        @Override
        StreamingChatModel buildModel(LlmConfig c) {
            // Thinking is now set per request (see newRequestParameters). returnThinking stays
            // build-time (langchain4j has no per-request setter) and is always on so thinking is
            // parsed whenever a per-agent think value enables it.
            var builder = OllamaStreamingChatModel.builder()
                    .timeout(c.getTimeout())
                    .baseUrl(c.getUrl())
                    .modelName(c.getModel())
                    .returnThinking(Boolean.TRUE)
                    .customHeaders(c.getHeaderParams())
                    .logRequests(c.isDebugMode())
                    .logResponses(c.isDebugMode());
            if (c.getMaxTokens() > 0) builder.numPredict(c.getMaxTokens());
            return builder.build();
        }

        @Override
        public ChatRequestParameters newRequestParameters(AgentConfig mc, List<ToolSpecification> tools) {
            var b = applyBase(OllamaChatRequestParameters.builder(), mc, tools);
            // empty -> omit; explicit off-token -> think:false; else think:true
            var think = ThinkResolver.toOllamaThink(mc.getThink());
            if (think != null) b.think(think);
            return b.build();
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
            var builder = OpenAiStreamingChatModel.builder()
                    .timeout(c.getTimeout())
                    .baseUrl(c.getUrl())
                    .modelName(c.getModel())
                    .apiKey(c.getApiKey())
                    .strictJsonSchema(true)
                    .returnThinking(c.shouldReturnThinking())
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
            var b = applyBase(OpenAiChatRequestParameters.builder(), mc, tools);
            var effort = effortFor(mc);
            if (effort != null) b.reasoningEffort(effort);
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
    },

    OPEN_AI_OFFICIAL {
        @Override
        StreamingChatModel buildModel(LlmConfig c) {
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
            return openAiOfficialParameters(mc, tools);
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
            var builder = OpenAiStreamingChatModel.builder()
                    .timeout(c.getTimeout())
                    .baseUrl(c.getUrl())
                    .modelName(c.getModel())
                    .apiKey(StringUtil.hasValue(c.getApiKey()) ? c.getApiKey() : "lm-studio")
                    .httpClientBuilder(http1)
                    .returnThinking(c.shouldReturnThinking())
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
            var b = applyBase(OpenAiChatRequestParameters.builder(), mc, tools);
            // LM Studio uses a custom body property "reasoning" with on/off (experimental —
            // not officially supported yet). Empty -> omit; explicit off-token -> "off"; else "on".
            var reasoning = ThinkResolver.toReasoning(mc.getThink());
            if (reasoning != null) b.customParameters(Map.of("reasoning", reasoning));
            return b.build();
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
            // TODO per-agent think: Gemini has no per-request thinking parameter subtype in this
            // langchain4j version, so thinking stays build-time via the global thinkingEnabled toggle.
            if (c.isThinkingOn()) {
                var think = GeminiThinkingConfig.builder()
                    .thinkingLevel(GeminiThinkingLevel.HIGH)
                    .thinkingBudget(c.getMaxTokens() > 0 ? c.getMaxTokens() / 2 : 2048);
                result.thinkingConfig(think.build());
            } else {
                result.thinkingConfig(GeminiThinkingConfig.builder()
                        .thinkingBudget(0)
                        .build());
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
    },

    MISTRAL {
        private static final String MODELS_URL = "https://api.mistral.ai/v1/models";

        @Override
        StreamingChatModel buildModel(LlmConfig c) {
            // TODO per-agent think: Mistral has no per-request thinking parameter subtype in this
            // langchain4j version, so thinking stays build-time via the global thinkingEnabled toggle.
            var builder = MistralAiStreamingChatModel.builder()
                    .timeout(c.getTimeout())
                    .modelName(c.getModel())
                    .apiKey(c.getApiKey())
                    .returnThinking(c.shouldReturnThinking())
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
                    .timeout(c.getTimeout())
                    .modelName(c.getModel())
                    .apiKey(c.getApiKey());
            if (c.getUrl() != null && c.getUrl().length() > 4) {
                builder.baseUrl(c.getUrl());
            }
            if (c.getMaxTokens() > 0) {
                builder.maxTokens(c.getMaxTokens());
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
            var b = applyBase(AnthropicChatRequestParameters.builder(), mc, tools);
            var type = anthropicThinkingType(mc);
            if (type != null) {
                if ("adaptive".equals(type)) {
                    b.thinkingType("adaptive");
                } else {
                    b.thinkingType(type).thinkingBudgetTokens(2048);
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
            return openAiOfficialParameters(mc, tools);
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
            var b = applyBase(OpenAiChatRequestParameters.builder(), mc, tools);
            var effort = effortFor(mc);
            if (effort != null) b.reasoningEffort(effort);
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

            return SharedHttpClient.cancelAndGet(request, AiModelParser::parseCopilotApiModels);
        }
    };

    // Streaming only needs to cover time-to-first-token (connect + model warmup), not the full response duration.
    private static final Duration MODEL_TIMEOUT = SharedHttpClient.MODEL_TIMEOUT;

    // --- Public API ---

    /** Builds the {@link StreamingChatModel} for this provider using the given config. */
    abstract StreamingChatModel buildModel(LlmConfig config);

    /**
     * Builds the per-request {@link ChatRequestParameters} for this provider from a {@link AgentConfig}.
     * This is where the per-agent {@code think} value becomes a real request parameter.
     *
     * <p>Default: only the neutral fields (modelName, temperature, toolSpecifications) — no thinking.
     * Providers whose langchain4j version supports per-request thinking override this. Gemini and
     * Mistral have no per-request thinking parameter subtype, so they use this default and keep
     * thinking build-time (see the TODOs in their {@code buildModel}).</p>
     */
    public ChatRequestParameters newRequestParameters(AgentConfig mc, List<ToolSpecification> tools) {
        var b = ChatRequestParameters.builder()
                .modelName(mc.getModel())
                .temperature(mc.getTemperature());
        if (tools != null && !tools.isEmpty()) b.toolSpecifications(tools);
        return b.build();
    }

    /** Shared reasoning-effort parameters for the OpenAI-official-based providers. */
    private static ChatRequestParameters openAiOfficialParameters(AgentConfig mc, List<ToolSpecification> tools) {
        var b = applyBase(OpenAiOfficialResponsesChatRequestParameters.builder(), mc, tools);
        var effort = effortFor(mc);
        if (effort != null) {
            b.reasoningEffort(ReasoningEffort.of(effort)).reasoningSummary(Reasoning.Summary.DETAILED);
        }
        return b.build();
    }

    /** Applies the neutral request fields (modelName, temperature, tools) onto any provider builder. */
    private static <T extends DefaultChatRequestParameters.Builder<T>> T applyBase(
            T b, AgentConfig mc, List<ToolSpecification> tools) {
        b.modelName(mc.getModel()).temperature(mc.getTemperature());
        if (tools != null && !tools.isEmpty()) b.toolSpecifications(tools);
        return b;
    }

    /**
     * OpenAI-family {@code reasoning.effort} for the agent's think value (3-stage schema):
     * off -&gt; {@code null} (send nothing); a concrete level -&gt; used verbatim; a generic on
     * ({@code true}/{@code on}) -&gt; the {@link ThinkModelMapping} for the OpenAI family and this
     * model (no known reasoning model -&gt; {@code null}, send nothing).
     */
    private static String effortFor(AgentConfig mc) {
        var think = mc.getThink();
        if (ThinkResolver.isOff(think)) return null;
        if (ThinkResolver.isGenericOn(think)) return ThinkModelMapping.resolveOn(OPEN_AI, mc.getModel());
        return ThinkResolver.toReasoningEffort(think);
    }

    /**
     * Anthropic {@code thinkingType} for the agent's think value (3-stage schema):
     * off -&gt; {@code null}; a concrete value ({@code adaptive}/{@code enabled}) -&gt; used verbatim;
     * a generic on -&gt; the {@link ThinkModelMapping} for Anthropic and this model (no match -&gt;
     * {@code null}, send nothing).
     */
    private static String anthropicThinkingType(AgentConfig mc) {
        var think = mc.getThink();
        if (ThinkResolver.isOff(think)) return null;
        if (ThinkResolver.isGenericOn(think)) return ThinkModelMapping.resolveOn(ANTHROPIC, mc.getModel());
        return think.trim().toLowerCase();
    }

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
