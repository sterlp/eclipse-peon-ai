package org.sterl.llmpeon.parts;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.sterl.llmpeon.AgentService;
import org.sterl.llmpeon.PeonMode;
import org.sterl.llmpeon.StandingOrdersBuilder.MessageProvider;
import org.sterl.llmpeon.agent.AbstractAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.CustomAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
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
import org.sterl.llmpeon.prompt.PromptYmlParser;
import org.sterl.llmpeon.shared.StringUtil;
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

    private final ConfiguredChatModel configuredModel;
    private final ToolService toolService;
    private final SkillService skillService;
    private final CommandService commandService;
    private final AgentService agentService;
    private final AgentsMdService agentsMdService;

    private final AgentModeService agentMode;
    private final AgentModeTool agentModeTool;
    private final McpConnectionService mcpConnectionService;
    
    private final EclipseWorkspaceWriteFileTool workspaceWriteFilesTool;
    private final DiskFileWriteTool diskFileWriteTool;
    private final DiskFileReadTool diskFileReadTool;
    private final DiskGrepTool diskGrepTool;

    /** Written from Eclipse DI thread, read from background LLM job threads. */
    private final AtomicReference<IProject> currentProject = new AtomicReference<>();

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
        
        toolService.addTool(new EclipseBuildTool());
        toolService.addTool(new EclipseGrepTool());
        toolService.addTool(new EclipseRunTestTool());
        toolService.addTool(new EclipseCodeNavigationTool());
        toolService.addTool(new EclipseConsoleLogTool());

        agentService  = new AgentService(true, 
                config.getConfigDir().resolve(LlmConfig.AGENT_DIRECTORY), toolService, configuredModel);

        // Agent mode uses separate instances with isolated memory
        var agentDev  = new AiDevAgent(configuredModel, toolService);
        var agentPlan = new AiPlanAgent(configuredModel, toolService);
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
    
    public AbstractAgent getActiveService() {
        var custom = activeCustomAgent;
        if (custom != null) return custom;
        return switch (getPeonMode()) {
            case DEV   -> getDeveloperService();
            case PLAN  -> getPlannerService();
            case AGENT -> getAgentMode().getActiveService();
        };
    }

    public String getActiveModel() {
        return StringUtil.hasValue(getActiveService().getAgentModelName())
                ? getActiveService().getAgentModelName()
                : getConfig().getModel();
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

    public AiDevAgent getDeveloperService() {
        return developerService;
    }

    public AiPlanAgent getPlannerService() {
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

    /** One tool with its active state for the currently selected agent/mode. */
    public record ToolStatus(String name, boolean active, boolean mcp) {}

    /**
     * Lists every registered tool (built-in + connected MCP) with whether it is active for the
     * currently active service. Sorted: active first, then by name. For UI introspection.
     */
    public List<ToolStatus> getToolStatus() {
        var svc = getActiveService();
        var result = new java.util.ArrayList<ToolStatus>();
        for (var exec : toolService.getExecutors()) {
            result.add(new ToolStatus(exec.getSpec().name(), svc.isToolActive(exec), false));
        }
        for (var name : toolService.mcpToolNames()) {
            result.add(new ToolStatus(name, svc.isMcpToolActive(name), true));
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
        var active = getActiveService();
        if (active instanceof CustomAgent custom) {
            if (custom.setModelName(model) && model != null && StringUtil.hasValue(model.getId())) {
                persistCustomAgentModel(custom, model.getId());
            }
        } else if (active.setModelName(model)) {
            LlmPreferenceInitializer.setModel(model.getId(), getPeonMode());
        }
    }

    private void persistCustomAgentModel(CustomAgent custom, String modelId) {
        var file = custom.getAgentFile().getPromptFile();
        try {
            PromptYmlParser.setFrontmatterValue(file, "model", modelId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist model to " + file, e);
        }
    }

    public ConfiguredChatModel resolveModel(List<AiModel> models) {
        this.configuredModel.resolveModel(models);
        return configuredModel;
    }

    public void withThinking(Boolean enabled) {
        if (enabled == null) enabled = Boolean.FALSE;
        configuredModel.withThinking(enabled);
    }

    /**
     * Selects a custom agent by name; subsequent requests use its service. Behaves like leaving
     * AGENT mode (agent-mode tool removed, orchestration reset). No-op for an unknown name.
     */
    public void setActiveCustomAgent(String name) {
        var def = agentService.get(name);
        if (def.isEmpty()) return;
        var svc = customAgents.computeIfAbsent(name.toLowerCase(Locale.ROOT),
                k -> new CustomAgent(configuredModel, toolService, def.get()));
        svc.setAgentFile(def.get());
        getToolService().removeTool(getAgentModeTools());
        getAgentMode().reset();
        activeCustomAgent = svc;
    }

    public void setPeonMode(PeonMode mode) {
        this.mode = mode;
        this.activeCustomAgent = null;
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

    public boolean isCustomAgentActive() {
        return activeCustomAgent != null;
    }

    /** Display label of the active selection: the custom agent name or the built-in mode label. */
    public String getActiveAgentLabel() {
        var custom = activeCustomAgent;
        return custom != null ? custom.getAgentFile().name() : mode.getLabel();
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
