package org.sterl.llmpeon.parts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.sterl.llmpeon.AgentService;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.AiPoAgent;
import org.sterl.llmpeon.agent.NamedAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.ai.model.AiModel;
import org.sterl.llmpeon.command.CommandService;
import org.sterl.llmpeon.context.AgentsMdContextItem;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.context.EclipseFileContextItem;
import org.sterl.llmpeon.context.SimpleContextItem;
import org.sterl.llmpeon.context.StaticContextItem;
import org.sterl.llmpeon.context.UserContext;
import org.sterl.llmpeon.parts.ai.component.BuildPoAgentComponent;
import org.sterl.llmpeon.parts.config.LlmPreferenceInitializer;
import org.sterl.llmpeon.parts.config.McpConnectionService;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.tools.AskUserTool;
import org.sterl.llmpeon.parts.tools.EclipseBuildTool;
import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;
import org.sterl.llmpeon.parts.tools.EclipseConsoleLogTool;
import org.sterl.llmpeon.parts.tools.EclipseGrepTool;
import org.sterl.llmpeon.parts.tools.EclipseRunTestTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceWriteFileTool;
import org.sterl.llmpeon.parts.tools.PlanTool;
import org.sterl.llmpeon.parts.tools.memory.WorkspaceMemoryTool;
import org.sterl.llmpeon.scaffold.AiScaffoldAgent;
import org.sterl.llmpeon.scaffold.ReloadConfigTool;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import org.sterl.llmpeon.tool.tools.DiskGrepTool;
import org.sterl.llmpeon.tool.tools.SearchAgentTool;
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

    private static final ILog LOG = Platform.getLog(PeonAiService.class);
    private final UserContext userContext = new UserContext();

    private final AgentService agentService;
    private final ConfiguredChatModel configuredModel;

    /** Shared tool service used by dev/plan/custom agents (MCP, AskUserTool, ShellTool, workspace disk tools). */
    private final ToolService sharedToolService;
    private final AiScaffoldAgent scaffoldAgent;

    private final SkillService skillService;
    private final CommandService commandService;

    private final McpConnectionService mcpConnectionService;

    private final PlanTool planTool;
    
    private final EclipseWorkspaceWriteFileTool workspaceWriteFilesTool;
    private final EclipseWorkspaceReadFileTool workspaceReadFilesTool;
    private final EclipseGrepTool eclipseGrepTool;
    private final DiskFileWriteTool diskFileWriteTool;
    private final DiskFileReadTool diskFileReadTool;
    private final DiskGrepTool diskGrepTool;
    
    private final ReloadConfigTool reloadConfigTool;
    
    private final WorkspaceMemoryTool workspaceMemoryTool = new WorkspaceMemoryTool();

    private IFile plan;

    /** Transient standing-order line set on handoff, consumed once by {@link #get()}. */
    private volatile SimpleContextItem _handoffLine;

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
        var rootPath            = EclipseUtil.workspacePath();
        sharedToolService       = new ToolService();
        skillService            = new SkillService();
        commandService          = new CommandService();
        
        // filter eclipse tools from the search agents ...
        var sa = sharedToolService.getTool(SearchAgentTool.class).get();
        sa.setFilter(sa.getFilter().and(e -> !(e.getTool() instanceof AskUserTool)
                       && !(e.getTool() instanceof WorkspaceMemoryTool)));

        sharedToolService.addTool(new SkillTool(skillService));
        workspaceWriteFilesTool = new EclipseWorkspaceWriteFileTool();
        sharedToolService.addTool(workspaceWriteFilesTool);
        workspaceReadFilesTool = new EclipseWorkspaceReadFileTool();
        sharedToolService.addTool(workspaceReadFilesTool);

        diskFileWriteTool = new DiskFileWriteTool(rootPath);
        diskFileReadTool  = new DiskFileReadTool(rootPath);
        diskGrepTool      = new DiskGrepTool(rootPath);


        sharedToolService.addTool(workspaceMemoryTool);
        sharedToolService.addTool(new EclipseBuildTool());
        eclipseGrepTool = new EclipseGrepTool();
        sharedToolService.addTool(eclipseGrepTool);
        sharedToolService.addTool(new EclipseRunTestTool());
        sharedToolService.addTool(new EclipseCodeNavigationTool());
        sharedToolService.addTool(new EclipseConsoleLogTool());

        planTool = new PlanTool(this);
        sharedToolService.addTool(planTool);

        agentService  = new AgentService(true,
                config.getConfigDir().resolve(LlmConfig.AGENT_DIRECTORY), sharedToolService, configuredModel, config.getConfigDir());

        scaffoldAgent = new AiScaffoldAgent(configuredModel);
        scaffoldAgent.addTool(new SkillTool(skillService));
        // ReloadConfigTool needs agentService (already created) + skillService + commandService + config
        reloadConfigTool = new ReloadConfigTool(agentService, skillService, commandService, config, onAgentReload);
        scaffoldAgent.addTool(reloadConfigTool);

        // Add scaffold as persistent agent (survives clearAgents on reload)
        agentService.addPersistentAgent(scaffoldAgent);

        var poAgent = new BuildPoAgentComponent(configuredModel, config, this::getProject, sharedToolService)
                .build();
            //new AiPoAgent(configuredModel, poToolService, config.getConfigDir(), List.of(thinka, mek));

        agentService.addPersistentAgent(poAgent);

        mcpConnectionService = new McpConnectionService(sharedToolService, mcpStateChange);

        updateConfig(configuredModel.getConfig());
        initStaticContext();
    }
    
    
    private void initStaticContext() {
        var env = new StaticContextItem();
        
        for (var agent : this.getAgents()) {
            var context = new ArrayList<ContextItem>();
            context.add(env);
            context.addAll(this.workspaceMemoryTool.get());
            agent.setStaticContext(context);
            agent.setTurnContextSupplier(() -> get());
        }
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
    }


    private void updateActiveDiskTools(LlmConfig config) {
        if (config.isDiskToolsEnabled()) {
            if (sharedToolService.getTool(DiskFileWriteTool.class).isEmpty()) {
                sharedToolService.addTool(diskFileWriteTool);
                sharedToolService.addTool(diskFileReadTool);
                sharedToolService.addTool(diskGrepTool);
            }
        } else {
            if (sharedToolService.getTool(DiskFileWriteTool.class).isPresent()) {
                sharedToolService.removeTool(diskFileWriteTool);
                sharedToolService.removeTool(diskFileReadTool);
                sharedToolService.removeTool(diskGrepTool);
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
     * @return <code>true</code> if changed
     */
    public boolean setProject(IProject project) {
        this.plan = null; // stale reference — restore on next agent activation if needed

        var projectPath = JdtUtil.pathOf(project);

        workspaceWriteFilesTool.setCurrentProject(project);

        // disk tools work with the disk path not eclipse path
        projectPath = JdtUtil.diskPathOf(project);
        if (projectPath != null) {
            diskFileWriteTool.setWorkingDir(projectPath);
            diskFileReadTool.setWorkingDir(projectPath);
            diskGrepTool.setWorkingDir(projectPath);
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
        if (this.plan != null) { // this.plan — not disk, avoids stale project reference
            planText = readPlan();
            planPath = JdtUtil.pathOf(this.plan);

            _handoffLine = new SimpleContextItem("Handover reference " + getActiveAgent().getName(), 
                    "Handover from " + getActiveAgent().getName() + " " + planPath);
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

    /**
     * Persists a model change for whichever agent is active: a built-in mode saves to the
     * per-mode preference, a custom agent writes {@code model:} back into its {@code AGENT.md}.
     */
    public void setModel(AiModel model) {
        if (model == null) return;
        var active = getActiveAgent();
        LlmPreferenceInitializer.saveModel(model.getId(), active);
    }

    public void withThinkSupported(Boolean supported) {
        if (supported == null) supported = Boolean.FALSE;
        var active = getActiveAgent();
        boolean prefChanged = LlmPreferenceInitializer.saveThinkSupported(supported, active);
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
            if (this.plan == null) this.plan = getProject().getFile(PlanTool.OVERVIEW_FILE);
        }
    }

    public void onPlanSaved(IFile planFile) {
        this.plan = planFile;
    }

    public void clear() {
        this.plan = null;
        getActiveAgent().clear();
    }
    
    public void clearAll() {
        this.plan = null;
        this.agentService.getAgents().forEach(AiAgent::clear);
    }
    
    public List<AiAgent> getAgents() {
        return this.agentService.getAgents();
    }

    private String readPlan() {
        if (this.plan == null || !this.plan.exists()) return "";
        return "Plan: " + JdtUtil.pathOf(plan) + System.lineSeparator() + "---" + System.lineSeparator() + System.lineSeparator()
            + IoUtils.readString(plan);
    }

    private List<ContextItem> get() {
        var result = new LinkedList<ContextItem>();

        var agent = getActiveAgent();

        if ((agent instanceof AiScaffoldAgent)) {
            var configDir = getConfig().getConfigDir();
            if (configDir == null) return List.of(new SimpleContextItem("No config dir set -- inform the user to check the config"));

            result.add(new SimpleContextItem("Parent folder of disk tools set to the config dir you should work with relative paths directly in this folder only."));

            var orders = new StringBuilder();
            try {
                var readTool = scaffoldAgent.getToolService().getTool(DiskFileReadTool.class);
                if (readTool.isPresent()) {
                    orders.append("Directory listing of the config dir ").append(configDir).append(":").append(System.lineSeparator());
                    orders.append(readTool.get().diskListDirectory(LlmConfig.AGENT_DIRECTORY)).append(System.lineSeparator());
                    orders.append(readTool.get().diskListDirectory(LlmConfig.COMMAND_DIRECTORY)).append(System.lineSeparator());
                    orders.append(readTool.get().diskListDirectory(LlmConfig.SKILL_DIRECTORY)).append(System.lineSeparator());
                }
                result.add(new SimpleContextItem("Scaffold env. info", orders.toString()));
                orders.setLength(0);
            } catch (IllegalArgumentException e) {
                LOG.info("Directories missing " + e.getMessage());
            }

            // Available tools from sharedToolService
            orders.append("Available tools:").append(System.lineSeparator());
            for (var spec : sharedToolService.toolSpecifications()) {
                orders.append("- ").append(spec.name()).append(": ").append(spec.description()).append(System.lineSeparator());
            }
            
            result.add(new SimpleContextItem("Scaffold tool names", orders.toString()));
        } else {
            if (_handoffLine == null) {
                final var plan = getProject().getFile(PlanTool.OVERVIEW_FILE);
                if (plan != null && plan.exists()) {
                    result.add(new ContextItem() {
                        @Override
                        public String label() {
                            return "Plan reference " + dedupKey();
                        }
                        @Override
                        public String dedupKey() {
                            return JdtUtil.pathOf(plan);
                        }
                        @Override
                        public String render() {
                            if (!plan.exists()) return null;
                            return "Existing plan found: " + JdtUtil.pathOf(plan);
                        };
                    });
                }
            } else {
                // Consume handoff line once (set by onHandoff, survives compaction)
                result.add(_handoffLine);
                _handoffLine = null;
            }
            result.addAll(AgentsMdContextItem.itemsFor(agent.getName(), this::getProject));
        }
        
        if ((agent instanceof AiPoAgent)) {
            result.add(new EclipseFileContextItem("docs/memory.md", this::getProject));
            result.add(new EclipseFileContextItem("docs/index.md", this::getProject));
        }

        result.addAll(userContext.get());

        return result;
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
