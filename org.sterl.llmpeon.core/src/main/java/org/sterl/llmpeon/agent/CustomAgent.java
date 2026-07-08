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
 * agent's {@code tools} allowlist (absent = all) and, when {@code readOnly} is set, to non-edit
 * tools. An optional {@code model} in the frontmatter overrides the active model.</p>
 *
 * <p>The {@link AgentPromptFile} snapshot is replaceable so a config refresh can pick up edits;
 * within a single tool loop the filters stay constant, preserving the KV cache.</p>
 */
public class CustomAgent extends AbstractAgent {
    public static final String MODEL = "model";
    public static final String INCLUDE_DEFAULT = "include-default";
    public static final String TEMPERATURE = "temperature";
    public static final String TOOLS = "tools";
    public static final String READ_ONLY = "read-only";

    @Getter @Setter
    private volatile SimplePromptFile promptFile;

    public CustomAgent(SimplePromptFile promptFile, 
            ConfiguredChatModel configuredModel, 
            ToolService toolService) {
        super(configuredModel, toolService);
        this.promptFile = promptFile;
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
        promptFile.setValue(MODEL, modelName);
        try {
            promptFile.save();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save " + modelName + " for agent " + getName(), e);
        }
        return true;
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

    /** True if the tool name passes the agent's allowlist. */
    public boolean allowed(String toolName) {
        var tools = promptFile.get(TOOLS);
        return ToolPolicy.enables(tools, toolName);
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
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[name=" + getName() + ", model: " + getAgentModelName() + "]";
    }

}
