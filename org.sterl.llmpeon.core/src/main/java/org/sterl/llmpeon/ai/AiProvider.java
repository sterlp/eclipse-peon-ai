package org.sterl.llmpeon.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.sterl.llmpeon.shared.StringUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GeminiThinkingConfig.GeminiThinkingLevel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.mistralai.MistralAiModels;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaModels;
import dev.langchain4j.model.openai.OpenAiChatModel;

public enum AiProvider {

    OLLAMA {
        @Override
        public ChatModel buildChatModel(LlmConfig c) {
            return OllamaChatModel.builder()
                    .timeout(TIMEOUT)
                    .baseUrl(c.url())
                    .modelName(c.model())
                    .think(c.thinkingEnabled())
                    .build();
        }

        @Override
        public List<String> listModels(LlmConfig c) {
            try {
                var models = OllamaModels.builder()
                        .baseUrl(c.url())
                        .build()
                        .availableModels()
                        .content();
                if (models == null || models.isEmpty()) return fallback(c);
                var names = new ArrayList<String>(models.size());
                for (var m : models) names.add(m.getName());
                Collections.sort(names);
                return names;
            } catch (Exception e) {
                e.printStackTrace();
                return fallback(c);
            }
        }
    },

    OPEN_AI {
        @Override
        public ChatModel buildChatModel(LlmConfig c) {
            return OpenAiChatModel.builder()
                    .timeout(TIMEOUT)
                    .baseUrl(c.url())
                    .modelName(c.model())
                    .apiKey(c.apiKey())
                    .build();
        }
    },

    LM_STUDIO {
        @Override
        public ChatModel buildChatModel(LlmConfig c) {
            var http1 = JdkHttpClient.builder()
                    .httpClientBuilder(HttpClient.newBuilder()
                            .version(HttpClient.Version.HTTP_1_1));
            return OpenAiChatModel.builder()
                    .timeout(TIMEOUT)
                    .baseUrl(c.url())
                    .modelName(c.model())
                    .apiKey(c.apiKey() != null && !c.apiKey().isBlank() ? c.apiKey() : "lm-studio")
                    .httpClientBuilder(http1)
                    .build();
        }

        @Override
        public List<String> listModels(LlmConfig c) {
            if (c.url() == null || c.url().isBlank()) return fallback(c);
            try {
                var modelsUrl = c.url() + "/models";
                var http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
                var request = HttpRequest.newBuilder()
                        .uri(URI.create(modelsUrl))
                        .GET()
                        .build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() > 299) return fallback(c);
                return extractedModels(c, response);
            } catch (Exception e) {
                e.printStackTrace();
                return fallback(c);
            }
        }
    },

    GOOGLE_GEMINI {
        @Override
        public ChatModel buildChatModel(LlmConfig c) {
            var result = GoogleAiGeminiChatModel.builder()
                    .apiKey(c.apiKey())
                    .modelName(c.model());

            // returnThinking + sendThinking are always required: preview models return a
            // thought_signature even when thinking is "disabled". Without sendThinking(true),
            // the thought_signature is not re-sent with tool results -> INVALID_ARGUMENT error.
            result.returnThinking(Boolean.TRUE).sendThinking(Boolean.TRUE);

            if (c.thinkingEnabled()) {
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
        @Override
        public ChatModel buildChatModel(LlmConfig c) {
            return MistralAiChatModel.builder()
                    .timeout(TIMEOUT)
                    .modelName(c.model())
                    .apiKey(c.apiKey())
                    .build();
        }

        @Override
        public List<String> listModels(LlmConfig c) {
            try {
                var models = MistralAiModels.builder()
                        .apiKey(c.apiKey())
                        .build()
                        .availableModels()
                        .content();
                if (models == null || models.isEmpty()) return fallback(c);
                var ids = new ArrayList<String>(models.size());
                for (var m : models) ids.add(m.getId());
                Collections.sort(ids);
                return ids;
            } catch (Exception e) {
                e.printStackTrace();
                return fallback(c);
            }
        }
    },

    GITHUB_COPILOT {
        private static final String MODELS_URL = "https://api.githubcopilot.com/models";

        @Override
        public ChatModel buildChatModel(LlmConfig c) {
            // api.githubcopilot.com accepts the GitHub OAuth token directly as Bearer.
            return OpenAiChatModel.builder()
                    .timeout(TIMEOUT)
                    .baseUrl("https://api.githubcopilot.com")
                    .apiKey(c.apiKey() != null && !c.apiKey().isBlank() ? c.apiKey() : "not-configured")
                    .modelName(c.model())
                    .customHeaders(java.util.Map.of("Copilot-Integration-Id", "eclipse-peon-ai"))
                    .build();
        }

        @Override
        public List<String> listModels(LlmConfig c) {
            if (c.apiKey() == null || c.apiKey().isBlank()) return fallback(c);
            try {
                var http = HttpClient.newHttpClient();
                var request = HttpRequest.newBuilder()
                        .uri(URI.create(MODELS_URL))
                        .header("Authorization", "Bearer " + c.apiKey())
                        .header("Copilot-Integration-Id", "eclipse-peon-ai")
                        .GET()
                        .build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() > 299) return fallback(c);

                return extractedModels(c, response);
            } catch (Exception e) {
                e.printStackTrace();
                return fallback(c);
            }
        }
    };
    
    private static final Duration TIMEOUT = Duration.ofMinutes(3);

    /** Builds the {@link ChatModel} for this provider using the given config. */
    public abstract ChatModel buildChatModel(LlmConfig config);

    /**
     * Returns a sorted list of available model names/IDs.
     * Default: single-element list with {@code config.model()} if set, empty list otherwise.
     * Providers that support model discovery override this.
     */
    public List<String> listModels(LlmConfig config) {
        return fallback(config);
    }

    protected static List<String> fallback(LlmConfig config) {
        return StringUtil.hasValue(config.model())
                ? List.of(config.model())
                : List.of();
    }

    public static AiProvider parse(String string) {
        try {
            return AiProvider.valueOf(string);
        } catch (Exception e) {
            System.err.println("AiProvider: unknown " + string + " using " + OLLAMA);
            return OLLAMA;
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private static ArrayList<String> extractedModels(LlmConfig config, HttpResponse<String> response)
            throws JsonProcessingException, JsonMappingException {
        var json = MAPPER.readTree(response.body());
        var data = json.get("data");
        var ids = new ArrayList<String>();

        if (data != null && data.isArray()) {
            for (var item : data) {
                var id = item.get("id");
                if (id != null) ids.add(id.asText());
            }
        }

        if (ids.isEmpty() && StringUtil.hasValue(config.model())) ids.add(config.model());
        else Collections.sort(ids);
        return ids;
    }
}
