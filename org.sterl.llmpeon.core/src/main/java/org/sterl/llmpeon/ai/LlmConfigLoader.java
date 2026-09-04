package org.sterl.llmpeon.ai;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.sterl.llmpeon.shared.StringUtil;

/**
 * Rebuilds a complete {@link LlmConfig} from a {@link LlmConfigStore} — the base config plus the
 * per-agent {@link AgentModelConfig} records. This is a clean rebuild: only the known keys are read,
 * unknown or historic keys (e.g. {@code llm.planModel}, {@code llm.thinkOnString}) are ignored — no
 * migration chain.
 *
 * <p>Missing base keys fall back to the {@link LlmConfig} defaults; missing per-agent fields fall
 * back to {@code null} (inherit base / provider default). Typed parsing (long/int/boolean and CSV
 * maps) happens here, keeping the {@link LlmConfigStore} contract string-only.</p>
 */
public final class LlmConfigLoader {

    private LlmConfigLoader() {
    }

    public static LlmConfig load(LlmConfigStore store) {
        return LlmConfig.builder()
                .providerType(AiProvider.parse(store.get(LlmConfigKeys.PROVIDER_TYPE, AiProvider.OLLAMA.name())))
                .model(store.get(LlmConfigKeys.MODEL, null))
                .url(store.get(LlmConfigKeys.URL, null))
                .apiKey(store.get(LlmConfigKeys.API_KEY, null))
                .timeout(Duration.ofSeconds(parseLong(store.get(LlmConfigKeys.TIMEOUT, null), 180)))
                .maxTokens(parseInt(store.get(LlmConfigKeys.MAX_TOKENS, null), 0))
                .autoCompactAfter(parseInt(store.get(LlmConfigKeys.TOKEN_WINDOW, null), 80_000))
                .thinkSupported(parseBoolean(store.get(LlmConfigKeys.THINK_SUPPORTED, null), false))
                .sendThinkingEnabled(parseBoolean(store.get(LlmConfigKeys.SEND_THINKING_ENABLED, null), true))
                .configDir(Path.of(store.get(LlmConfigKeys.CONFIG_DIRECTORY, defaultConfigDir())))
                .diskToolsEnabled(parseBoolean(store.get(LlmConfigKeys.DISK_TOOLS_ENABLED, null), false))
                .debugMode(parseBoolean(store.get(LlmConfigKeys.LOG_RESPONSE, null), false))
                .showRealtimeAiResponse(parseBoolean(store.get(LlmConfigKeys.SHOW_REALTIME_AI_RESPONSE, null), true))
                .queryParams(parseCsvMap(store.get(LlmConfigKeys.QUERY_PARAMS, "")))
                .headerParams(parseCsvMap(store.get(LlmConfigKeys.HEADER_PARAMS, "")))
                .shellCommandConfirmationRequired(shellConfirmationRequired(store.get(LlmConfigKeys.SHELL_CONFIRMATION_ENABLED, "")))
                .modelConfigs(loadModelConfigs(store))
                .build();
    }

    /** The per-agent records: dev's model is the base {@code llm.model}; the others read their own model key. */
    private static Map<String, AgentModelConfig> loadModelConfigs(LlmConfigStore store) {
        var baseModel = store.get(LlmConfigKeys.MODEL, null);
        var map = new LinkedHashMap<String, AgentModelConfig>();
        for (var id : AgentModelConfig.CORE_IDS) {
            String model = AgentModelConfig.DEV.equals(id) ? baseModel
                    : StringUtil.stripToNull(store.get(LlmConfigKeys.agentKey(id, LlmConfigKeys.AGENT_FIELD_MODEL), null));
            map.put(id, agentRecord(store, id, model));
        }
        return Map.copyOf(map);
    }

    private static AgentModelConfig agentRecord(LlmConfigStore store, String id, String model) {
        return new AgentModelConfig(
                StringUtil.stripToNull(store.get(LlmConfigKeys.agentKey(id, LlmConfigKeys.AGENT_FIELD_URL), null)),
                StringUtil.stripToNull(store.get(LlmConfigKeys.agentKey(id, LlmConfigKeys.AGENT_FIELD_API_KEY), null)),
                model,
                StringUtil.stripToNull(store.get(LlmConfigKeys.agentKey(id, LlmConfigKeys.AGENT_FIELD_THINK), null)),
                StringUtil.stripToNull(store.get(LlmConfigKeys.agentKey(id, LlmConfigKeys.AGENT_FIELD_EXTRA_BODY), null)),
                StringUtil.stripToNull(store.get(LlmConfigKeys.agentKey(id, LlmConfigKeys.AGENT_FIELD_TEMPERATURE), null)));
    }

    private static String defaultConfigDir() {
        return Path.of(System.getProperty("user.home"), ".peon").toString();
    }

    private static boolean shellConfirmationRequired(String value) {
        return "always".equals(value) || "not-autonomous".equals(value);
    }

    // --- Typed parsing (string store -> value, fallback on null/blank/invalid) ---

    static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.isBlank()) return fallback;
        return Boolean.parseBoolean(value.trim());
    }

    /** Parses a {@code k=v,k2=v2} CSV string into an ordered map; blank entries map to an empty value. */
    static Map<String, String> parseCsvMap(String csv) {
        if (StringUtil.hasNoValue(csv)) return Collections.emptyMap();
        var map = new LinkedHashMap<String, String>();
        for (var entry : csv.split(",")) {
            int idx = entry.indexOf('=');
            if (idx > 0) {
                map.put(entry.substring(0, idx).trim(), entry.substring(idx + 1).trim());
            } else if (!entry.trim().isEmpty()) {
                map.put(entry.trim(), "");
            }
        }
        return map;
    }
}
