package org.sterl.llmpeon.agent;

import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.LlmConfigSaver;
import org.sterl.llmpeon.ai.LlmConfigStore;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * The model selection of one agent (R-ML5, {@code org.sterl.llmpeon/docs/model-loading.md}):
 * which model it effectively uses, whether that is its own or the inherited base model, and
 * where a change is saved. The one routing for every model selector — the selector shows
 * {@link #effectiveModel()} and saves through {@link #save}.
 *
 * <ul>
 * <li>{@link Target#BASE} — Dev: the base model {@code llm.model}, which every agent without an
 * own model inherits.</li>
 * <li>{@link Target#AGENT_SLOT} — PO/Plan/Review: {@code llm.agent.<id>.model}.</li>
 * <li>{@link Target#AGENT_FILE} — custom agents: {@code model:} in their {@code AGENT.md}.</li>
 * <li>{@link Target#NONE} — agents without an own model (e.g. Scaffold): always the base model,
 * not selectable.</li>
 * </ul>
 *
 * @param agentName      the agent's display name
 * @param target         where a change is saved
 * @param ownModel       the agent's own model; {@code null} = inherits the base ({@link Target#BASE}: the base itself)
 * @param effectiveModel the model the agent actually uses (own, else base); {@code null} only if no base is set
 */
public record AgentModelSelection(String agentName, Target target, @Nullable String ownModel,
        @Nullable String effectiveModel) {

    public enum Target {
        BASE, AGENT_SLOT, AGENT_FILE, NONE
    }

    /** @return whether the agent has no own model and uses the base model. Never true for {@link Target#BASE}. */
    public boolean inherited() {
        return target != Target.BASE && ownModel == null;
    }

    /** @return whether the selector may change this agent's model. */
    public boolean selectable() {
        return target != Target.NONE;
    }

    /** Resolves the selection of the given agent against the given (live) config. */
    public static AgentModelSelection of(AiAgent agent, LlmConfig config) {
        var base = StringUtil.stripToNull(config.getModel());
        var slot = agent.modelSlot();
        if (AgentModelConfig.DEV.equals(slot)) {
            return new AgentModelSelection(agent.getName(), Target.BASE, base, base);
        }
        if (slot != null) {
            var own = StringUtil.stripToNull(config.modelConfigFor(slot).model());
            return new AgentModelSelection(agent.getName(), Target.AGENT_SLOT, own, own != null ? own : base);
        }
        if (agent.ownModelConfig().isPresent()) {
            var own = StringUtil.stripToNull(agent.ownModelConfig().get().model());
            return new AgentModelSelection(agent.getName(), Target.AGENT_FILE, own, own != null ? own : base);
        }
        return new AgentModelSelection(agent.getName(), Target.NONE, null, base);
    }

    /**
     * The per-agent record whose connection (url/key) the model list is fetched for — the slot's
     * record, the agent's own {@code AGENT.md} config, or empty (= base connection).
     */
    public static AgentModelConfig connectionRecord(AiAgent agent, LlmConfig config) {
        var slot = agent.modelSlot();
        if (slot != null) return config.modelConfigFor(slot);
        return agent.ownModelConfig().orElse(AgentModelConfig.empty());
    }

    /**
     * Saves a model chosen in a selector for the given agent. Only a real change from the shown
     * {@link #effectiveModel()} is saved (case-insensitive, R-ML4) — leaving the field unchanged
     * never creates an own model for an inheriting agent. A blank value removes the agent's own
     * model (key or frontmatter field), it is never stored as {@code ""}.
     *
     * <p>Exactly one target is written: the base for Dev, the slot key for PO/Plan/Review, the
     * {@code AGENT.md} for custom agents — the other agents stay untouched.</p>
     *
     * @param store the config store for slot and base keys (not used for {@link Target#AGENT_FILE})
     * @return {@code true} if something was saved
     */
    public static boolean save(AiAgent agent, LlmConfig config, LlmConfigStore store, @Nullable String model) {
        var selection = of(agent, config);
        var value = StringUtil.stripToNull(model);
        if (sameModel(value, selection.effectiveModel())) return false;
        switch (selection.target()) {
            case BASE -> LlmConfigSaver.saveAgentModel(store, AgentModelConfig.DEV, value);
            case AGENT_SLOT -> LlmConfigSaver.saveAgentModel(store, agent.modelSlot(), value);
            case AGENT_FILE -> {
                return agent.setAgentModelName(value);
            }
            case NONE -> {
                return false;
            }
        }
        return true;
    }

    private static boolean sameModel(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }
}
