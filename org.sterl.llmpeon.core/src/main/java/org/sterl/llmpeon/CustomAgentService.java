package org.sterl.llmpeon;

import java.util.Objects;
import java.util.function.Predicate;

import org.sterl.llmpeon.agent.AgentPromptFile;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.ToolPolicy;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;

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
public class CustomAgentService extends AbstractChatService {

    private volatile AgentPromptFile agentFile;

    public CustomAgentService(ConfiguredChatModel configuredModel, ToolService toolService,
            AgentPromptFile agentFile) {
        super(configuredModel, toolService);
        this.agentFile = agentFile;
    }

    /** Replaces the underlying definition (e.g. after a directory refresh). */
    public void setAgentFile(AgentPromptFile agentFile) {
        this.agentFile = agentFile;
    }

    public AgentPromptFile getAgentFile() {
        return agentFile;
    }

    @Override
    protected String getSystemPrompt() {
        return PromptLoader.withDefault(agentFile.readBody());
    }

    @Override
    public double getTemperature() {
        return configuredModel.getConfig().getDevTemperature();
    }

    @Override
    public String getAgentModelName() {
        var model = agentFile.getModel();
        return StringUtil.hasValue(model) ? model : configuredModel.getConfig().getModel();
    }

    /**
     * Pins the model to this agent only (not the shared config). {@link PeonAiService} persists the
     * change back into the {@code AGENT.md} frontmatter.
     *
     * @return <code>true</code> if changed
     */
    @Override
    public boolean setModelName(AiModel modelName) {
        String id = modelName == null ? null : modelName.getId();
        if (Objects.equals(id, agentFile.getModel())) return false;
        agentFile.setModel(id);
        return true;
    }

    /** True if the tool name passes the agent's allowlist (absent allowlist = all tools). */
    public boolean allowed(String toolName) {
        var tools = agentFile.getTools();
        return tools == null || ToolPolicy.enables(tools, toolName);
    }

    @Override
    protected Predicate<SmartToolExecutor> getToolFilter() {
        return exec -> allowed(exec.getSpec().name())
                && (!agentFile.isReadOnly() || !exec.getTool().isEditTool());
    }

    @Override
    protected Predicate<String> getToolNameFilter() {
        return this::allowed;
    }
}
