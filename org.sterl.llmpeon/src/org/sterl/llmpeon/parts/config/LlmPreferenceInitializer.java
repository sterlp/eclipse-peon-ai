package org.sterl.llmpeon.parts.config;

import java.nio.file.Files;
import java.nio.file.Path;

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
import org.sterl.llmpeon.shared.StringUtil;

public class LlmPreferenceInitializer extends AbstractPreferenceInitializer {
    private static final ILog LOG = Platform.getLog(LlmPreferenceInitializer.class);
    
    private static final LlmConfig DEFAULT = LlmConfig.newOllama("gemma4:26b-a4b-it-q4_K_M");
            //LlmConfig.newLmStudio("qwen/qwen3.6-35b-a3b");

    @Override
    public void initializeDefaultPreferences() {
        IEclipsePreferences defaults = DefaultScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        defaults.put(PeonConstants.PREF_PROVIDER_TYPE, DEFAULT.getProviderType().name());
        defaults.put(PeonConstants.PREF_MODEL, StringUtil.stripToEmpty(DEFAULT.getModel()));
        defaults.put(PeonConstants.PREF_URL, StringUtil.stripToEmpty(DEFAULT.getUrl()));
        defaults.putInt(PeonConstants.PREF_TOKEN_WINDOW, DEFAULT.getTokenWindow());
        defaults.putBoolean(PeonConstants.PREF_THINKING_ENABLED, DEFAULT.isThinkingEnabled());
        defaults.putBoolean(PeonConstants.PREF_SEND_THINKING_ENABLED, DEFAULT.isSendThinkingEnabled());
        defaults.put(PeonConstants.PREF_API_KEY, StringUtil.stripToEmpty(DEFAULT.getApiKey()));
        defaults.put(PeonConstants.PREF_SKILL_DIRECTORY, StringUtil.stripToEmpty(DEFAULT.getSkillDirectory()));
        defaults.putBoolean(PeonConstants.PREF_DISK_TOOLS_ENABLED, false);
        defaults.put(PeonConstants.PREF_SHELL_CONFIRMATION_ENABLED, "");
    }
    
    public static LlmConfig buildWithDefaults() {
        var prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);

        var skillDir = prefs.get(PeonConstants.PREF_SKILL_DIRECTORY, "");
        if (StringUtil.hasValue(skillDir) && !Files.isDirectory(Path.of(skillDir))) {
            var dir = EclipseUtil.resolveInEclipse(skillDir);
            if (dir.isPresent()) {
                // save Eclipse workspace-relative path to prefs (portable)
                prefs.put(PeonConstants.PREF_SKILL_DIRECTORY, dir.get().getFullPath().toPortableString());
                // use absolute file system path for SkillService
                LOG.info("Resolved skill dir " + skillDir + " as " + dir.get().getRawLocation().toPortableString());
                skillDir = dir.get().getRawLocation().toPortableString();
            }
        }

        return LlmConfig.builder()
            .providerType(AiProvider.parse(prefs.get(PeonConstants.PREF_PROVIDER_TYPE, DEFAULT.getProviderType().name())))
            .model(prefs.get(PeonConstants.PREF_MODEL, DEFAULT.getModel()))
            .url(prefs.get(PeonConstants.PREF_URL, DEFAULT.getUrl()))
            .tokenWindow(prefs.getInt(PeonConstants.PREF_TOKEN_WINDOW, DEFAULT.getTokenWindow()))
            .thinkingEnabled(prefs.getBoolean(PeonConstants.PREF_THINKING_ENABLED, DEFAULT.isThinkingEnabled()))
            .sendThinkingEnabled(prefs.getBoolean(PeonConstants.PREF_SEND_THINKING_ENABLED, DEFAULT.isSendThinkingEnabled()))
            .apiKey(prefs.get(PeonConstants.PREF_API_KEY, ""))
            .skillDirectory(skillDir)
            .diskToolsEnabled(prefs.getBoolean(PeonConstants.PREF_DISK_TOOLS_ENABLED, false))
            .build();
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
}
