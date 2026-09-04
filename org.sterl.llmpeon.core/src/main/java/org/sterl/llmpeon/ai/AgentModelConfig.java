package org.sterl.llmpeon.ai;

import java.util.List;

/**
 * Raw per-agent model configuration. {@link #DEV}, {@link #PO}, {@link #PLAN}, {@link #SEARCH}, and
 * {@link #COMPACT} are the stable slot identifiers used by {@code llm.agent.<id>.<field>} keys.
 * Null or blank fields are unset; temperature remains a string until agent configuration
 * resolution. The dev slot is special: its model is the base {@code llm.model}, not a per-agent
 * model key.
 */
public record AgentModelConfig(String url, String apiKey, String model, String think, String extraBody,
        String temperature) {

    public static final String DEV     = "dev";
    public static final String PO      = "po";
    public static final String PLAN    = "plan";
    public static final String SEARCH  = "search";
    public static final String COMPACT = "compact";

    public static final List<String> CORE_IDS = List.of(DEV, PO, PLAN, SEARCH, COMPACT);

    public static AgentModelConfig empty() {
        return new AgentModelConfig(null, null, null, null, null, null);
    }

    public AgentModelConfig withModel(String model) {
        return new AgentModelConfig(url, apiKey, model, think, extraBody, temperature);
    }

    @Override
    public String toString() {
        return "AgentModelConfig[url=%s, apiKey=***, model=%s, think=%s, extraBody=%s, temperature=%s]"
                .formatted(url, model, think, extraBody, temperature);
    }
}
