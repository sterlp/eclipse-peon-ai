package org.sterl.llmpeon.parts;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

public interface PeonConstants {
    String PLUGIN_ID             = "org.sterl.llmpeon";
    String PREF_PROVIDER_TYPE    = "llm.providerType";
    String PREF_MODEL            = "llm.model";
    String PREF_URL              = "llm.url";
    String PREF_TOKEN_WINDOW     = "llm.tokenWindow";
    String PREF_THINKING_ENABLED = "llm.thinkingEnabled";
    String PREF_API_KEY          = "llm.apiKey";
    String PREF_SKILL_DIRECTORY  = "llm.skillDirectory";
    
    
    public static IStatus okStatus(String message) {
        return new Status(IStatus.OK, PLUGIN_ID, message);
    }
    public static IStatus errorStatus(String message, Exception e) {
        return new Status(IStatus.ERROR, PLUGIN_ID, message, e);
    }
    
    public static IStatus status(String message, Exception e) {
        if (e == null) return okStatus(message);
        return errorStatus(message, e);
    }
}
