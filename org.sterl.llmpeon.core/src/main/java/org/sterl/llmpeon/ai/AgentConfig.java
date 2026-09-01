package org.sterl.llmpeon.ai;

import java.util.List;

import org.sterl.llmpeon.provider.LlmProvider;
import org.sterl.llmpeon.provider.LlmProviders;

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
 * <p>{@code url} and {@code apiKey} are effective since cycle 2a: {@link EffectiveConnection}
 * resolves the per-agent connection (falling back to the base values) and
 * {@link ConfiguredChatModel} caches one model per effective identity. {@code extraBody} is applied
 * per request (OpenAI family) or at build time (Anthropic).</p>
 * a different endpoint. Today all agents still share the single built {@link ConfiguredChatModel},
 * so these two are <b>not yet applied per request</b> (see the TODO in {@link AiProvider}).</p>
 *
 * <p>{@link #newRequestParameters(List)} builds the provider-specific
 * {@link ChatRequestParameters} — this is the single place where the per-agent {@code think} value
 * becomes a real request parameter, delegating to
 * {@link LlmProvider#newRequestParameters(AgentConfig, List)} via {@link LlmProviders}.</p>
 */
@Builder(toBuilder = true)
@Getter
@ToString(exclude = {"apiKey", "extraBody"})
public class AgentConfig {

    private final AiProvider provider;
    /**
     * Stable agent identifier (e.g. {@code plan}, custom agent name) — per-request metadata only,
     * never part of the connection identity.
     */
    private final String id;
    private final String url;
    private final String apiKey;
    private final String model;
    /** {@code null}/empty/{@code false} = off; otherwise the reasoning effort / on value. */
    private final String think;
    private final Double temperature;
    /**
     * Raw extra JSON body merged into the request (advanced configuration); {@code null} = none.
     * Reserved top-level keys ({@code model}, {@code messages}, {@code tools}) are stripped at
     * parse time.
     */
    private final String extraBody;

    public ChatRequestParameters newRequestParameters(List<ToolSpecification> tools) {
        return LlmProviders.of(provider).newRequestParameters(this, tools);
    }
}
