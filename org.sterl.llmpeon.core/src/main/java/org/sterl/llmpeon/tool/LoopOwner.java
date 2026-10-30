package org.sterl.llmpeon.tool;

import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.ai.AgentConfig;
import org.sterl.llmpeon.ai.LlmConfig;

/**
 * Who owns a {@link ToolLoopRequest}: the name stamped as {@code origin} on every line the loop emits
 * (docs/adr/0078-origin-in-core-owner-monitor.md), the agent a tool may delegate back to, and the model
 * config to call with. One source for all three, so a sub-loop cannot inherit a name that belongs to
 * its parent.
 *
 * <p>The config is a function of the live {@link LlmConfig}, read at CALL time — a preferences change
 * therefore takes effect on the next request of a running loop, and no owner can hold a stale snapshot.
 */
public interface LoopOwner {

    /**
     * Core id of the speaking agent, stamped as {@code origin}. {@code null} means <b>no owner</b>:
     * nothing is stamped and the lines stay unstamped — deliberately not a placeholder like
     * {@code "(none)"}, which a consumer would read as a real agent.
     */
    @Nullable String name();

    /** The agent that owns this loop; {@code null} for an agent-less sub-loop — then no back-delegation (R-CC-15). */
    @Nullable AiAgent agent();

    /** The loop's model config, read at call time from the live base config — never stored. */
    AgentConfig config(LlmConfig base);

    /** The agent owns the loop: its name is the stamp, its back-delegation target is itself. */
    static LoopOwner of(AiAgent agent, Function<LlmConfig, AgentConfig> config) {
        return new Of(agent.getName(), agent, config);
    }

    /**
     * A named, agent-less loop (e.g. the search sub-loop): the name is stamped, nothing can delegate
     * back. A {@code null} name is an unnamed loop — it brings only its own config and stamps nothing.
     */
    static LoopOwner named(@Nullable String name, Function<LlmConfig, AgentConfig> config) {
        return new Of(name, null, config);
    }

    /**
     * The bare-builder default: no name (so nothing is stamped) and the historic dev-slot config.
     * Test and stand-alone loops keep their behaviour without naming themselves.
     */
    LoopOwner NONE = named(null, LlmConfig::devAgentConfig);

    /** The single implementation — a name, an optional agent, and how to read the config live. */
    record Of(@Nullable String name, @Nullable AiAgent agent, Function<LlmConfig, AgentConfig> configFn)
            implements LoopOwner {
        @Override
        public AgentConfig config(LlmConfig base) {
            return configFn.apply(base);
        }
    }
}
