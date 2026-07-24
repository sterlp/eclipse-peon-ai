package org.sterl.llmpeon.agent;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.prompt.model.SimplePromptFile;
import org.sterl.llmpeon.tool.ToolPolicy;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

import lombok.Getter;
import lombok.Setter;

/**
 * Chat service backed by a user-defined {@link AgentPromptFile} ({@code AGENT.md}).
 *
 * <p>System prompt = shared default prompt + the agent's markdown body. Tools are restricted by the
 * agent's {@code tools} allowlist (absent = all) and, when {@code isReadOnly} is set, to non-edit
 * tools. An optional {@code model} in the frontmatter overrides the active model.</p>
 *
 * <p>The {@link AgentPromptFile} snapshot is replaceable so a config refresh can pick up edits;
 * within a single tool loop the filters stay constant, preserving the KV cache.</p>
 */
public class CustomAgent extends AbstractAgent {
    public static final String MODEL = "model";
    @Deprecated
    public static final String THINK = "think";                       // legacy alias for think_on_string
    @Deprecated
    public static final String THINK_ENABLED = "think_enabled";       // deprecated, kept for backward compat
    public static final String THINK_SUPPORTED = "think_supported";   // canonical name
    public static final String THINK_ON = "think_on_string";
    public static final String THINK_OFF = "think_off_string";
    public static final String INCLUDE_DEFAULT = "include-default";
    public static final String TEMPERATURE = "temperature";
    public static final String TOOLS = "tools";
    public static final String READ_ONLY = "read-only";
    /** if an agent has a handover it gets the -> handover to button */
    public static final String HANDOVER = "handover";

    @Getter @Setter
    private volatile SimplePromptFile promptFile;

    public CustomAgent(SimplePromptFile promptFile,
            ConfiguredChatModel configuredModel,
            ToolService toolService) {
        super(configuredModel, toolService);
        this.promptFile = promptFile;
    }

    /** Migrates legacy frontmatter keys to their canonical names. Called before any write to avoid
     * eager file mutation on load — the file is only modified when a write operation occurs. */
    public void migrateIfNeeded() {
        try {
            boolean changed = false;
            // think_enabled → think_supported
            String thinkEnabled = promptFile.firstOrDefault(THINK_ENABLED, null);
            if (thinkEnabled != null) {
                promptFile.setValue(THINK_SUPPORTED, thinkEnabled);
                promptFile.remove(THINK_ENABLED);
                changed = true;
            }
            // think → think_on_string (also implies enabled if an on-value)
            String think = promptFile.firstOrDefault(THINK, null);
            if (think != null) {
                promptFile.setValue(THINK_ON, think);
                promptFile.remove(THINK);
                // `think: high` implies enabled; set think_supported if not already present
                if (org.sterl.llmpeon.ai.ThinkResolver.isOn(think) && promptFile.firstOrDefault(THINK_SUPPORTED, null) == null) {
                    promptFile.setValue(THINK_SUPPORTED, "true");
                }
                changed = true;
            }
            if (changed) {
                promptFile.save();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to migrate legacy frontmatter for agent " + getName(), e);
        }
    }

    public SimplePromptFile getAgentFile() {
        return promptFile;
    }
    
    @Override
    public String getName() {
        return promptFile.getName();
    }

    @Override
    public String getSystemPrompt() {
        if (promptFile.isTrue(INCLUDE_DEFAULT)) {
            return PromptLoader.withDefault(promptFile.getBody());
        }
        return promptFile.getBody();
    }

    @Override
    public Double getTemperature() {
        return promptFile.firstOrDefaultNumber(TEMPERATURE, null);
    }

    @Override
    public boolean isThinkEnabled() {
        // THINK_SUPPORTED (canonical) takes precedence; THINK_ENABLED (deprecated) as fallback; legacy `think:` implies enabled for an on-value
        if (promptFile.firstOrDefault(THINK_SUPPORTED, null) != null) return promptFile.isTrue(THINK_SUPPORTED);
        if (promptFile.firstOrDefault(THINK_ENABLED, null) != null) return promptFile.isTrue(THINK_ENABLED);
        var legacy = promptFile.firstOrDefault(THINK, null);
        return legacy != null && org.sterl.llmpeon.ai.ThinkResolver.isOn(legacy);
    }

    @Override
    public org.sterl.llmpeon.ai.AgentConfig getConfig() {
        // legacy `think:` is read as an alias for think_on_string (no inheritance between agents)
        var on = promptFile.firstOrDefault(THINK_ON, promptFile.firstOrDefault(THINK, null));
        var off = promptFile.firstOrDefault(THINK_OFF, null);
        return configuredModel.getConfig().customAgentConfig(
                promptFile.firstOrDefault(MODEL, null),
                isThinkEnabled(), on, off, getTemperature());
    }

    @Override
    public String getAgentModelName() {
        return promptFile.firstOrDefault(MODEL, null);
    }
    /**
     * Pins the model to this agent only (not the shared config). {@link PeonAiService} persists the
     * change back into the {@code AGENT.md} frontmatter.
     *
     * @return <code>true</code> if changed
     */
    @Override
    public boolean setAgentModelName(String modelName) {
        if (modelName == null) return false;
        if (Objects.equals(modelName, getAgentModelName())) return false;
        migrateIfNeeded();
        promptFile.setValue(MODEL, modelName);
        try {
            promptFile.save();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save " + modelName + " for agent " + getName(), e);
        }
        return true;
    }
    
    public String handoverTo() {
        return promptFile.firstOrDefault(HANDOVER, null);
    }
    
    @Override
    public boolean isReadOnly() {
        return promptFile.isTrue(READ_ONLY);
    }
    
    @Override
    public List<String> getTools() {
        return promptFile.get(TOOLS);
    }
    public void setTools(List<String> values) {
        this.promptFile.set(TOOLS, values);
    }

    @Override
    protected Predicate<SmartToolExecutor> getToolFilter() {
        return exec -> allowed(exec.getSpec().name())
                && (!isReadOnly() || !exec.getTool().isEditTool());
    }

    @Override
    protected Predicate<String> getToolNameFilter() {
        return this::allowed;
    }

    /** @return {@code true} if the tool name passes this agent's {@code tools} allowlist. */
    private boolean allowed(String toolName) {
        return ToolPolicy.enables(getTools(), toolName);
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[name=" + getName() + ", model: " + getAgentModelName() + "]";
    }

}
