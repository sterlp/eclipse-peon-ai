package org.sterl.llmpeon.parts.config;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.sterl.llmpeon.parts.LlmPreferenceConstants;

public class LlmPreferenceInitializer extends AbstractPreferenceInitializer {
    @Override
    public void initializeDefaultPreferences() {
        IEclipsePreferences defaults = DefaultScope.INSTANCE.getNode(LlmPreferenceConstants.PLUGIN_ID);
        defaults.put(LlmPreferenceConstants.PREF_PROVIDER_TYPE, "ollama");
        defaults.put(LlmPreferenceConstants.PREF_MODEL, "devstral-small-2:24b");
        defaults.put(LlmPreferenceConstants.PREF_URL, "http://localhost:11434");
        defaults.putInt(LlmPreferenceConstants.PREF_TOKEN_WINDOW, 4096);
        defaults.putBoolean(LlmPreferenceConstants.PREF_THINKING_ENABLED, false);
        defaults.put(LlmPreferenceConstants.PREF_API_KEY, "");
    }
    
    public static LlmConfig buildWithDefaults() {
    	var prefs = InstanceScope.INSTANCE.getNode(LlmPreferenceConstants.PLUGIN_ID);
        var config = new LlmConfig(
            prefs.get(LlmPreferenceConstants.PREF_PROVIDER_TYPE, "ollama"),
            prefs.get(LlmPreferenceConstants.PREF_MODEL, "devstral-small-2:24b"),
            prefs.get(LlmPreferenceConstants.PREF_URL, "http://localhost:11434"),
            prefs.getInt(LlmPreferenceConstants.PREF_TOKEN_WINDOW, 4096),
            prefs.getBoolean(LlmPreferenceConstants.PREF_THINKING_ENABLED, false),
            prefs.get(LlmPreferenceConstants.PREF_API_KEY, "")
        );
        return config;
    }
}
