package org.sterl.llmpeon.ai;

/**
 * Canonical configuration keys for the LLM config — base keys plus the per-agent key scheme
 * ({@code llm.agent.<id>.<field>}). The plugin's {@code PeonConstants} LLM keys are 1:1 aliases of
 * these, so the storage format is defined exactly once, here in core.
 */
public final class LlmConfigKeys {

    private LlmConfigKeys() {
    }

    // --- Base keys (storage format unchanged from the historic preference keys) ---

    public static final String PROVIDER_TYPE            = "llm.providerType";
    public static final String MODEL                    = "llm.model";
    public static final String URL                      = "llm.url";
    public static final String API_KEY                  = "llm.apiKey";
    public static final String TIMEOUT                  = "llm.timeout";
    public static final String MAX_TOKENS               = "llm.maxTokens";
    public static final String TOKEN_WINDOW             = "llm.tokenWindow";
    public static final String THINK_SUPPORTED          = "llm.thinkingEnabled";
    public static final String SEND_THINKING_ENABLED    = "llm.sendThinkingEnabled";
    public static final String CONFIG_DIRECTORY         = "llm.configDirectory";
    public static final String DISK_TOOLS_ENABLED       = "llm.diskToolsEnabled";
    public static final String SHELL_CONFIRMATION_ENABLED = "llm.shellConfirmationEnabled";
    public static final String LOG_RESPONSE             = "llm.logResponse";
    public static final String SHOW_REALTIME_AI_RESPONSE = "llm.showRealtimeAiResponse";
    public static final String QUERY_PARAMS             = "llm.queryParams";
    public static final String HEADER_PARAMS            = "llm.headerParams";

    // --- Per-agent key scheme: llm.agent.<id>.<field> ---

    public static final String AGENT_FIELD_MODEL      = "model";
    public static final String AGENT_FIELD_URL        = "url";
    public static final String AGENT_FIELD_API_KEY    = "apiKey";
    public static final String AGENT_FIELD_THINK      = "think";
    public static final String AGENT_FIELD_EXTRA_BODY = "extraBody";
    public static final String AGENT_FIELD_TEMPERATURE = "temperature";

    /** Builds the key {@code llm.agent.<agentId>.<field>}. */
    public static String agentKey(String agentId, String field) {
        return "llm.agent." + agentId + "." + field;
    }
}
