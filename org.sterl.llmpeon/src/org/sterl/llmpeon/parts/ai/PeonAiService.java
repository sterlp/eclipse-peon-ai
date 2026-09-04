package org.sterl.llmpeon.parts.ai;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.sterl.llmpeon.AgentService;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.NamedAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.command.CommandService;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.context.UserContext;
import org.sterl.llmpeon.parts.AIChatView;
import org.sterl.llmpeon.parts.ai.component.AgentContextComponent;
import org.sterl.llmpeon.parts.ai.component.BuildPoAgentComponent;
import org.sterl.llmpeon.parts.ai.component.SharedToolsComponent;
import org.sterl.llmpeon.parts.config.LlmPreferenceInitializer;
import org.sterl.llmpeon.parts.config.McpConnectionService;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.tools.PlanTool;
import org.sterl.llmpeon.parts.tools.memory.WorkspaceMemoryTool;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.llmpeon.scaffold.AiScaffoldAgent;
import org.sterl.llmpeon.scaffold.ReloadConfigTool;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.tools.SkillTool;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Bundles all AI services into a single eagerly-initialized object.
 *
 * <p>Declaring this as a {@code final} field in {@link AIChatView} guarantees that all
 * services are non-null from the moment the view object is constructed — before Eclipse DI
 * has a chance to call any {@code @Inject} methods.</p>
 *
 */
public class PeonAiService {

    private final UserContext userContext = new UserContext();

    private final AgentService agentService;
    private final ConfiguredChatModel configuredModel;

    /** Shared tool service used by dev/plan/custom agents (MCP, AskUserTool, ShellTool, workspace disk tools). */
    private ToolService sharedToolService;
    private AiScaffoldAgent scaffoldAgent;

    private SkillService skillService;
    private CommandService commandService;

    private McpConnectionService mcpConnectionService;

    private PlanTool planTool;

    private ReloadConfigTool reloadConfigTool;

    private SharedToolsComponent sharedTools;
    private WorkspaceMemoryTool workspaceMemoryTool;
    private AgentContextComponent agentContext;

    /**
     * Creates all AI services with defaults from the current Eclipse preferences.
     *
     * @param sendTrigger         callback to re-trigger the send loop (agent autonomous mode)
     * @param openInEditorCallback callback to open a file in the Eclipse editor
     * @param mcpStateChange      callback notified when MCP connected/disconnected
     * @param onAgentReload       callback invoked after agents are reloaded (e.g. to refresh the UI)
     */
    public PeonAiService(Runnable sendTrigger,
                         Consumer<IFile> openInEditorCallback,
                         Consumer<Boolean> mcpStateChange,
                         Runnable onAgentReload) {

        this(sendTrigger, openInEditorCallback, mcpStateChange, onAgentReload, LlmPreferenceInitializer.buildWithDefaults().build());
    }
    
    public PeonAiService(Runnable sendTrigger,
            Consumer<IFile> openInEditorCallback,
            Consumer<Boolean> mcpStateChange,
            Runnable onAgentReload,
            ConfiguredChatModel configuredModel) {
        
        var config              = configuredModel.getConfig();
        this.configuredModel    = configuredModel;
        skillService            = new SkillService();
        commandService          = new CommandService();
        sharedTools             = new SharedToolsComponent(skillService, commandService);
        sharedToolService       = sharedTools.toolService();
        workspaceMemoryTool     = sharedTools.workspaceMemoryTool();

        planTool = new PlanTool(this);
        sharedToolService.addTool(planTool);

        agentService  = new AgentService(true,
                config.getConfigDir().resolve(LlmConfig.AGENT_DIRECTORY), sharedToolService, configuredModel, config.getConfigDir());

        scaffoldAgent = new AiScaffoldAgent(configuredModel);
        scaffoldAgent.addTool(new SkillTool(skillService));
        // ReloadConfigTool needs agentService (already created) + skillService + commandService + config.
        // Its callback fires after the reloadAgents() inside the tool, so wrap it: re-bake the Env
        // static context first (new custom agents included), then hand off to the original UI
        // callback — the reload path never goes through updateConfig, so without this the freshly
        // loaded custom agents would have no system-prompt context at all.
        // (ADR-0032 Rev: memory is dynamic only — the re-bake carries Env exclusively.)
        reloadConfigTool = new ReloadConfigTool(agentService, skillService, commandService, config, () -> {
            initStaticContext();
            if (onAgentReload != null) onAgentReload.run();
        });
        scaffoldAgent.addTool(reloadConfigTool);

        // Add scaffold as persistent agent (survives clearAgents on reload)
        agentService.addPersistentAgent(scaffoldAgent);

        var poAgent = new BuildPoAgentComponent(configuredModel, config, this::getProject, sharedToolService)
                .build();
            //new AiPoAgent(configuredModel, poToolService, config.getConfigDir(), List.of(thinka, mek));

        agentService.addPersistentAgent(poAgent);

        mcpConnectionService = new McpConnectionService(sharedToolService, mcpStateChange);

        // Context assembly (turn context, static bake, plan/handoff state) — pure component,
        // wired with values + lazy suppliers only (no service dependency).
        agentContext = new AgentContextComponent(this::getProject, workspaceMemoryTool, userContext,
                scaffoldAgent, sharedToolService,
                this::getActiveAgent, configuredModel::getConfig, this::getAgents);

        updateConfig(configuredModel.getConfig());
        // Default turn-context supplier for every agent present at construction. New custom agents
        // (created by a later updateConfig/refresh) receive it via call() before each turn.
        for (var agent : this.getAgents()) {
            agent.setTurnContextSupplier(() -> get());
        }
    }
    
    
    /**
     * Bakes the Env info into the static context (system prompt) of every agent — the Workspace-Memory
     * is NOT part of it anymore (ADR-0032 Rev: dynamic turn-context only).
     * Runs on every {@link #updateConfig(LlmConfig)} and after a scaffold-agent reload (wrapped
     * {@code onAgentReload} callback) — after the agent refresh — so new custom
     * agents receive it too and existing agents get a fresh list (invalidating their cached
     * system prompt). Jon's slaves share his list via {@link AiPoAgent#setStaticContext}.
     * Deliberately does NOT touch the turn-context supplier (that would clobber a custom one);
     * the default supplier is wired once in the constructor.
     */
    private void initStaticContext() {
        agentContext.initStaticContext();
    }

    /**
     * Propagates a new {@link LlmConfig} to all chat services and refreshes skills.
     * Safe to call from any thread.
     */
    public void updateConfig(LlmConfig config) {
        configuredModel.updateConfig(config);
        reloadConfigTool.updateConfig(config);
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
        // After the refresh: (re-)bake the Env static context — new custom agents are included,
        // and existing agents get a fresh list, which invalidates their cached system prompt so an
        // edited AGENT.md base prompt is picked up on the next turn.
        initStaticContext();
    }


    private void updateActiveDiskTools(LlmConfig config) {
        sharedTools.updateActiveDiskTools(config);
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
     * @return <code>true</code> if changed
     */
    public boolean setProject(IProject project) {
        agentContext.clearPlan(); // stale reference — restore on next agent activation if needed

        var projectPath = JdtUtil.pathOf(project);

        sharedTools.workspaceWriteFilesTool().setCurrentProject(project);
        sharedTools.workspaceReadFilesTool().setCurrentProject(project);
        sharedTools.eclipseGrepTool().setCurrentProject(project);
        // disk tools work with the disk path not eclipse path
        projectPath = JdtUtil.diskPathOf(project);
        if (projectPath != null) {
            sharedTools.diskFileWriteTool().setWorkingDir(projectPath);
            sharedTools.diskFileReadTool().setWorkingDir(projectPath);
            sharedTools.diskGrepTool().setWorkingDir(projectPath);
        }
        return this.userContext.setCurrentProject(project);
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

        String planText;
        String planPath;
        var plan = agentContext.planRef();
        if (plan != null) { // workspace plan ref — not disk, avoids stale project reference
            planText = agentContext.readPlan();
            planPath = JdtUtil.pathOf(plan);

            agentContext.armHandoffLine(getActiveAgent().getName(), planPath);
        } else {
            var chatPlan = getActiveAgent().getMemory().getLastOf(AiMessage.class);
            if (chatPlan == null) planText = null;
            else planText = chatPlan.text();
            
            planPath = "Handover from " + getActiveAgent().getName() + ":";
        }

        if (planText != null) {
            // and directly add the plan too ...
            toAgent.get().clear();
            toAgent.get().getMemory().add(UserMessage.from(planPath + System.lineSeparator() + planText));
            this.agentService.setActiveAgent(toAgent.get());
        }
        
        return planText != null;
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
        return userContext.getCurrentProject();
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
        var active = getActiveAgent();
        if (active != null && active.getToolService() != null) {
            return active.getToolService();
        }
        return sharedToolService;
    }

    /** Returns the shared tool service used by dev/plan/custom agents (MCP, AskUserTool, ShellTool). */
    public ToolService getSharedToolService() {
        return sharedToolService;
    }

    /** One tool with its active state for the currently selected agent/mode. */
    public record ToolStatus(String name, boolean active, boolean mcp) {}

    /**
     * The agents shown in the header status widget: when Jon (Peon-PO) is active, his
     * {@link AiPoAgent#getTeam() team} (Da Boss + the two orks Da Thinka/Da Mek); for every other
     * agent an empty list — they show nothing. This is the <b>single</b> {@code instanceof AiPoAgent}
     * choke-point (ADR-0025): the widget pulls this list and stays type-agnostic, the {@code AiAgent}
     * interface stays lean.
     */
    public List<NamedAgent> getStatusAgents() {
        return getActiveAgent() instanceof AiPoAgent po ? po.getTeam() : List.of();
    }

    /**
     * Lists every registered tool (built-in + connected MCP) with whether it is active for the
     * currently active service. Sorted: active first, then by name. For UI introspection.
     */
    public List<ToolStatus> getToolStatus() {
        var svc = getActiveAgent();
        var result = new java.util.ArrayList<ToolStatus>();
        var ts = getToolService();
        for (var exec : ts.getExecutors()) {
            result.add(new ToolStatus(exec.getSpec().name(), svc.isToolActive(exec), false));
        }
        for (var name : ts.mcpToolNames()) {
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

    public McpConnectionService getMcpConnectionService() {
        return mcpConnectionService;
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

    /** Returns the tutorial text for the scaffold agent (null if already shown in this session). */
    public String getScaffoldTutorial() {
        var agent = getActiveAgent();
        if (!(agent instanceof AiScaffoldAgent)) return null;
        if (agent.getMemory().size() > 0) return null;
        return org.sterl.llmpeon.prompt.PromptLoader.load("scaffold-tutorial.txt");
    }

    /**
     * A one-time Jon intro shown as a chat message when there is nothing to onboard from yet — Jon
     * active, empty memory, and no {@code docs/index.md}: if there is a map, Jon navigates it (it
     * lands in his turn context, ADR-0029); if there is none, we greet the user and explain how Jon
     * works. Returns {@code null} otherwise. Shown on activation like {@link #getScaffoldTutorial()},
     * not sent to the LLM.
     */
    public String getPoTutorial() {
        var agent = getActiveAgent();
        if (!(agent instanceof AiPoAgent)) return null;
        if (agent.getMemory().size() > 0) return null;
        var project = getProject();
        if (project == null) return null;

        var index = project.getFile("docs/index.md");
        if (index != null && index.exists()) return null; // there is a map -> Jon navigates it

        return org.sterl.llmpeon.prompt.PromptLoader.load("po-tutorial.txt");
    }

    private void preloadPlanIfNeeded() {
        if (!planTool.hasPlan()) return;

        var agent = getActiveAgent();
        if (agent instanceof AiPlanAgent) {
            if (agentContext.planRef() == null) {
                agentContext.setPlan(getProject().getFile(PlanTool.OVERVIEW_FILE));
            }
        }
    }

    public void onPlanSaved(IFile planFile) {
        agentContext.setPlan(planFile);
    }

    public void clear() {
        agentContext.clearPlan();
        getActiveAgent().clear();
    }

    public void clearAll() {
        agentContext.clearPlan();
        this.agentService.getAgents().forEach(AiAgent::clear);
    }

    public List<AiAgent> getAgents() {
        return this.agentService.getAgents();
    }

    private List<ContextItem> get() {
        return agentContext.turnContext();
    }

    public UserContext getUserContext() {
        return userContext;
    }

    /**
     * Calls the active agent of the current context set
     */
    public ChatResponse call(String messageToSend, AiMonitor monitor) {
        var agent = getActiveAgent();
        agent.setTurnContextSupplier(() -> get());
        return agent.call(messageToSend, monitor);
    }
}
