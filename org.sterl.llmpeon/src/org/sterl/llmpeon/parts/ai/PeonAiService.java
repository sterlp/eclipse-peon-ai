package org.sterl.llmpeon.parts.ai;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Platform;
import org.sterl.llmpeon.AgentService;
import org.sterl.llmpeon.agent.AbstractAgent;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.AiReviewAgent;
import org.sterl.llmpeon.agent.NamedAgent;
import org.sterl.llmpeon.ai.AgentModelConfig;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.command.CommandService;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.context.UserContext;
import org.sterl.llmpeon.mcp.DefaultCodegraphProbe;
import org.sterl.llmpeon.parts.AIChatView;
import org.sterl.llmpeon.parts.PeonConstants;
import org.sterl.llmpeon.parts.ai.component.AgentContextComponent;
import org.sterl.llmpeon.parts.ai.component.BuildPoAgentComponent;
import org.sterl.llmpeon.parts.ai.component.SharedToolsComponent;
import org.sterl.llmpeon.parts.config.LlmPreferenceInitializer;
import org.sterl.llmpeon.parts.config.McpConnectionService;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.tools.AskUserTool;
import org.sterl.llmpeon.parts.tools.PlanTool;
import org.sterl.llmpeon.parts.tools.memory.WorkspaceMemoryTool;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.llmpeon.scaffold.AiScaffoldAgent;
import org.sterl.llmpeon.scaffold.ReloadConfigTool;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.tools.SearchAgentTool;
import org.sterl.llmpeon.tool.tools.ShellTool;
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

    /**
     * The last-constructed service. JFace creates the preference pages without an injection point,
     * so the config UI resolves the service statically. The last constructed service wins — the MCP
     * connection state is per view ToolService, so a preview can show another view's MCP tool set.
     */
    private static volatile PeonAiService current;

    /** @see #current — null before any view (or headless build) constructed a service. */
    public static PeonAiService current() {
        return current;
    }

    /** Test seam: reset the static handle (OSGi tests construct a service per test). */
    public static void clearCurrent() {
        current = null;
    }

    private final UserContext userContext = new UserContext();

    private final AgentService agentService;
    private final ConfiguredChatModel configuredModel;

    /** Shared tool service used by dev/plan/custom agents (MCP, AskUserTool, ShellTool, workspace disk tools). */
    private ToolService sharedToolService;
    private AiScaffoldAgent scaffoldAgent;

    /**
     * R17: the user skills root {@code ~/.agents/skills}. Fixed at the home dir (never a
     * preference) — the deprecated {@code <configDir>/skills} root follows the preference.
     */
    private final Path agentsSkillsDir = Path.of(System.getProperty("user.home")).resolve(SkillService.USER_SKILLS_DIR);

    private SkillService skillService;
    private CommandService commandService;

    private McpConnectionService mcpConnectionService;

    /** ADR-0068: built-in Codegraph connection — own MCP group, failure-isolated from the user MCP group. */
    private CodegraphConnectionService codegraph;

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

        this(sendTrigger, openInEditorCallback, mcpStateChange, onAgentReload, LlmPreferenceInitializer.buildWithDefaults().build(), null);
    }

    public PeonAiService(Runnable sendTrigger,
            Consumer<IFile> openInEditorCallback,
            Consumer<Boolean> mcpStateChange,
            Runnable onAgentReload,
            ConfiguredChatModel configuredModel) {

        this(sendTrigger, openInEditorCallback, mcpStateChange, onAgentReload, configuredModel, null);
    }

    /**
     * @param questionPresenter presents the agent's questions in the UI (the view's question widget).
     *        Null in headless builds → no agent gets the {@link org.sterl.llmpeon.parts.tools.AskUserTool}
     *        (empty = unset).
     */
    public PeonAiService(Runnable sendTrigger,
            Consumer<IFile> openInEditorCallback,
            Consumer<Boolean> mcpStateChange,
            Runnable onAgentReload,
            ConfiguredChatModel configuredModel,
            AskUserTool.QuestionPresenter questionPresenter) {

        var config              = configuredModel.getConfig();
        this.configuredModel    = configuredModel;
        skillService            = newSkillService(agentsSkillsDir);
        commandService          = new CommandService();
        sharedTools             = new SharedToolsComponent(skillService, commandService);
        sharedToolService       = sharedTools.toolService();
        // R3 (shell-tool.md): default shell working dir = active project's disk path, evaluated at
        // call time — project switch (or unpinned selection change) takes effect without any refresh.
        sharedToolService.getTool(ShellTool.class)
                .ifPresent(t -> t.setDefaultWorkingDir(this::projectWorkDir));
        workspaceMemoryTool     = sharedTools.workspaceMemoryTool();

        planTool = new PlanTool(this);
        sharedToolService.addTool(planTool);

        // askUser (R17): one tool instance for ALL agents — the view's question widget is the
        // presenter, so Jon, dev/plan agents and custom agents share the same widget + queue-safety.
        // Null in headless builds → no agent gets the tool (empty = unset).
        if (questionPresenter != null) {
            sharedToolService.addTool(new AskUserTool(questionPresenter));
        }

        // ADR-0041 R2: agent history lives in the workspace metadata state (not ~/.peon/state).
        var stateDir = Platform.getStateLocation(Platform.getBundle(PeonConstants.PLUGIN_ID))
                .append("state").toFile().toPath();

        agentService  = new AgentService(true,
                config.getConfigDir().resolve(LlmConfig.AGENT_DIRECTORY), sharedToolService, configuredModel, stateDir);

        // R15: a successful scaffold disk write deterministically refreshes the skill view.
        scaffoldAgent = new AiScaffoldAgent(configuredModel, skillService);
        // ADR-0043: the scaffold may write into the user skills root and into the .agents/skills
        // dir of every open project (read at validate time, R10/R17) — in addition to the config dir.
        scaffoldAgent.setAdditionalWriteRootsSupplier(this::additionalSkillsWriteRoots);
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

        var poAgent = new BuildPoAgentComponent(configuredModel, this::getProject, sharedTools, stateDir)
                .build();

        agentService.addPersistentAgent(poAgent);

        // ADR-0067 R20: Jon's own registry follows the disk-tool gate like the shared service.
        // The persistent poAgent instance survives clearAgents/reloads — stable reference.
        sharedTools.registerDiskGatedRegistry(poAgent.getToolService());

        // ADR-0067: MCP connects every agent-facing registry (shared + Jon's own).
        mcpConnectionService = new McpConnectionService(List.of(sharedToolService, poAgent.getToolService()), mcpStateChange);

        // ADR-0068: Codegraph built-in — same agent-facing registries, own group + probe.
        codegraph = new CodegraphConnectionService(this::getProject,
                List.of(sharedToolService, poAgent.getToolService()), new DefaultCodegraphProbe());
        // Context assembly (turn context, static bake, plan/handoff state) — pure component,
        // wired with values + lazy suppliers only (no service dependency). The codegraph item
        // (R-CG-7 / R-CG-12) reads the live state + instructions and is the single codegraph line.
        var codegraphItem = new CodegraphContextItem(codegraph::state);
        agentContext = new AgentContextComponent(this::getProject, planTool::visiblePlan,
                workspaceMemoryTool, userContext,
                scaffoldAgent, sharedToolService,
                this::getActiveAgent, configuredModel::getConfig, this::getAgents,
                codegraphItem);

        updateConfig(configuredModel.getConfig());
        // Default turn-context supplier for every agent present at construction. New custom agents
        // (created by a later updateConfig/refresh) receive it via call() before each turn.
        for (var agent : this.getAgents()) {
            agent.setTurnContextSupplier(() -> get());
        }
        current = this;
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
            skillService.refresh(dir);    // config slot: set + refresh (compat semantics)
            skillService.refreshAll();    // R3: the project slot is refreshed as well
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
        sharedTools.javaDebugTool().setCurrentProject(project);
        // disk tools work with the disk path not eclipse path
        projectPath = JdtUtil.diskPathOf(project);
        if (projectPath != null) {
            sharedTools.diskFileWriteTool().setWorkingDir(projectPath);
            sharedTools.diskFileReadTool().setWorkingDir(projectPath);
            sharedTools.diskGrepTool().setWorkingDir(projectPath);
            sharedTools.docsLinterTool().setWorkingDir(projectPath);
        }
        // ADR-0042: project skill slot — this is the single choke point that replaces it;
        // null project = empty slot, the config slot stays untouched (R2b)
        try {
            skillService.setProjectSkillsDir(projectPath == null ? null : Path.of(projectPath).resolve(SkillService.PROJECT_SKILLS_DIR));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load project skills from " + projectPath, e);
        }
        var changed = this.userContext.setCurrentProject(project);
        if (changed) codegraph.projectChanged();
        return changed;
    }

    /**
     * ADR-0043 + R17: the writable skill roots at validate time — the user root, then the
     * {@code .agents/skills} dir of every open project. The scaffold only learns which roots are
     * writable, never where they came from.
     */
    private List<Path> additionalSkillsWriteRoots() {
        var roots = new ArrayList<Path>();
        roots.add(agentsSkillsDir);
        roots.addAll(projectSkillsRoots());
        return roots;
    }

    /** ADR-0043: the {@code .agents/skills} dir of every open project, read at validate time (R10). */
    private List<Path> projectSkillsRoots() {
        return EclipseUtil.openProjects().stream()
                .map(JdtUtil::diskPathOf)
                .filter(Objects::nonNull)
                .map(diskPath -> Path.of(diskPath).resolve(SkillService.PROJECT_SKILLS_DIR))
                .toList();
    }

    /** R17: the user root loads eagerly with the service — an I/O failure is fatal, reported like {@link #updateConfig(LlmConfig)}. */
    private static SkillService newSkillService(Path userSkillsDir) {
        try {
            return new SkillService(userSkillsDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load skills from " + userSkillsDir, e);
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
        String planPath;
        var plan = agentContext.planRef();
        if (plan != null) { // workspace plan ref — not disk, avoids stale project reference
            // R-PT-4: the raw file body decides — readPlan() prefixes its header, so an empty
            // overview.md would never look empty when judged through it
            planText = agentContext.planContent().isBlank() ? null : agentContext.readPlan();
            planPath = JdtUtil.pathOf(plan);
        } else {
            var chatPlan = getActiveAgent().getMemory().getLastOf(AiMessage.class);
            if (chatPlan == null) planText = null;
            else planText = chatPlan.text();
            
            planPath = "Handover from " + getActiveAgent().getName() + ":";
        }

        // R-PT-4: an archived or deleted plan reads back empty — that is "no plan", never a handoff
        // that clears the target memory and reports success on nothing.
        if (planText == null || planText.isBlank()) return false;

        if (plan != null) agentContext.armHandoffLine(getActiveAgent().getName(), planPath);
        // and directly add the plan too ...
        toAgent.get().clear();
        toAgent.get().getMemory().add(UserMessage.from(planPath + System.lineSeparator() + planText));
        this.agentService.setActiveAgent(toAgent.get());
        
        return true;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void applyMcpConfig() {
        mcpConnectionService.applyConfig();
    }

    public void disconnectMcp() {
        mcpConnectionService.disconnect();
        codegraph.disconnect();
    }

    // -------------------------------------------------------------------------
    // Accessors — all non-null after construction
    // -------------------------------------------------------------------------

    public IProject getProject() {
        return userContext.getCurrentProject();
    }

    /** ADR-0068: the built-in Codegraph connection service (icon state, context line, instructions). */
    public CodegraphConnectionService getCodegraphConnectionService() {
        return codegraph;
    }

    /** Header-icon state listener — the view wraps the callback in the UI thread (D9/I5). */
    public void setCodegraphStateListener(Consumer<CodegraphConnectionService.State> listener) {
        codegraph.setOnStateChange(listener);
    }

    /** R3: disk path of the active project (pin-aware) at call time; null = no project selected. */
    private Path projectWorkDir() {
        var diskPath = JdtUtil.diskPathOf(getProject());
        return diskPath == null ? null : Path.of(diskPath);
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

    /** One resolved preview entry (R-ATC-8): the tool name plus whether it comes from a connected MCP server. */
    public record PreviewTool(String name, boolean mcp) {}

    /**
     * The agents shown in the header status widget: when Jon is active, his
     * {@link AiPoAgent#getTeam() team} (Jon + Plan, Dev, Review); for every other
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

    /**
     * R-ATC-8: the preview target for a tool-allowlist slot. The PO-Team slave is preferred over the
     * standalone persistent agent — it carries the stricter class filter (noPrivilegedTools), so the
     * preview never claims a tool the agent actually cannot call. {@code search} and {@code compact}
     * have no AiAgent (search previews via {@link #searchAgentTool()}, compact has no tools field).
     */
    public AiAgent agentForPreviewSlot(String slotId) {
        if (slotId == null || AgentModelConfig.SEARCH.equals(slotId) || AgentModelConfig.COMPACT.equals(slotId)) {
            return null;
        }
        var slave = agentService.get(AiPoAgent.NAME)
                .filter(AiPoAgent.class::isInstance)
                .map(AiPoAgent.class::cast)
                .map(po -> po.getTeam().stream()
                        .map(NamedAgent::agent)
                        .filter(agent -> matchesSlot(slotId, agent))
                        .findFirst()
                        .orElse(null))
                .orElse(null);
        if (slave != null) {
            return slave;
        }
        return agentService.get(standaloneName(slotId)).orElse(null);
    }

    private static boolean matchesSlot(String slotId, AiAgent agent) {
        return switch (slotId) {
            case AgentModelConfig.DEV -> agent instanceof AiDevAgent;
            case AgentModelConfig.PLAN -> agent instanceof AiPlanAgent;
            case AgentModelConfig.REVIEW -> agent instanceof AiReviewAgent;
            case AgentModelConfig.PO -> agent instanceof AiPoAgent;
            default -> false;
        };
    }

    private static String standaloneName(String slotId) {
        return switch (slotId) {
            case AgentModelConfig.DEV -> AiDevAgent.NAME;
            case AgentModelConfig.PLAN -> AiPlanAgent.NAME;
            case AgentModelConfig.REVIEW -> AiReviewAgent.NAME;
            case AgentModelConfig.PO -> AiPoAgent.NAME;
            default -> null;
        };
    }

    /** The shared {@link SearchAgentTool} — the preview target of the search slot (R-ATC-8). */
    public Optional<SearchAgentTool> searchAgentTool() {
        return sharedToolService.getTool(SearchAgentTool.class);
    }

    /**
     * R-ATC-8: the active tools for the given slot and TYPED raw CSV — the one resolution
     * behind every preview button (dev/plan/po/review via {@link #agentForPreviewSlot(String)},
     * search via {@link #searchAgentTool()}). Each entry carries the MCP flag so the dialog can
     * mark connected-MCP tools the way the header hammer does. {@code null} when no preview
     * target exists.
     */
    public List<PreviewTool> previewToolsFor(String slotId, String rawCsv) {
        if (slotId == null) {
            return null;
        }
        if (AgentModelConfig.SEARCH.equals(slotId)) {
            var search = searchAgentTool();
            if (search.isEmpty()) {
                return null;
            }
            // SearchAgentTool is registered on the shared service with itself as its tool service
            // (ToolService ctor), so its connected-MCP set is the shared service's.
            return previewEntries(search.get().resolveActiveTools(rawCsv), sharedToolService.mcpToolNames());
        }
        var agent = agentForPreviewSlot(slotId);
        if (!(agent instanceof AbstractAgent a)) {
            return null;
        }
        return previewEntries(a.resolveActiveTools(rawCsv), a.getToolService().mcpToolNames());
    }

    private static List<PreviewTool> previewEntries(List<String> names, List<String> mcpNames) {
        var mcp = Set.copyOf(mcpNames);
        return names.stream().map(n -> new PreviewTool(n, mcp.contains(n))).toList();
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
        return org.sterl.llmpeon.prompt.PromptLoader.load("scaffold-tutorial.md");
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

        return org.sterl.llmpeon.prompt.PromptLoader.load("po-tutorial.md");
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

    /**
     * R-PT-4 (org.sterl.llmpeon/docs/plan-tool.md): the archive moved the file away, so the
     * reference is released here — same release as {@code clear()} via {@code agentContext.clearPlan()}
     * — and a following handoff cannot see a plan that no longer exists.
     */
    public void onPlanArchived() {
        agentContext.clearPlan();
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
