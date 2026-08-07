package org.sterl.llmpeon.parts;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.sterl.llmpeon.AgentService;
import org.sterl.llmpeon.StandingOrdersBuilder.MessageProvider;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.AiPoAgent;
import org.sterl.llmpeon.agent.NamedAgent;
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
import org.sterl.llmpeon.parts.tools.PlanReadTool;
import org.sterl.llmpeon.parts.tools.PlanTool;
import org.sterl.llmpeon.parts.tools.memory.WorkspaceMemoryTool;
import org.sterl.llmpeon.scaffold.AiScaffoldAgent;
import org.sterl.llmpeon.scaffold.ReloadConfigTool;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.JonDelegateTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import org.sterl.llmpeon.tool.tools.DiskGrepTool;
import org.sterl.llmpeon.tool.tools.SearchAgentTool;
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
 */
public class PeonAiService implements MessageProvider {

    /** Jon's RAM-only slaves compact at 70% of the shared budget so their throw-away context stays lean. */
    private static final double SLAVE_COMPACT_FACTOR = 0.7;

    private final AgentService agentService;
    private final ConfiguredChatModel configuredModel;

    /** Shared tool service used by dev/plan/custom agents (MCP, AskUserTool, ShellTool, workspace disk tools). */
    private final ToolService sharedToolService;
    private final AiScaffoldAgent scaffoldAgent;
    /** Jon's delegate tool — the header roster peeks its Plan/Dev slaves when Jon is the active agent. */
    private final JonDelegateTool jonDelegateTool;

    private final SkillService skillService;
    private final CommandService commandService;
    
    private final AgentsMdService agentsMdService;

    private final McpConnectionService mcpConnectionService;

    private final PlanTool planTool;
    
    private final EclipseWorkspaceWriteFileTool workspaceWriteFilesTool;
    private final EclipseWorkspaceReadFileTool workspaceReadFilesTool;
    private final EclipseGrepTool eclipseGrepTool;
    private final DiskFileWriteTool diskFileWriteTool;
    private final DiskFileReadTool diskFileReadTool;
    private final DiskGrepTool diskGrepTool;
    
    private final ReloadConfigTool reloadConfigTool;
    
    private final WorkspaceMemoryTool workspaceMemoryTool = WorkspaceMemoryTool.getInstance();

    private  volatile IProject currentProject = null;
    
    private IFile plan;

    /** Transient standing-order line set on handoff, consumed once by {@link #get()}. */
    private volatile String _handoffLine;

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

        var config = LlmPreferenceInitializer.buildWithDefaults();
        configuredModel = config.build();

        var rootPath            = EclipseUtil.workspacePath();
        sharedToolService       = new ToolService();
        skillService            = new SkillService();
        commandService          = new CommandService();
        agentsMdService         = new AgentsMdService();
        agentsMdService.setAgentNameSupplier(() -> getActiveAgent().getName());

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

        // Peon-PO (Jon): own curated tool set — docs read/write/grep only (no shell/build/test/plan).
        // Reuses the shared Eclipse tool instances so project updates + the per-request write validator
        // (WriteValidator.DOCS from AiPoAgent) apply automatically.
        var poToolService = new ToolService(false);
        poToolService.addTool(workspaceReadFilesTool);
        poToolService.addTool(workspaceWriteFilesTool);
        poToolService.addTool(eclipseGrepTool);
        // Jon curates the shared memory (write) — he knows it is shared by all agents, so writing it
        // steers his slaves and the other agents (they only ever READ it, via standing-order injection).
        poToolService.addTool(workspaceMemoryTool);
        // Read-only plan access: hasPlan (returns the path) + planRead. Jon reviews & hands the path
        // to buildWithAgent but never writes the plan himself — that is his Peon-Plan agent's job.
        poToolService.addTool(new PlanReadTool(planTool));
        // Jon's Plan/Dev slaves: RAM-only (2-arg ctor — no history file, ADR-0024), reusing the shared
        // Eclipse tool set. Lazy singletons via the factory so Jon-in-core stays testable headless.
        // Slaves may READ the shared memory (injected as a standing order) but never WRITE it — strip the
        // memory-write tool from their effective set; only Jon curates the shared memory.
        Predicate<SmartToolExecutor> noMemoryWrite = t -> !(t.getTool() instanceof WorkspaceMemoryTool);
        // Eager shared slaves (ADR-0025): created once here and handed — as the same NamedAgent objects —
        // to both the delegate tool (which drives them) and AiPoAgent (which exposes them via getTeam()).
        AiAgent planSlave = new AiPlanAgent(configuredModel, sharedToolService, SLAVE_COMPACT_FACTOR) {
            @Override protected Predicate<SmartToolExecutor> getToolFilter() {
                return super.getToolFilter().and(noMemoryWrite);
            }
        };
        AiAgent devSlave = new AiDevAgent(configuredModel, sharedToolService, SLAVE_COMPACT_FACTOR) {
            @Override protected Predicate<SmartToolExecutor> getToolFilter() {
                return super.getToolFilter().and(noMemoryWrite);
            }
        };
        var thinka = new NamedAgent("Da Thinka", planSlave);
        var mek = new NamedAgent("Da Mek", devSlave);
        // Slaves also need the same relevant context as the active agent (Jon gets it via userContext;
        // they get it folded into their injected standing orders — read-only, like the shared memory):
        // the shared memory, the base AGENTS.md ground rules, and the selected project.
        jonDelegateTool = new JonDelegateTool(thinka, mek, () -> {
            var orders = new java.util.ArrayList<String>(workspaceMemoryTool.get());
            var agentsMd = agentsMdService.getBaseAgentsMd();
            if (agentsMd != null) orders.add(agentsMd);
            if (currentProject != null) orders.add("Selected project:" + System.lineSeparator()
                    + EclipseUtil.projectInfo(currentProject));
            return orders;
        });
        poToolService.addTool(jonDelegateTool);
        // Jon's own throw-away research sub-agent (Da Sniffa) — searches with his read/grep tools to
        // save his context; stateless one-shot, not one of his persistent slaves.
        poToolService.addTool(new SearchAgentTool(poToolService));
        var poAgent = new AiPoAgent(configuredModel, poToolService, config.getConfigDir(), List.of(thinka, mek));
        agentService.addPersistentAgent(poAgent);

        mcpConnectionService = new McpConnectionService(sharedToolService, mcpStateChange);

        updateConfig(configuredModel.getConfig());
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
     */
    public void setProject(IProject project) {
        this.plan = null; // stale reference — restore on next agent activation if needed
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

        String planText;
        if (this.plan != null) { // this.plan — not disk, avoids stale project reference
            planText = readPlan();
        } else {
            var chatPlan = getActiveAgent().getMemory().getLastOf(AiMessage.class);
            if (chatPlan == null) planText = null;
            else planText = chatPlan.text();
        }

        if (planText != null) {
            if (this.plan != null) _handoffLine = "Handover from " + getActiveAgent().getName() + " " + JdtUtil.pathOf(this.plan);

            toAgent.get().clear();
            toAgent.get().getMemory().add(UserMessage.from(
                    "Handover from " + getActiveAgent().getName() + System.lineSeparator()
                    + planText));

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
     * active, empty memory, and no {@code docs/index.md}. The clean complement to
     * {@link #docsIndexSeedForFirstMessage()} (which fires when the index <em>exists</em>): if there is
     * a map, Jon navigates it; if there is none, we greet the user and explain how Jon works. Returns
     * {@code null} otherwise. Shown on activation like {@link #getScaffoldTutorial()}, not sent to the LLM.
     */
    public String getPoTutorial() {
        var agent = getActiveAgent();
        if (!(agent instanceof AiPoAgent)) return null;
        if (agent.getMemory().size() > 0) return null;
        var project = getProject();
        if (project == null) return null;

        var index = project.getFile("docs/index.md");
        if (index != null && index.exists()) return null; // there is a map -> the seed handles it

        return org.sterl.llmpeon.prompt.PromptLoader.load("po-tutorial.txt");
    }

    private void preloadPlanIfNeeded() {
        if (!planTool.hasPlan()) return;

        var agent = getActiveAgent();
        if (agent instanceof AiPlanAgent planAgent) {
            if (this.plan == null) this.plan = getProject().getFile(PlanTool.OVERVIEW_FILE);

            if (planAgent.getMemory().size() == 0) {
                planAgent.getMemory().add(UserMessage.from(
                        "Current active plan. Use plan* tools to change" + System.lineSeparator() + "---" + System.lineSeparator() + System.lineSeparator()
                        + planTool.planRead()));
            }
        }
    }

    /**
     * The {@code docs/index.md} seed text for Jon's <b>first</b> user message, or {@code null} when it
     * does not apply. The caller attaches it as a <b>one-time standing order</b> so it is folded into
     * the same {@code UserMessage} as the user's question (which stays the last {@code TextContent}) —
     * unlike the Plan agent, whose plan is shown as its own chat message. Guarded on empty memory so
     * only the first message is seeded, and read fresh at send time (not on activation / project-set).
     */
    public String docsIndexSeedForFirstMessage() {
        var agent = getActiveAgent();
        if (!(agent instanceof AiPoAgent)) return null;
        if (agent.getMemory().size() != 0) return null;
        var project = getProject();
        if (project == null) return null;

        var index = project.getFile("docs/index.md");
        if (index == null || !index.exists()) return null;

        return "Docs index (docs/index.md) — the map of all feature docs. Use it to navigate; no need to re-read it."
                + System.lineSeparator() + "---" + System.lineSeparator() + System.lineSeparator()
                + IoUtils.readString(index);
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

    public void setStaticContext(List<ChatMessage> content) {
        this.agentService.getAgents().forEach(a -> a.setStaticContext(content));
        // Jon's RAM slaves are not registered in agentService, so give them the same static context
        // (date/OS + file-access rules) directly — Inc 2, docs/sklaven-kontext-plan.md.
        jonDelegateTool.getPlanSlave().setStaticContext(content);
        jonDelegateTool.getDevSlave().setStaticContext(content);
    }

    @Override
    public List<String> get() {
        var result = new LinkedList<String>();
        
        var agent = getActiveAgent();
        if ((agent instanceof AiScaffoldAgent)) {
            var configDir = getConfig().getConfigDir();
            if (configDir == null) return List.of("No config dir set -- inform the user to check the config");
            
            result.add("Parent folder of disk tools set to the config dir you should work with relative paths directly in this folder only.");
            
            var orders = new StringBuilder();
            try {
                var readTool = scaffoldAgent.getToolService().getTool(DiskFileReadTool.class);
                if (readTool.isPresent()) {
                    orders.append("Directory listing of the config dir ").append(configDir).append(":").append(System.lineSeparator());
                    orders.append(readTool.get().diskListDirectory(LlmConfig.AGENT_DIRECTORY)).append(System.lineSeparator());
                    orders.append(readTool.get().diskListDirectory(LlmConfig.COMMAND_DIRECTORY)).append(System.lineSeparator());
                    orders.append(readTool.get().diskListDirectory(LlmConfig.SKILL_DIRECTORY)).append(System.lineSeparator());
                }
                result.add(orders.toString());
                orders.setLength(0);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            
            // Available tools from sharedToolService
            orders.append("Available tools:").append(System.lineSeparator());
            for (var spec : sharedToolService.toolSpecifications()) {
                orders.append("- ").append(spec.name()).append(": ").append(spec.description()).append(System.lineSeparator());
            }
            result.add(orders.toString());
            orders.setLength(0);
            
            return result;
        }

        if (_handoffLine != null) {
             // Consume handoff line once (set by onHandoff, survives compaction)
            result.add(_handoffLine);
            _handoffLine = null;
        }

        if (planTool.hasPlan()) {
            result.add("Plan found: " + JdtUtil.pathOf(getProject().getFile(PlanTool.OVERVIEW_FILE)) + System.lineSeparator()
                    + "If plan* tools are available accessable by them too.");
        }
        return result;

    }
}
