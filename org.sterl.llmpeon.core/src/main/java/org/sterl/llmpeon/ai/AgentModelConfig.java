package org.sterl.llmpeon.ai;

/**
 * Per-agent model configuration (cycle 2b). Carries the per-agent overrides for one agent:
 * its own {@code url}/{@code apiKey} (null/blank = inherit the base connection), its
 * {@code model} (null = provider default — except {@code dev}, which always uses the base model),
 * the {@code think} value (verbatim: {@code ""}=off, {@code "true"}=generic-on, a concrete level
 * passes through) and the raw extra JSON {@code extraBody} (null = none).
 *
 * <p>NULL/blank fields mean "not set". The agent IDs {@link #DEV}/{@link #PLAN}/{@link #SEARCH}/
 * {@link #COMPACT} are the stable vocabulary for the config keys
 * ({@code llm.agent.<id>.<field>}) and the {@link LlmConfig#getModelConfigs()} map.</p>
 */
public record AgentModelConfig(String url, String apiKey, String model, String think, String extraBody) {

    public static final String DEV     = "dev";
    public static final String PLAN    = "plan";
    public static final String SEARCH  = "search";
    public static final String COMPACT = "compact";

    /** A config with no field set (inherits everything from base / provider default). */
    public static AgentModelConfig empty() {
        return new AgentModelConfig(null, null, null, null, null);
    }

    /** A copy with the model replaced (null = provider default). */
    public AgentModelConfig withModel(String model) {
        return new AgentModelConfig(url, apiKey, model, think, extraBody);
    }

    @Override
    public String toString() {
        return "AgentModelConfig[url=%s, apiKey=***, model=%s, think=%s, extraBody=%s]"
                .formatted(url, model, think, extraBody);
    }
}
