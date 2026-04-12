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
    String PREF_DISK_TOOLS_ENABLED = "llm.diskToolsEnabled";

    String PREF_MCP_SERVERS  = "mcp.servers";   // JSON: List<McpServerConfig>
    String PREF_MCP_ENABLED  = "mcp.enabled";   // boolean

    String PREF_VOICE_ENABLED  = "voice.enabled";   // boolean, default false
    String PREF_VOICE_MODEL    = "voice.model";      // e.g. "whisper-1", "whisper"
    String PREF_VOICE_ENDPOINT = "voice.endpoint";   // default "/v1/audio/transcriptions"
    String PREF_VOICE_BASE_URL = "voice.baseUrl";    // empty = use main provider URL
    String PREF_VOICE_LANGUAGE = "voice.language";   // e.g. "en", "de" — empty = auto-detect


    public static IStatus okStatus(String message) {
        return new Status(IStatus.OK, PLUGIN_ID, message);
    }
    public static IStatus errorStatus(String message, Throwable e) {
        var cause = e.getCause();
        return new Status(IStatus.ERROR, PLUGIN_ID, message, cause == null ? e : cause);
    }
    
    public static IStatus status(String message, Throwable e) {
        if (e == null) return okStatus(message);
        return errorStatus(message + " " + e.getMessage(), e);
    }
}
