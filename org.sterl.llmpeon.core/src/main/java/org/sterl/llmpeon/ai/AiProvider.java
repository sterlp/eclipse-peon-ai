package org.sterl.llmpeon.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.ai.model.AiModelParser;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GeminiThinkingConfig.GeminiThinkingLevel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaModel;
import dev.langchain4j.model.ollama.OllamaModels;
import dev.langchain4j.model.openai.OpenAiChatModel;

public enum AiProvider {

    OLLAMA {
        @Override
        public ChatModel buildChatModel(LlmConfig c) {
            return OllamaChatModel.builder()
                    .timeout(TIMEOUT)
                    .baseUrl(c.getUrl())
                    .modelName(c.getModel())
                    .think(c.isThinkingEnabled())
                    .build();
        }

        @Override
        public List<AiModel> listAiModels(LlmConfig c) {
            try {
                var models = OllamaModels.builder()
                        .baseUrl(c.getUrl())
                        .build()
                        .availableModels()
                        .content();
                if (models == null || models.isEmpty()) return fallbackAiModels(c);
                var result = new ArrayList<AiModel>(models.size());
                for (OllamaModel m : models) {
                    result.add(AiModel.builder().id(m.getName()).name(m.getModel()).build());
                }
                Collections.sort(result, (a, b) -> a.getId().compareTo(b.getId()));
                return result;
            } catch (Exception e) {
                e.printStackTrace();
                return fallbackAiModels(c);
            }
        }
    },

    OPEN_AI {
        @Override
        public ChatModel buildChatModel(LlmConfig c) {
            var result = OpenAiChatModel.builder()
                    .timeout(TIMEOUT)
                    .baseUrl(c.getUrl())
                    .modelName(c.getModel())
                    .apiKey(c.getApiKey());
            // no thinking "off" supported
            result.returnThinking(Boolean.TRUE)
                  .sendThinking(Boolean.TRUE);
            return result.build();
        }
    },

    // model URL /api/v1/models
    LM_STUDIO {
        @Override
        protected HttpClient buildHttpClient() {
            return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        }

        @Override
        public ChatModel buildChatModel(LlmConfig c) {
            var http1 = JdkHttpClient.builder()
                    .httpClientBuilder(HttpClient.newBuilder()
                            .version(HttpClient.Version.HTTP_1_1));
            var result = OpenAiChatModel.builder()
                    .timeout(TIMEOUT)
                    .baseUrl(c.getUrl())
                    .modelName(c.getModel())
                    .strictJsonSchema(true)
                    .apiKey(StringUtil.hasValue(c.getApiKey()) ? c.getApiKey() : "lm-studio")
                    .httpClientBuilder(http1);
            // no thinking "off" supported
            result.returnThinking(Boolean.TRUE)
                  .sendThinking(Boolean.TRUE);
            return result.build();
        }

        @Override
        public List<AiModel> listAiModels(LlmConfig c) {
            if (c.getUrl() == null || c.getUrl().isBlank()) return fallbackAiModels(c);
            try {
                var url = c.getUrl().replace("/v1", "/api/v1");
                var request = HttpRequest.newBuilder()
                        .uri(URI.create(url + "/models"))
                        .GET()
                        .build();
                var response = cancelAndSend(request);
                if (response == null || response.statusCode() > 299) return fallbackAiModels(c);
                return AiModelParser.parseLmStudioModels(response.body());
            } catch (Exception e) {
                e.printStackTrace();
                return fallbackAiModels(c);
            }
        }
    },

    GOOGLE_GEMINI {
        @Override
        public ChatModel buildChatModel(LlmConfig c) {
            var result = GoogleAiGeminiChatModel.builder()
                    .apiKey(c.getApiKey())
                    .modelName(c.getModel());
            // returnThinking + sendThinking are always required: preview models return a
            // thought_signature even when thinking is "disabled". Without sendThinking(true),
            // the thought_signature is not re-sent with tool results -> INVALID_ARGUMENT error.
            result.returnThinking(Boolean.TRUE).sendThinking(Boolean.TRUE);
            if (c.isThinkingEnabled()) {
                result.thinkingConfig(GeminiThinkingConfig.builder()
                        .thinkingLevel(GeminiThinkingLevel.MEDIUM)
                        .build());
            } else {
                result.thinkingConfig(GeminiThinkingConfig.builder()
                        .thinkingBudget(0)
                        .build());
            }
            return result.build();
        }
    },

    MISTRAL {
        private static final String MODELS_URL = "https://api.mistral.ai/v1/models";

        @Override
        public ChatModel buildChatModel(LlmConfig c) {
            return MistralAiChatModel.builder()
                    .timeout(TIMEOUT)
                    .modelName(c.getModel())
                    .apiKey(c.getApiKey())
                    .build();
        }

        @Override
        public List<AiModel> listAiModels(LlmConfig c) {
            if (c.getApiKey() == null || c.getApiKey().isBlank()) return fallbackAiModels(c);
            try {
                var request = HttpRequest.newBuilder()
                        .uri(URI.create(MODELS_URL))
                        .header("X-API-Key", c.getApiKey())
                        .GET()
                        .build();
                var response = cancelAndSend(request);
                if (response == null || response.statusCode() > 299) return fallbackAiModels(c);
                return AiModelParser.parseMistralModels(response.body());
            } catch (Exception e) {
                e.printStackTrace();
                return fallbackAiModels(c);
            }
        }
    },

    ANTHROPIC {
        private static final String MODELS_URL = "https://api.anthropic.com/v1/models";
        private static final String ANTHROPIC_VERSION = "2023-06-01";

        @Override
        public ChatModel buildChatModel(LlmConfig c) {
            var innerBuilder = AnthropicChatModel.builder()
                    .timeout(TIMEOUT)
                    .modelName(c.getModel())
                    .apiKey(c.getApiKey());
            if (c.isThinkingEnabled()) {
                innerBuilder.thinkingType("enabled").thinkingBudgetTokens(16000);
            }
            final AnthropicChatModel inner = innerBuilder.build();
            final String modelName = c.getModel();
            final boolean thinking = c.isThinkingEnabled();

            // Workaround for langchain4j 1.12.x: AnthropicChatModel does not override
            // defaultRequestParameters(), so the model name is lost during ChatModel.chat()'s
            // merge step -> Anthropic rejects with "model: Field required".
            // Also: Anthropic requires temperature=1.0 when extended thinking is enabled,
            // and max_tokens is always required (8192 is safe across all current Claude models).
            return new ChatModel() {
                private final ChatRequestParameters defaults =
                        DefaultChatRequestParameters.builder()
                                .modelName(modelName)
                                .maxOutputTokens(8192)
                                .build();

                @Override
                public ChatResponse doChat(ChatRequest request) {
                    if (thinking) {
                        request = ChatRequest.builder()
                                .messages(request.messages())
                                .parameters(DefaultChatRequestParameters.builder()
                                        .overrideWith(request.parameters())
                                        .temperature(1.0)
                                        .build())
                                .build();
                    }
                    return inner.doChat(request);
                }

                @Override
                public ChatRequestParameters defaultRequestParameters() {
                    return defaults;
                }
            };
        }

        @Override
        public List<AiModel> listAiModels(LlmConfig c) {
            if (c.getApiKey() == null || c.getApiKey().isBlank()) return fallbackAiModels(c);
            try {
                var request = HttpRequest.newBuilder()
                        .uri(URI.create(MODELS_URL))
                        .header("x-api-key", c.getApiKey())
                        .header("anthropic-version", ANTHROPIC_VERSION)
                        .GET()
                        .build();
                var response = cancelAndSend(request);
                if (response == null || response.statusCode() > 299) return fallbackAiModels(c);
                return AiModelParser.parseAnthropicModels(response.body());
            } catch (Exception e) {
                e.printStackTrace();
                return fallbackAiModels(c);
            }
        }
    },

    GITHUB_COPILOT {
        private static final String DEFAULT_BASE_URL    = "https://api.githubcopilot.com";
        private static final String CATALOG_URL         = "https://models.github.ai/catalog/models";
        private static final String CATALOG_API_VERSION = "2026-03-10";

        private String baseUrl(LlmConfig c) {
            return (c.getUrl() != null && !c.getUrl().isBlank())
                    ? c.getUrl().replaceAll("/+$", "")
                    : DEFAULT_BASE_URL;
        }

        @Override
        public ChatModel buildChatModel(LlmConfig c) {
            return OpenAiChatModel.builder()
                    .timeout(TIMEOUT)
                    .baseUrl(baseUrl(c))
                    .apiKey(c.getApiKey() != null && !c.getApiKey().isBlank() ? c.getApiKey() : "not-configured")
                    .modelName(c.getModel())
                    .customHeaders(java.util.Map.of(
                            "Copilot-Integration-Id", "eclipse-peon-ai",
                            "Openai-Intent", "conversation-edits",
                            "x-initiator", "user"))
                    .build();
        }

        // https://docs.github.com/en/rest/models/catalog?apiVersion=2026-03-10#list-all-models
        @Override
        public List<AiModel> listAiModels(LlmConfig c) {
            if (c.getApiKey() == null || c.getApiKey().isBlank()) return List.of();
            try {
                var request = HttpRequest.newBuilder()
                        .uri(URI.create(CATALOG_URL))
                        .header("Authorization", "Bearer " + c.getApiKey())
                        .header("X-GitHub-Api-Version", CATALOG_API_VERSION)
                        .GET()
                        .build();
                var response = cancelAndSend(request);
                if (response == null || response.statusCode() > 299) {
                    System.err.println("GITHUB_COPILOT listAiModels HTTP "
                            + (response != null ? response.statusCode() : "null"));
                    return List.of();
                }
                return AiModelParser.parseCopilotModels(response.body());
            } catch (Exception e) {
                e.printStackTrace();
                return List.of();
            }
        }
    };

    private static final Duration TIMEOUT = Duration.ofMinutes(3);

    // --- Per-instance HTTP client (lazy, reused) ---

    private final AtomicReference<HttpClient> httpClient = new AtomicReference<HttpClient>(null);
    private final AtomicReference<CompletableFuture<HttpResponse<String>>> pendingList
            = new AtomicReference<>();

    protected HttpClient buildHttpClient() {
        return HttpClient.newHttpClient();
    }

    protected synchronized HttpClient getHttpClient() {
        if (httpClient.get() == null) httpClient.set(buildHttpClient());
        return httpClient.get();
    }

    /**
     * Sends the request asynchronously, cancelling any previously pending list request.
     * Returns null if the request was itself cancelled before completion.
     */
    protected HttpResponse<String> cancelAndSend(HttpRequest request) throws Exception {
        var future = getHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var prev = pendingList.getAndSet(future);
        if (prev != null) prev.cancel(true);
        try {
            return future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.CancellationException e) {
            return null;
        } finally {
            pendingList.compareAndSet(future, null);
        }
    }

    // --- Public API ---

    /** Builds the {@link ChatModel} for this provider using the given config. */
    public abstract ChatModel buildChatModel(LlmConfig config);

    /**
     * Returns a list of available {@link AiModel}s with metadata.
     * For providers that expose capability data (Copilot, LM Studio, Mistral), only
     * tool-callable models are returned. Other providers return all known models.
     * Default: wraps the configured model ID as a single-element list.
     */
    public List<AiModel> listAiModels(LlmConfig config) {
        return fallbackAiModels(config);
    }

    /**
     * Returns a sorted list of available model IDs.
     * Delegates to {@link #listAiModels(LlmConfig)} — override {@code listAiModels} instead.
     */
    public final List<String> listModels(LlmConfig config) {
        return listAiModels(config).stream().map(AiModel::getId).toList();
    }

    protected static List<String> fallback(LlmConfig config) {
        return StringUtil.hasValue(config.getModel())
                ? List.of(config.getModel())
                : List.of();
    }

    protected static List<AiModel> fallbackAiModels(LlmConfig config) {
        if (!StringUtil.hasValue(config.getModel())) return List.of();
        String id = config.getModel();
        return List.of(AiModel.builder().id(id).name(id).build());
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
