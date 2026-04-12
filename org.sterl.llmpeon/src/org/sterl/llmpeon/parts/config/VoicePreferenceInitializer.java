package org.sterl.llmpeon.parts.config;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.sterl.llmpeon.ai.voice.VoiceConfig;
import org.sterl.llmpeon.parts.PeonConstants;

public class VoicePreferenceInitializer extends AbstractPreferenceInitializer {

    @Override
    public void initializeDefaultPreferences() {
        IEclipsePreferences defaults = DefaultScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        defaults.putBoolean(PeonConstants.PREF_VOICE_ENABLED, false);
        defaults.put(PeonConstants.PREF_VOICE_MODEL, "voxtral-mini-latest");
        defaults.put(PeonConstants.PREF_VOICE_ENDPOINT, "/v1/audio/transcriptions");
        defaults.put(PeonConstants.PREF_VOICE_BASE_URL, "");
        defaults.put(PeonConstants.PREF_VOICE_API_KEY, "");
        defaults.put(PeonConstants.PREF_VOICE_LANGUAGE, "");
        defaults.put(PeonConstants.PREF_VOICE_MIXER, "");
    }

    public static VoiceConfig buildWithDefaults() {
        var prefs = InstanceScope.INSTANCE.getNode(PeonConstants.PLUGIN_ID);
        return new VoiceConfig(
            prefs.getBoolean(PeonConstants.PREF_VOICE_ENABLED, false),
            prefs.get(PeonConstants.PREF_VOICE_MODEL, "voxtral-mini-latest"),
            prefs.get(PeonConstants.PREF_VOICE_ENDPOINT, "/v1/audio/transcriptions"),
            prefs.get(PeonConstants.PREF_VOICE_BASE_URL, ""),
            prefs.get(PeonConstants.PREF_VOICE_API_KEY, ""),
            prefs.get(PeonConstants.PREF_VOICE_LANGUAGE, ""),
            prefs.get(PeonConstants.PREF_VOICE_MIXER, "")
        );
    }
}
