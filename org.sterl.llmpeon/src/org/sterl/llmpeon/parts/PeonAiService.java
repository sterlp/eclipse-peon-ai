package org.sterl.llmpeon.parts;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.sterl.llmpeon.AbstractChatService;
import org.sterl.llmpeon.AiDeveloperService;
import org.sterl.llmpeon.AiPlannerService;
import org.sterl.llmpeon.PeonMode;
import org.sterl.llmpeon.StandingOrdersBuilder.MessageProvider;
import org.sterl.llmpeon.ai.ConfiguredModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.command.CommandService;
import org.sterl.llmpeon.parts.agent.AgentModeService;
import org.sterl.llmpeon.parts.agentsmd.AgentsMdService;
import org.sterl.llmpeon.parts.config.LlmPreferenceInitializer;
import org.sterl.llmpeon.parts.config.McpConnectionService;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.tools.AgentModeTool;
import org.sterl.llmpeon.parts.tools.EclipseBuildTool;
import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;
import org.sterl.llmpeon.parts.tools.EclipseConsoleLogTool;
import org.sterl.llmpeon.parts.tools.EclipseGrepTool;
import org.sterl.llmpeon.parts.tools.EclipseRunTestTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceWriteFileTool;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import org.sterl.llmpeon.tool.tools.DiskGrepTool;
import org.sterl.llmpeon.tool.tools.SkillTool;

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
public class PeonAiService implements MessageProvider {

    private final ConfiguredModel configuredModel;
    private final ToolService toolService;
    private final SkillService skillService;
    private final CommandService commandService;
    private final AgentsMdService agentsMdService;
    private final AiDeveloperService developerService;
    private final AiPlannerService plannerService;
    private final AgentModeService agentMode;
    private final AgentModeTool agentModeTool;
    private final McpConnectionService mcpConnectionService;
    
    private final EclipseWorkspaceWriteFileTool workspaceWriteFilesTool;
    private final DiskFileWriteTool diskFileWriteTool;
    private final DiskFileReadTool diskFileReadTool;
    private final DiskGrepTool diskGrepTool;

    /** Written from Eclipse DI thread, read from background LLM job threads. */
    private final AtomicReference<IProject> currentProject = new AtomicReference<>();
    
    private volatile PeonMode mode = PeonMode.DEV;
    
    /**
     * Package-private constructor for unit tests — accepts pre-built services to avoid
     * hard Eclipse dependencies ({@code EclipseUtil.workspacePath()}, Eclipse tools, etc.).
     */
    PeonAiService(ConfiguredModel configuredModel, AiPlannerService plannerService, AiDeveloperService developerService) {
        this.configuredModel = configuredModel;
        this.plannerService = plannerService;
        this.developerService = developerService;
        this.toolService = null;
        this.skillService = null;
        this.commandService = null;
        this.agentsMdService = null;
        this.agentMode = null;
        this.agentModeTool = null;
        this.mcpConnectionService = null;
        this.workspaceWriteFilesTool = null;
        this.diskFileWriteTool = null;
        this.diskFileReadTool = null;
        this.diskGrepTool = null;
    }

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

        configuredModel = LlmPreferenceInitializer.buildWithDefaults().build();

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
        
        toolService.addTool(new EclipseBuildTool());
        toolService.addTool(new EclipseGrepTool());
        toolService.addTool(new EclipseRunTestTool());
        toolService.addTool(new EclipseCodeNavigationTool());
        toolService.addTool(new EclipseConsoleLogTool());

        developerService = new AiDeveloperService(configuredModel, toolService);
        plannerService   = new AiPlannerService(configuredModel, toolService);

        // Agent mode uses separate instances with isolated memory
        var agentDev  = new AiDeveloperService(configuredModel, toolService);
        var agentPlan = new AiPlannerService(configuredModel, toolService);
        agentMode     = new AgentModeService(agentPlan, agentDev, sendTrigger, openInEditorCallback);
        agentModeTool = new AgentModeTool(agentMode);

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
        if (config.getSkillDirectory() != null && !config.getSkillDirectory().isBlank()) {
            try {
                skillService.refresh(config.getSkillDirectory());
            } catch (IOException e) {
                throw new RuntimeException("Failed to load skills from " + config.getSkillDirectory(), e);
            }
        }
        try {
            commandService.refresh(config.getCommandDirectory());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load commands from " + config.getCommandDirectory(), e);
        }
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
    
    public AbstractChatService getActiveService() {
        return switch (getPeonMode()) {
            case DEV   -> getDeveloperService();
            case PLAN  -> getPlannerService();
            case AGENT -> getAgentMode().getActiveService();
        };
    }

    /**
     * Updates the active project across all project-aware services.
     * Safe to call from any thread — each downstream setter manages its own state.
     */
    public void setProject(IProject project) {
        currentProject.set(project);
        agentsMdService.load(project);
        agentMode.setProject(project);  // volatile write inside AgentModeService

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
    public boolean startImplementation() {
        // LM Studio is sometimes bugged, if the first message is no user message ... :-/
        var plan = plannerService.extractLastPlan();
        if (plan.isPresent()) {
            developerService.clear();
            developerService.addMessage(UserMessage.from("Reading this plan:"));
            plan.ifPresent(developerService::addMessage);
        }
        return plan.isPresent();
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
        return currentProject.get();
    }

    public LlmConfig getConfig() {
        return configuredModel.getConfig();
    }

    public AiDeveloperService getDeveloperService() {
        return developerService;
    }

    public AiPlannerService getPlannerService() {
        return plannerService;
    }

    public AgentModeService getAgentMode() {
        return agentMode;
    }

    public AgentModeTool getAgentModeTools() {
        return agentModeTool;
    }

    public ToolService getToolService() {
        return toolService;
    }

    public SkillService getSkillService() {
        return skillService;
    }

    public CommandService getCommandService() {
        return commandService;
    }

    public AgentsMdService getAgentsMdService() {
        return agentsMdService;
    }

    public McpConnectionService getMcpConnectionService() {
        return mcpConnectionService;
    }

    public void setModel(AiModel model) {
        if (this.configuredModel.withModel(model)) {
            LlmPreferenceInitializer.setModel(model.getId());
        }
    }

    public ConfiguredModel resolveModel(List<AiModel> models) {
        this.configuredModel.resolveModel(models);
        return configuredModel;
    }

    public String getModel() {
        return configuredModel.getModel();
    }

    public void withThinking(Boolean enabled) {
        if (enabled == null) enabled = Boolean.FALSE;
        configuredModel.withThinking(enabled);
    }

    public void setPeonMode(PeonMode mode) {
        this.mode = mode;
        if (mode == PeonMode.AGENT) {
            getToolService().addTool(getAgentModeTools());
            getAgentMode().reset();
            if (getAgentMode().overviewExists()) {
                getAgentMode().getActiveService().addMessage(UserMessage.from(
                        "Existing plan found:\n\n" + getAgentMode().readOverview()));
                getAgentMode().openOverviewInEditor();
            }
        } else {
            getToolService().removeTool(getAgentModeTools());
            getAgentMode().reset();
        }
    }
    
    public PeonMode getPeonMode() {
        return mode;
    }

    // TODO do we provide this twice?
    @Override
    public String get() {
        var agentMode = getAgentMode();
        if (getPeonMode() == PeonMode.AGENT && agentMode.hasPlan()) {
            return agentMode.planPathHint();
        }
        return null;
    }
}
