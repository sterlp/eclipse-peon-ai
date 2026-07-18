package org.sterl.llmpeon.parts;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

public interface PeonConstants {
    String PLUGIN_ID             = "org.sterl.llmpeon";
    String PREF_PROVIDER_TYPE    = "llm.providerType";
    String PREF_URL              = "llm.url";
    String PREF_TOKEN_WINDOW     = "llm.tokenWindow";
    String PREF_MAX_TOKENS       = "llm.maxTokens";
    String PREF_TIMEOUT          = "llm.timeout";

    // Dev == GLOBAL == DEFAULT: thinkEnabled (boolean) + on/off strings. Kept historic enabled key.
    String PREF_THINKING_ENABLED        = "llm.thinkingEnabled";   // boolean
    String PREF_SEND_THINKING_ENABLED   = "llm.sendThinkingEnabled";
    String PREF_THINK_ON_STRING         = "llm.thinkOnString";
    String PREF_THINK_OFF_STRING        = "llm.thinkOffString";
    // Plan agent think.
    String PREF_PLAN_THINK_ENABLED      = "llm.planThinkEnabled";  // boolean
    String PREF_PLAN_THINK_ON_STRING    = "llm.planThinkOnString";
    String PREF_PLAN_THINK_OFF_STRING   = "llm.planThinkOffString";

    String PREF_API_KEY                    = "llm.apiKey";
    String PREF_CONFIG_DIRECTORY           = "llm.configDirectory";
    String PREF_DISK_TOOLS_ENABLED         = "llm.diskToolsEnabled";
    String PREF_SHELL_CONFIRMATION_ENABLED = "llm.shellConfirmationEnabled";
    
    String PREF_LOG_RESPONSE       = "llm.logResponse";
    String PREF_PLAN_TEMPERATURE   = "llm.planTemperature";
    String PREF_DEV_TEMPERATURE    = "llm.devTemperature";
    String PREF_QUERY_PARAMS       = "llm.queryParams";
    String PREF_HEADER_PARAMS      = "llm.headerParams";

    String PREF_MCP_SERVERS        = "mcp.servers";   // JSON: List<McpServerConfig>
    String PREF_MCP_ENABLED        = "mcp.enabled";   // boolean

    String PREF_VOICE_ENABLED  = "voice.enabled";   // boolean, default false
    String PREF_VOICE_MODEL    = "voice.model";      // e.g. "whisper-1", "whisper"
    String PREF_VOICE_ENDPOINT = "voice.endpoint";   // default "/v1/audio/transcriptions"
    String PREF_VOICE_BASE_URL = "voice.baseUrl";    // empty = use main provider URL
    String PREF_VOICE_API_KEY  = "voice.apiKey";     // empty = use main provider API key
    String PREF_VOICE_LANGUAGE = "voice.language";   // e.g. "en", "de" — empty = auto-detect
    String PREF_VOICE_MIXER    = "voice.mixer";       // mixer name — empty = system default


    String PREF_AGENTS_MD_ENABLED  = "agentsMd.enabled";   // boolean, default true

    String PREF_MODEL            = "llm.model";
    String PREF_PLAN_MODEL       = "llm.planModel";
    String PREF_SEARCH_MODEL     = "llm.searchModel";
    String PREF_COMPACT_MODEL    = "llm.compactModel";

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
