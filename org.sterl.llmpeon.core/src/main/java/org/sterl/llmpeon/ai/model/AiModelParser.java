package org.sterl.llmpeon.ai.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sterl.llmpeon.shared.StringUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AiModelParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Boolean-valued capability keys found in object-style capability blocks
     * (LM Studio, Mistral). Maps each known key to its AiCapability value.
     */
    private static final Map<String, AiCapability> CAPABILITY_OBJECT_KEYS = Map.of(
        "trained_for_tool_use", AiCapability.TOOL_CALLING,  // LM Studio
        "function_calling",     AiCapability.TOOL_CALLING,  // Mistral
        "vision",               AiCapability.VISION,
        "reasoning",            AiCapability.REASONING
    );

    /**
     * Parses the GitHub Copilot catalog API response (flat JSON array).
     * Only returns models that support tool calling.
     */
    public static List<AiModel> parseGithubModels(String json) {
        try {
            var root = MAPPER.readTree(json);
            var result = new ArrayList<AiModel>();
            if (!root.isArray()) return result;

            for (var item : root) {
                var idNode = item.get("id");
                if (idNode == null) continue;

                String modelId = idNode.asText();
                int slash = modelId.indexOf('/');
                if (slash >= 0) modelId = modelId.substring(slash + 1);
                String name = getNodeString(item, "name", modelId);

                Integer maxInputTokens = null;
                var limits = item.get("limits");
                if (limits != null) {
                    maxInputTokens = getNodeNumber(limits, "max_input_tokens");
                }

                var model = AiModel.builder()
                        .id(modelId)
                        .name(StringUtil.formatModelName(name, maxInputTokens))
                        .maxInputTokens(maxInputTokens)
                        .capabilities(parseCapabilityArray(item.get("capabilities")))
                        .build();

                if (model.supportsToolCalling()) result.add(model);
            }

            Collections.sort(result, (a, b) -> a.getId().compareTo(b.getId()));
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    /**
     * Parses the GitHub Copilot subscriber API response ({ "data": [...] }).
     * Only returns models where model_picker_enabled=true, tool_calls=true,
     * and /chat/completions is listed in supported_endpoints (LangChain4j uses OpenAI-compatible calls).
     */
    public static List<AiModel> parseCopilotApiModels(String json) {
        try {
            var root = MAPPER.readTree(json);
            var data = root.get("data");
            var result = new ArrayList<AiModel>();
            if (data == null || !data.isArray()) return result;

            for (var item : data) {
                var pickerEnabled = item.get("model_picker_enabled");
                if (pickerEnabled == null || !pickerEnabled.asBoolean()) continue;

                // LangChain4j OpenAiChatModel only calls /chat/completions
                var endpoints = item.get("supported_endpoints");
                if (endpoints != null && endpoints.isArray()) {
                    boolean hasChatCompletions = false;
                    for (var ep : endpoints) {
                        if ("/chat/completions".equals(ep.asText())) { hasChatCompletions = true; break; }
                    }
                    if (!hasChatCompletions) continue;
                }

                var idNode = item.get("id");
                if (idNode == null) continue;
                String id = idNode.asText();
                String name = getNodeString(item, "name", id);

                // check policy
                var policy = item.get("policy");
                if (policy != null) {
                    var state = policy.get("state");
                    if (state != null) name += " (" + state.asText() + ")";
                }

                Integer maxInputTokens = null;
                boolean toolCalls = false;
                var caps = item.get("capabilities");
                if (caps != null) {
                    var limits = caps.get("limits");
                    if (limits != null) {
                        maxInputTokens = getNodeNumber(limits, "max_context_window_tokens");
                    }
                    var supports = caps.get("supports");
                    if (supports != null) {
                        var tc = supports.get("tool_calls");
                        toolCalls = tc != null && tc.asBoolean();
                    }
                }
                if (!toolCalls) continue;

                result.add(AiModel.builder()
                        .id(id)
                        .name(StringUtil.formatModelName(name, maxInputTokens))
                        .maxInputTokens(maxInputTokens)
                        .capabilities(Set.of(AiCapability.TOOL_CALLING))
                        .build());
            }

            Collections.sort(result, (a, b) -> a.getId().compareTo(b.getId()));
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    /**
     * Parses the OpenAI models API response ({ "data": [...] }).
     */
    public static List<AiModel> parseOpenApiModels(String json) {
        try {
            var root = MAPPER.readTree(json);
            var data = root.get("data");
            var result = new ArrayList<AiModel>();
            if (data == null || !data.isArray()) return result;

            for (var item : data) {
                var idNode = item.get("id");
                if (idNode == null) continue;
                String id = idNode.asText();

                result.add(AiModel.builder()
                        .id(id)
                        .name(id)
                        .build());
            }

            Collections.sort(result, (a, b) -> a.getId().compareTo(b.getId()));
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    /**
     * Parses the LM Studio models API response ({ "models": [...] }).
     * Only returns LLM models that support tool calling.
     */
    public static List<AiModel> parseLmStudioModels(String json) {
        try {
            var root = MAPPER.readTree(json);
            var modelsNode = root.get("models");
            var result = new ArrayList<AiModel>();
            if (modelsNode == null || !modelsNode.isArray()) return result;

            for (var item : modelsNode) {
                var typeNode = item.get("type");
                if (typeNode == null || !"llm".equals(typeNode.asText())) continue;

                var keyNode = item.get("key");
                if (keyNode == null) continue;
                String id = keyNode.asText();

                String name = getNodeString(item, "display_name", id);
                Integer maxInputTokens = getNodeNumber(item, "max_context_length");

                var model = AiModel.builder()
                        .id(id)
                        .name(StringUtil.formatModelName(name, maxInputTokens))
                        .maxInputTokens(maxInputTokens)
                        .capabilities(parseCapabilityObject(item.get("capabilities")))
                        .build();

                if (model.supportsToolCalling()) result.add(model);
            }

            Collections.sort(result, (a, b) -> a.getId().compareTo(b.getId()));
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    /**
     * Parses the Mistral AI models API response ({ "data": [...] }).
     * Only returns models where capabilities.function_calling == true.
     * Deduplicates by display name, preferring entries whose ID ends with "-latest".
     */
    public static List<AiModel> parseMistralModels(String json) {
        try {
            var root = MAPPER.readTree(json);
            var data = root.get("data");
            if (data == null || !data.isArray()) return List.of();

            // name → best model seen so far; "-latest" IDs take priority
            var byName = new LinkedHashMap<String, AiModel>();

            for (var item : data) {
                var idNode = item.get("id");
                if (idNode == null) continue;
                String id = idNode.asText();
                String name = getNodeString(item, "name", id);

                Integer maxInputTokens = getNodeNumber(item, "max_context_length");
                var model = AiModel.builder()
                        .id(id)
                        .name(StringUtil.formatModelName(name, maxInputTokens))
                        .maxInputTokens(maxInputTokens)
                        .capabilities(parseCapabilityObject(item.get("capabilities")))
                        .build();

                if (!model.supportsToolCalling()) continue;

                var existing = byName.get(name);
                if (existing == null || id.endsWith("-latest")) {
                    byName.put(name, model);
                }
            }

            var result = new ArrayList<>(byName.values());
            Collections.sort(result, (a, b) -> a.getId().compareTo(b.getId()));
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    /**
     * Parses the Anthropic models API response ({ "data": [...] }).
     * Anthropic does not expose per-model capability metadata; all returned
     * Claude models support tool use, so no filtering is applied.
     */
    public static List<AiModel> parseAnthropicModels(String json) {
        try {
            var root = MAPPER.readTree(json);
            var data = root.get("data");
            var result = new ArrayList<AiModel>();
            if (data == null || !data.isArray()) return result;

            for (var item : data) {
                var idNode = item.get("id");
                if (idNode == null) continue;
                String id = idNode.asText();

                String name = getNodeString(item, "display_name", id);
                Integer maxToken = getNodeNumber(item, "max_input_tokens");

                result.add(AiModel.builder()
                        .id(id)
                        .name(StringUtil.formatModelName(name, maxToken))
                        .maxInputTokens(maxToken)
                        .capabilities(Set.of(AiCapability.TOOL_CALLING))
                        .build());
            }

            Collections.sort(result, (a, b) -> a.getId().compareTo(b.getId()));
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }
    
    private static Integer getNodeNumber(JsonNode node, String path) {
        var result = getNodeString(node, path);
        if (result == null) return null;
        try {
            return Integer.parseInt(result);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private static String getNodeString(JsonNode node, String path, String defaultValue) {
        var result = getNodeString(node, path);
        if (StringUtil.hasValue(result)) return result;
        return defaultValue;
    }
    
    private static String getNodeString(JsonNode node, String path) {
        var result = node.get(path);
        if (result == null || result.isNull()) return null;
        return result.asText();
    }

    /** Parses a JSON array of capability strings (Copilot format). */
    private static Set<AiCapability> parseCapabilityArray(JsonNode node) {
        if (node == null || !node.isArray()) return Set.of();
        var caps = EnumSet.noneOf(AiCapability.class);
        for (var el : node) {
            var cap = AiCapability.parse(el.asText());
            if (cap != null) caps.add(cap);
        }
        return caps.isEmpty() ? Set.of() : Collections.unmodifiableSet(caps);
    }

    /**
     * Parses a JSON object of boolean capability flags (LM Studio, Mistral format).
     * Known keys are mapped via {@link #CAPABILITY_OBJECT_KEYS}.
     */
    private static Set<AiCapability> parseCapabilityObject(JsonNode node) {
        if (node == null || !node.isObject()) return Set.of();
        var caps = EnumSet.noneOf(AiCapability.class);
        CAPABILITY_OBJECT_KEYS.forEach((key, cap) -> {
            var field = node.get(key);
            if (field != null && field.asBoolean()) caps.add(cap);
        });
        return caps.isEmpty() ? Set.of() : Collections.unmodifiableSet(caps);
    }
}
