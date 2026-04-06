package org.sterl.llmpeon.parts.config;

import java.nio.file.Files;
import java.nio.file.Path;

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
    @Override
    public void initializeDefaultPreferences() {
        IEclipsePreferences defaults = DefaultScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        defaults.put(PeonConstants.PREF_PROVIDER_TYPE, AiProvider.OLLAMA.name());
        defaults.put(PeonConstants.PREF_MODEL, "devstral-small-2:24b");
        defaults.put(PeonConstants.PREF_URL, "http://localhost:11434");
        defaults.putInt(PeonConstants.PREF_TOKEN_WINDOW, 4000);
        defaults.putBoolean(PeonConstants.PREF_THINKING_ENABLED, false);
        defaults.put(PeonConstants.PREF_API_KEY, "");
        defaults.put(PeonConstants.PREF_SKILL_DIRECTORY, "");
    }
    
    public static LlmConfig buildWithDefaults() {
        var prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);

        var skillDir = prefs.get(PeonConstants.PREF_SKILL_DIRECTORY, "");
        if (StringUtil.hasValue(skillDir) && !Files.isDirectory(Path.of(skillDir))) {
            var dir = EclipseUtil.resolveInEclipse(skillDir);
            if (dir.isPresent()) {
                // save Eclipse workspace-relative path to prefs (portable)
                prefs.put(PeonConstants.PREF_SKILL_DIRECTORY, dir.get().getFullPath().toPortableString());
                // use absolute filesystem path for SkillService
                System.err.println("Resolved skill dir " + skillDir + " as " + dir.get().getRawLocation().toPortableString());
                skillDir = dir.get().getRawLocation().toPortableString();
            }
        }

        var config = new LlmConfig(
            AiProvider.parse(prefs.get(PeonConstants.PREF_PROVIDER_TYPE, AiProvider.OLLAMA.name())),
            prefs.get(PeonConstants.PREF_MODEL, "devstral-small-2:24b"),
            prefs.get(PeonConstants.PREF_URL, "http://localhost:11434"),
            prefs.getInt(PeonConstants.PREF_TOKEN_WINDOW, 4000),
            prefs.getBoolean(PeonConstants.PREF_THINKING_ENABLED, false),
            prefs.get(PeonConstants.PREF_API_KEY, ""),
            skillDir
        );
        return config;
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
