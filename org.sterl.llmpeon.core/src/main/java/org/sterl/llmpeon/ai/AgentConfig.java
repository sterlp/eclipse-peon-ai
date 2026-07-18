package org.sterl.llmpeon.ai;

import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Per-agent config. Carries everything an agent needs to talk to the LLM for a single request: the
 * {@link AiProvider}, the model name, the thinking/reasoning setting ({@code think}) and the
 * temperature.
 *
 * <p>{@code url} and {@code apiKey} are captured here for a later step where each agent may talk to
 * a different endpoint. Today all agents still share the single built {@link ConfiguredChatModel},
 * so these two are <b>not yet applied per request</b> (see the TODO in {@link AiProvider}).</p>
 *
 * <p>{@link #newRequestParameters(List)} builds the provider-specific
 * {@link ChatRequestParameters} — this is the single place where the per-agent {@code think} value
 * becomes a real request parameter, delegating to
 * {@link AiProvider#newRequestParameters(AgentConfig, List)}.</p>
 */
@Builder(toBuilder = true)
@Getter
@ToString(exclude = "apiKey")
public class AgentConfig {

    private final AiProvider provider;
    private final String url;
    private final String apiKey;
    private final String model;
    /** {@code null}/empty/{@code false} = off; otherwise the reasoning effort / on value. */
    private final String think;
    private final Double temperature;

    public ChatRequestParameters newRequestParameters(List<ToolSpecification> tools) {
        return provider.newRequestParameters(this, tools);
    }
}
