package org.sterl.llmpeon.parts;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.sterl.llmpeon.AgentService;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.command.CommandService;
import org.sterl.llmpeon.parts.agentsmd.AgentsMdService;
import org.sterl.llmpeon.parts.config.LlmPreferenceInitializer;
import org.sterl.llmpeon.parts.config.McpConnectionService;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.tools.EclipseBuildTool;
import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;
import org.sterl.llmpeon.parts.tools.EclipseConsoleLogTool;
import org.sterl.llmpeon.parts.tools.EclipseGrepTool;
import org.sterl.llmpeon.parts.tools.EclipseRunTestTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceWriteFileTool;
import org.sterl.llmpeon.parts.tools.PlanTool;
import org.sterl.llmpeon.parts.tools.memory.WorkspaceMemoryTool;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import org.sterl.llmpeon.tool.tools.DiskGrepTool;
import org.sterl.llmpeon.tool.tools.SkillTool;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Bundles all AI services into a single eagerly-initialized object.
 *
 * <p>Declaring this as a {@code final} field in {@link AIChatView} guarantees that all
 * services are non-null from the moment the view object is constructed — before Eclipse DI
 * has a chance to call any {@code @Inject} methods.</p>
 *
 * <p>Mutable state ({@code currentProject}, {@code currentConfig}) is held in
 * {@link AtomicReference} so that reads on background threads always see the latest values
 * without requiring locks. The downstream setters on individual services are called from the
 * Eclipse DI thread and are inherently serialized by the single-threaded event dispatch.</p>
 */
public class PeonAiService {

    private final AgentService agentService;
    private final ConfiguredChatModel configuredModel;

    private final ToolService toolService;
    private final SkillService skillService;
    private final CommandService commandService;
    
    private final AgentsMdService agentsMdService;

    private final McpConnectionService mcpConnectionService;

    private final PlanTool planTool;
    
    private final EclipseWorkspaceWriteFileTool workspaceWriteFilesTool;
    private final DiskFileWriteTool diskFileWriteTool;
    private final DiskFileReadTool diskFileReadTool;
    private final DiskGrepTool diskGrepTool;
    
    private final WorkspaceMemoryTool workspaceMemoryTool = WorkspaceMemoryTool.getInstance();

    private  volatile IProject currentProject = null;
    
    private IFile plan;

    /**
     * Creates all AI services with defaults from the current Eclipse preferences.
     *
     * @param sendTrigger         callback to re-trigger the send loop (agent autonomous mode)
     * @param openInEditorCallback callback to open a file in the Eclipse editor
     * @param mcpStateChange      callback notified when MCP connected/disconnected
     */
    public PeonAiService(Runnable sendTrigger,
                         Consumer<IFile> openInEditorCallback,
                         Consumer<Boolean> mcpStateChange) {

        var config = LlmPreferenceInitializer.buildWithDefaults();
        configuredModel = config.build();

        var rootPath            = EclipseUtil.workspacePath();
        toolService             = new ToolService();
        skillService            = new SkillService();
        commandService          = new CommandService();
        agentsMdService         = new AgentsMdService();
        
        toolService.addTool(new SkillTool(skillService));
        workspaceWriteFilesTool = new EclipseWorkspaceWriteFileTool();
        toolService.addTool(workspaceWriteFilesTool);
        toolService.addTool(new EclipseWorkspaceReadFileTool());
        
        diskFileWriteTool = new DiskFileWriteTool(rootPath);
        diskFileReadTool  = new DiskFileReadTool(rootPath);
        diskGrepTool      = new DiskGrepTool(rootPath);
        
        
        toolService.addTool(workspaceMemoryTool);
        toolService.addTool(new EclipseBuildTool());
        toolService.addTool(new EclipseGrepTool());
        toolService.addTool(new EclipseRunTestTool());
        toolService.addTool(new EclipseCodeNavigationTool());
        toolService.addTool(new EclipseConsoleLogTool());
        
        planTool = new PlanTool(this);
        toolService.addTool(planTool);

        agentService  = new AgentService(true, 
                config.getConfigDir().resolve(LlmConfig.AGENT_DIRECTORY), toolService, configuredModel);


        mcpConnectionService = new McpConnectionService(toolService, mcpStateChange);

        updateConfig(configuredModel.getConfig());
    }
    
    /**
     * Propagates a new {@link LlmConfig} to all chat services and refreshes skills.
     * Safe to call from any thread.
     */
    public void updateConfig(LlmConfig config) {
        configuredModel.updateConfig(config);
        updateActiveDiskTools(config);
        
        var dir = config.getConfigDir().resolve(LlmConfig.SKILL_DIRECTORY);
        try {
            skillService.refresh(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load skills from " + dir, e);
        }
        dir = config.getConfigDir().resolve(LlmConfig.COMMAND_DIRECTORY);
        try {
            commandService.refresh(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load commands from " + dir, e);
        }
        dir = config.getConfigDir().resolve(LlmConfig.AGENT_DIRECTORY);
        agentService.refresh(dir);
    }


    private void updateActiveDiskTools(LlmConfig config) {
        if (config.isDiskToolsEnabled()) {
            if (toolService.getTool(DiskFileWriteTool.class).isEmpty()) {
                toolService.addTool(diskFileWriteTool);
                toolService.addTool(diskFileReadTool);
                toolService.addTool(diskGrepTool);
            }
        } else {
            if (toolService.getTool(DiskFileWriteTool.class).isPresent()) {
                toolService.removeTool(diskFileWriteTool);
                toolService.removeTool(diskFileReadTool);
                toolService.removeTool(diskGrepTool);
            }
        }
    }
    
    public AiAgent getActiveAgent() {
        return this.agentService.getActiveAgent();
    }

    public String getActiveModel() {
        return StringUtil.hasValue(getActiveAgent().getAgentModelName())
                ? getActiveAgent().getAgentModelName()
                : getConfig().getModel();
    }

    /**
     * Updates the active project across all project-aware services.
     * Safe to call from any thread — each downstream setter manages its own state.
     */
    public void setProject(IProject project) {
        currentProject = project;
        agentsMdService.load(project);

        var projectPath = JdtUtil.pathOf(project);

        workspaceWriteFilesTool.setCurrentProject(project);

        // disk tools work with the disk path not eclipse path
        projectPath = JdtUtil.diskPathOf(project);
        if (projectPath != null) {
            diskFileWriteTool.setWorkingDir(projectPath);
            diskFileReadTool.setWorkingDir(projectPath);
            diskGrepTool.setWorkingDir(projectPath);
        }
    }

    // -------------------------------------------------------------------------
    // Business logic
    // -------------------------------------------------------------------------

    /**
     * Performs the PLAN → DEV handoff: clears the developer service, adds the
     * "Start Implementation" trigger, and appends the last planner message (the
     * self-contained implementation plan) if one exists.
     * @return <code>true</code> if plan is found
     */
    public boolean onHandoff() {
        if (getActiveAgent() == null) return false;
        var toAgent = agentService.get(getActiveAgent().handoverTo());
        if (toAgent.isEmpty()) return false;

        String plan;
        if (planTool.hasPlan()) {
            plan = readPlan();
        } else {
            var chatPlan = getActiveAgent().getMemory().getLastOf(AiMessage.class);
            if (chatPlan == null) plan = null;
            else plan = chatPlan.text();
        }
        
        if (plan != null) {
            toAgent.get().clear();
            // LM Studio is sometimes bugged, if the first message is no user message ... :-/
            toAgent.get().getMemory().add(UserMessage.from(
                    "Handover from " + getActiveAgent().getName() + System.lineSeparator()
                    + plan));
            this.agentService.setActiveAgent(toAgent.get());
        }
        
        return plan != null;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void applyMcpConfig() {
        mcpConnectionService.applyConfig();
    }

    public void disconnectMcp() {
        mcpConnectionService.disconnect();
    }

    // -------------------------------------------------------------------------
    // Accessors — all non-null after construction
    // -------------------------------------------------------------------------

    public IProject getProject() {
        return currentProject;
    }

    public LlmConfig getConfig() {
        if (configuredModel == null) {
            throw new IllegalStateException("configuredModel is not initialized");
        }
        return configuredModel.getConfig();
    }

    public PlanTool getAgentModeTools() {
        return planTool;
    }

    public ToolService getToolService() {
        return toolService;
    }

    /** One tool with its active state for the currently selected agent/mode. */
    public record ToolStatus(String name, boolean active, boolean mcp) {}

    /**
     * Lists every registered tool (built-in + connected MCP) with whether it is active for the
     * currently active service. Sorted: active first, then by name. For UI introspection.
     */
    public List<ToolStatus> getToolStatus() {
        var svc = getActiveAgent();
        var result = new java.util.ArrayList<ToolStatus>();
        for (var exec : toolService.getExecutors()) {
            result.add(new ToolStatus(exec.getSpec().name(), svc.allowed(exec.getSpec().name()), false));
        }
        for (var name : toolService.mcpToolNames()) {
            result.add(new ToolStatus(name, svc.allowed(name), true));
        }
        result.sort(java.util.Comparator
                .comparing(ToolStatus::active).reversed()
                .thenComparing(ToolStatus::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public SkillService getSkillService() {
        return skillService;
    }

    public CommandService getCommandService() {
        return commandService;
    }

    public AgentService getAgentService() {
        return agentService;
    }

    public AgentsMdService getAgentsMdService() {
        return agentsMdService;
    }

    public McpConnectionService getMcpConnectionService() {
        return mcpConnectionService;
    }

    /**
     * Persists a model change for whichever agent is active: a built-in mode saves to the
     * per-mode preference, a custom agent writes {@code model:} back into its {@code AGENT.md}.
     */
    public void setModel(AiModel model) {
        if (model == null) return;
        var active = getActiveAgent();
        LlmPreferenceInitializer.saveModel(model.getId(), active);
    }

    public void withThinking(Boolean enabled) {
        if (enabled == null) enabled = Boolean.FALSE;
        var active = getActiveAgent();
        boolean prefChanged = LlmPreferenceInitializer.saveThinkEnabled(enabled, active);
        if (prefChanged) {
            // Dev/Plan live in LlmConfig -> reload so devAgentConfig()/planAgentConfig() pick it up
            updateConfig(LlmPreferenceInitializer.buildWithDefaults());
        }
        // Custom reads its frontmatter live per request; nothing else to do.
    }
    
    public Optional<AiAgent> getAgent(String agent) {
        return this.agentService.get(agent);
    }
    
    public boolean setActiveAgent(String agent) {
        var a = this.agentService.get(agent);
        if (a.isPresent()) {
            setActiveAgent(a.get());
        }
        return a.isPresent();
    }

    public void setActiveAgent(AiAgent agent) {
        this.agentService.setActiveAgent(agent);
        preloadPlanIfNeeded();
    }

    private void preloadPlanIfNeeded() {
        if (!planTool.hasPlan()) return;

        var agent = getActiveAgent();
        if (agent instanceof AiPlanAgent planAgent) {
            if (planAgent.getMemory().size() == 0) {
                this.plan = getProject().getFile(PlanTool.OVERVIEW_FILE);
                planAgent.getMemory().add(UserMessage.from(
                        "Current active plan. Use plan* tools to change" + System.lineSeparator() + "---" + System.lineSeparator() + System.lineSeparator()
                        + planTool.planRead()));
            }
        } else if (agent.getMemory().size() == 0) {
            agent.getMemory().add(UserMessage.from(
                    "Plan found: " + JdtUtil.pathOf(getProject().getFile(PlanTool.OVERVIEW_FILE)) + System.lineSeparator()
                    + "If plan* tools are available accessable by them too."));
        }
    }

    public void onPlanSaved(IFile planFile) {
        this.plan = planFile;
    }

    public void clear() {
        this.plan = null;
        getActiveAgent().clear();
    }
    
    public List<AiAgent> getAgents() {
        return this.agentService.getAgents();
    }

    private String readPlan() {
        if (this.plan == null) return "";
        return "Plan: " + JdtUtil.pathOf(plan) + System.lineSeparator() + "---" + System.lineSeparator() + System.lineSeparator()
            + IoUtils.readString(plan);
    }

    public boolean hasPlan() {
        return this.plan != null;
    }

    public void setStaticContext(List<ChatMessage> content) {
        this.agentService.getAgents().forEach(a -> a.setStaticContext(content));
    }
}
