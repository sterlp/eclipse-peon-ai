package org.sterl.llmpeon.parts.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.shared.StringUtil;

public class LlmPreferenceInitializer extends AbstractPreferenceInitializer {
    private static final ILog LOG = Platform.getLog(LlmPreferenceInitializer.class);

    private static final LlmConfig DEFAULT = LlmConfig.newOllama("qwen3.6-27b-i1");

    @Override
    public void initializeDefaultPreferences() {
        IEclipsePreferences defaults = DefaultScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        defaults.put(PeonConstants.PREF_PROVIDER_TYPE, DEFAULT.getProviderType().name());
        defaults.put(PeonConstants.PREF_MODEL, StringUtil.stripToEmpty(DEFAULT.getModel()));
        defaults.put(PeonConstants.PREF_URL, StringUtil.stripToEmpty(DEFAULT.getUrl()));
        defaults.putInt(PeonConstants.PREF_MAX_TOKENS, 0);
        defaults.putInt(PeonConstants.PREF_TOKEN_WINDOW, DEFAULT.getAutoCompactAfter());
        defaults.putBoolean(PeonConstants.PREF_THINKING_ENABLED, DEFAULT.isThinkingEnabled());
        defaults.putBoolean(PeonConstants.PREF_SEND_THINKING_ENABLED, DEFAULT.isSendThinkingEnabled());
        defaults.put(PeonConstants.PREF_API_KEY, StringUtil.stripToEmpty(DEFAULT.getApiKey()));
        defaults.put(PeonConstants.PREF_SKILL_DIRECTORY, Path.of(System.getProperty("user.home"), ".claude", "skills").toString());
        defaults.put(PeonConstants.PREF_COMMAND_DIRECTORY, Path.of(System.getProperty("user.home"), ".claude", "commands").toString());
        defaults.putBoolean(PeonConstants.PREF_DISK_TOOLS_ENABLED, false);
        defaults.put(PeonConstants.PREF_SHELL_CONFIRMATION_ENABLED, "");
        defaults.put(PeonConstants.PREF_PLAN_TEMPERATURE, String.valueOf(DEFAULT.getPlanTemperature()));
        defaults.put(PeonConstants.PREF_DEV_TEMPERATURE, String.valueOf(DEFAULT.getDevTemperature()));
        defaults.put(PeonConstants.PREF_QUERY_PARAMS, "");
        defaults.put(PeonConstants.PREF_HEADER_PARAMS, "");
        defaults.putBoolean(PeonConstants.PREF_AGENTS_MD_ENABLED, true);
    }
    
    public static LlmConfig buildWithDefaults() {
        var prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);

        var skillDir = resolveDirPreference(prefs, PeonConstants.PREF_SKILL_DIRECTORY, "skill");
        var commandDir = resolveDirPreference(prefs, PeonConstants.PREF_COMMAND_DIRECTORY, "command");

        return LlmConfig.builder()
            .providerType(AiProvider.parse(prefs.get(PeonConstants.PREF_PROVIDER_TYPE, DEFAULT.getProviderType().name())))
            .model(prefs.get(PeonConstants.PREF_MODEL, DEFAULT.getModel()))
            .url(prefs.get(PeonConstants.PREF_URL, DEFAULT.getUrl()))
            .autoCompactAfter(prefs.getInt(PeonConstants.PREF_TOKEN_WINDOW, DEFAULT.getAutoCompactAfter()))
            .maxTokens(prefs.getInt(PeonConstants.PREF_MAX_TOKENS, 0))
            .thinkingEnabled(prefs.getBoolean(PeonConstants.PREF_THINKING_ENABLED, DEFAULT.isThinkingEnabled()))
            .sendThinkingEnabled(prefs.getBoolean(PeonConstants.PREF_SEND_THINKING_ENABLED, DEFAULT.isSendThinkingEnabled()))
            .apiKey(prefs.get(PeonConstants.PREF_API_KEY, ""))
            .skillDirectory(skillDir)
            .commandDirectory(commandDir)
            .diskToolsEnabled(prefs.getBoolean(PeonConstants.PREF_DISK_TOOLS_ENABLED, false))
            .planTemperature(parseDoublePref(prefs, PeonConstants.PREF_PLAN_TEMPERATURE, DEFAULT.getPlanTemperature()))
            .devTemperature(parseDoublePref(prefs, PeonConstants.PREF_DEV_TEMPERATURE, DEFAULT.getDevTemperature()))
            .debugMode(prefs.getBoolean(PeonConstants.PREF_LOG_RESPONSE, false))
            .queryParams(parseCsvMap(prefs.get(PeonConstants.PREF_QUERY_PARAMS, "")))
            .headerParams(parseCsvMap(prefs.get(PeonConstants.PREF_HEADER_PARAMS, "")))
            .shellCommandConfirmationRequired("always".equals(prefs.get(PeonConstants.PREF_SHELL_CONFIRMATION_ENABLED, "")) ||
                    "not-autonomous".equals(prefs.get(PeonConstants.PREF_SHELL_CONFIRMATION_ENABLED, "")))
            .build();
    }

    /**
     * Returns an absolute file system path for the directory preference. If the stored value is
     * not already an absolute directory, attempts to resolve it as an Eclipse workspace-relative
     * resource and rewrites the preference to the portable workspace path on success.
     */
    private static String resolveDirPreference(org.eclipse.core.runtime.preferences.IEclipsePreferences prefs,
            String key, String label) {
        var dirValue = prefs.get(key, "");
        if (StringUtil.hasValue(dirValue) && !Files.isDirectory(Path.of(dirValue))) {
            var dir = EclipseUtil.resolveInEclipse(dirValue);
            if (dir.isPresent()) {
                var resource = dir.get();
                prefs.put(key, JdtUtil.diskPathOf(resource));
                var abs = JdtUtil.diskPathOf(resource);
                if (abs != null) {
                    LOG.info("Resolved " + label + " dir " + dirValue + " as " + abs);
                    dirValue = abs;
                } else {
                    LOG.warn("Could not resolve " + label + " dir to a filesystem path for " + resource.getFullPath());
                    dirValue = "";
                }
            }
        }
        return dirValue;
    }
    
    public static void setModel(String model) {
        if (model == null) return;
        try {
            var prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
            prefs.put(PeonConstants.PREF_MODEL, model);
            prefs.flush();
        } catch (Exception e) {
            LOG.warn("Failed to save model preference", e);
        }
    }

    public static void saveGitHubOAuthToken(String token, String enterpriseUrl) {
        try {
            IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
            prefs.put(PeonConstants.PREF_API_KEY, token);
            prefs.put(PeonConstants.PREF_PROVIDER_TYPE, AiProvider.GITHUB_COPILOT.name());
            if (enterpriseUrl != null && !enterpriseUrl.isBlank()) {
                // Store as copilot-api.{domain} so LlmConfig.url is the ready-to-use base URL
                String normalized = enterpriseUrl.replaceAll("^https?://", "").replaceAll("/+$", "");
                prefs.put(PeonConstants.PREF_URL, "https://copilot-api." + normalized);
            } else {
                prefs.put(PeonConstants.PREF_URL, "");
            }
            prefs.flush();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save GitHub OAuth token", e);
        }
    }

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

    static String toCsvString(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "";
        return map.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(java.util.stream.Collectors.joining(","));
    }

    static double parseDoublePref(IEclipsePreferences prefs, String key, double fallback) {
        String val = prefs.get(key, null);
        if (val == null || val.isBlank()) return fallback;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
