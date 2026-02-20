package org.sterl.llmpeon.parts.config;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.parts.LlmPreferenceConstants;
import org.sterl.llmpeon.parts.tools.EclipseToolContext;
import org.sterl.llmpeon.shared.StringUtil;

public class LlmPreferenceInitializer extends AbstractPreferenceInitializer {
    @Override
    public void initializeDefaultPreferences() {
        IEclipsePreferences defaults = DefaultScope.INSTANCE.getNode(LlmPreferenceConstants.PLUGIN_ID);
        defaults.put(LlmPreferenceConstants.PREF_PROVIDER_TYPE, AiProvider.OLLAMA.name());
        defaults.put(LlmPreferenceConstants.PREF_MODEL, "devstral-small-2:24b");
        defaults.put(LlmPreferenceConstants.PREF_URL, "http://localhost:11434");
        defaults.putInt(LlmPreferenceConstants.PREF_TOKEN_WINDOW, 4000);
        defaults.putBoolean(LlmPreferenceConstants.PREF_THINKING_ENABLED, false);
        defaults.put(LlmPreferenceConstants.PREF_API_KEY, "");
        defaults.put(LlmPreferenceConstants.PREF_SKILL_DIRECTORY, "");
    }
    
    public static LlmConfig buildWithDefaults() {
        var prefs = InstanceScope.INSTANCE.getNode(LlmPreferenceConstants.PLUGIN_ID);
        
        var skillDir = prefs.get(LlmPreferenceConstants.PREF_SKILL_DIRECTORY, "");
        if (StringUtil.haValue(skillDir) && !Files.isDirectory(Path.of(skillDir))) {
            IResource dir = EclipseToolContext.resolveInEclipse(skillDir);
            if (dir != null) {
                // save Eclipse workspace-relative path to prefs (portable)
                prefs.put(LlmPreferenceConstants.PREF_SKILL_DIRECTORY, dir.getFullPath().toPortableString());
                // use absolute filesystem path for SkillService

                System.err.println("Resolved skill dir " + skillDir + " as " + dir.getRawLocation().toPortableString());
                skillDir = dir.getRawLocation().toPortableString();
            }
        } else {
            System.err.println(skillDir + " found at: " + Path.of(skillDir).toFile().getAbsolutePath());
        }
        
        var config = new LlmConfig(
            AiProvider.parse(prefs.get(LlmPreferenceConstants.PREF_PROVIDER_TYPE, AiProvider.OLLAMA.name())),
            prefs.get(LlmPreferenceConstants.PREF_MODEL, "devstral-small-2:24b"),
            prefs.get(LlmPreferenceConstants.PREF_URL, "http://localhost:11434"),
            prefs.getInt(LlmPreferenceConstants.PREF_TOKEN_WINDOW, 4096),
            prefs.getBoolean(LlmPreferenceConstants.PREF_THINKING_ENABLED, false),
            prefs.get(LlmPreferenceConstants.PREF_API_KEY, ""),
            skillDir
        );
        return config;
    }
}
