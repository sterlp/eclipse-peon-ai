package org.sterl.llmpeon.parts;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.sterl.llmpeon.ai.LlmConfigKeys;

public interface PeonConstants {
    String PLUGIN_ID             = "org.sterl.llmpeon";
    // LLM base keys — the canonical storage strings live in core (LlmConfigKeys); these are 1:1 aliases.
    String PREF_PROVIDER_TYPE    = LlmConfigKeys.PROVIDER_TYPE;
    String PREF_URL              = LlmConfigKeys.URL;
    String PREF_TOKEN_WINDOW     = LlmConfigKeys.TOKEN_WINDOW;
    String PREF_MAX_TOKENS       = LlmConfigKeys.MAX_TOKENS;
    String PREF_TIMEOUT          = LlmConfigKeys.TIMEOUT;

    String PREF_THINK_SUPPORTED         = LlmConfigKeys.THINK_SUPPORTED;
    String PREF_SEND_THINKING_ENABLED   = LlmConfigKeys.SEND_THINKING_ENABLED;

    String PREF_API_KEY                    = LlmConfigKeys.API_KEY;
    String PREF_CONFIG_DIRECTORY           = LlmConfigKeys.CONFIG_DIRECTORY;
    String PREF_DISK_TOOLS_ENABLED         = LlmConfigKeys.DISK_TOOLS_ENABLED;
    String PREF_SHELL_CONFIRMATION_ENABLED = LlmConfigKeys.SHELL_CONFIRMATION_ENABLED;

    String PREF_LOG_RESPONSE       = LlmConfigKeys.LOG_RESPONSE;
    String PREF_SHOW_REALTIME_AI_RESPONSE = LlmConfigKeys.SHOW_REALTIME_AI_RESPONSE;
    String PREF_QUERY_PARAMS       = LlmConfigKeys.QUERY_PARAMS;
    String PREF_HEADER_PARAMS      = LlmConfigKeys.HEADER_PARAMS;

    String PREF_MCP_SERVERS        = "mcp.servers";   // JSON: List<McpServerConfig>
    String PREF_MCP_ENABLED        = "mcp.enabled";   // boolean

    String PREF_VOICE_ENABLED  = "voice.enabled";   // boolean, default false
    String PREF_VOICE_MODEL    = "voice.model";      // e.g. "whisper-1", "whisper"
    String PREF_VOICE_ENDPOINT = "voice.endpoint";   // default "/v1/audio/transcriptions"
    String PREF_VOICE_BASE_URL = "voice.baseUrl";    // empty = use main provider URL
    String PREF_VOICE_API_KEY  = "voice.apiKey";     // empty = use main provider API key
    String PREF_VOICE_LANGUAGE = "voice.language";   // e.g. "en", "de" — empty = auto-detect
    String PREF_VOICE_MIXER    = "voice.mixer";       // mixer name — empty = system default


    String PREF_MODEL            = LlmConfigKeys.MODEL;

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
