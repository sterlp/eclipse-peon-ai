package org.sterl.llmpeon.parts;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.sterl.llmpeon.AiDeveloperService;
import org.sterl.llmpeon.AiPlannerService;
import org.sterl.llmpeon.PeonMode;

import dev.langchain4j.data.message.UserMessage;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.parts.agent.AgentModeService;
import org.sterl.llmpeon.parts.agentsmd.AgentsMdService;
import org.sterl.llmpeon.parts.config.LlmPreferenceInitializer;
import org.sterl.llmpeon.parts.config.McpConnectionService;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.tools.AgentModeTool;
import org.sterl.llmpeon.parts.tools.EclipseBuildTool;
import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;
import org.sterl.llmpeon.parts.tools.EclipseGrepTool;
import org.sterl.llmpeon.parts.tools.EclipseRunTestTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceWriteFileTool;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.template.TemplateContext;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;

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

    private final ToolService toolService;
    private final SkillService skillService;
    private final TemplateContext context;
    private final AgentsMdService agentsMdService;
    private final AiDeveloperService developerService;
    private final AiPlannerService plannerService;
    private final AgentModeService agentMode;
    private final AgentModeTool agentModeTool;
    private final McpConnectionService mcpConnectionService;
    
    private final EclipseWorkspaceWriteFileTool workspaceWriteFilesTool;
    private final DiskFileWriteTool diskFileWriteTool;
    private final DiskFileReadTool diskFileReadTool;

    /** Written from Eclipse DI thread, read from background LLM job threads. */
    private final AtomicReference<IProject> currentProject = new AtomicReference<>();

    /** Written from preference-change listener, read during every LLM call setup. */
    private final AtomicReference<LlmConfig> currentConfig;

    /**
     * Package-private constructor for unit tests — accepts pre-built services to avoid
     * hard Eclipse dependencies ({@code EclipseUtil.workspacePath()}, Eclipse tools, etc.).
     */
    PeonAiService(AiPlannerService plannerService, AiDeveloperService developerService) {
        this.plannerService = plannerService;
        this.developerService = developerService;
        this.toolService = null;
        this.skillService = null;
        this.context = null;
        this.agentsMdService = null;
        this.agentMode = null;
        this.agentModeTool = null;
        this.mcpConnectionService = null;
        this.workspaceWriteFilesTool = null;
        this.diskFileWriteTool = null;
        this.diskFileReadTool = null;
        this.currentConfig = new AtomicReference<>(null);
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
        LlmConfig config = LlmPreferenceInitializer.buildWithDefaults();
        currentConfig = new AtomicReference<>(config);

        var rootPath            = EclipseUtil.workspacePath();
        toolService             = new ToolService();
        skillService            = new SkillService();
        context                 = new TemplateContext(rootPath);
        agentsMdService         = new AgentsMdService();

        workspaceWriteFilesTool = new EclipseWorkspaceWriteFileTool();
        toolService.addTool(workspaceWriteFilesTool);
        toolService.addTool(new EclipseWorkspaceReadFileTool());
        
        diskFileWriteTool = new DiskFileWriteTool(rootPath);
        toolService.addTool(diskFileWriteTool);
        diskFileReadTool = new DiskFileReadTool(rootPath);
        toolService.addTool(diskFileReadTool);

        toolService.addTool(new EclipseBuildTool());
        toolService.addTool(new EclipseGrepTool());
        toolService.addTool(new EclipseRunTestTool());
        toolService.addTool(new EclipseCodeNavigationTool());

        developerService = new AiDeveloperService(config, toolService, skillService, context);
        plannerService   = new AiPlannerService(config, toolService, skillService, context);

        // Agent mode uses separate instances with isolated memory
        var agentDev  = new AiDeveloperService(config, toolService, skillService, context);
        var agentPlan = new AiPlannerService(config, toolService, skillService, context);
        agentMode     = new AgentModeService(agentPlan, agentDev, sendTrigger, openInEditorCallback);
        agentModeTool = new AgentModeTool(agentMode);

        mcpConnectionService = new McpConnectionService(toolService, mcpStateChange);
    }

    /**
     * Updates the active project across all project-aware services.
     * Safe to call from any thread — each downstream setter manages its own state.
     */
    public void setProject(IProject project) {
        var projectPath = JdtUtil.pathOf(project);
        currentProject.set(project);
        agentsMdService.load(project);
        agentMode.setProject(project);  // volatile write inside AgentModeService
        context.setWorkingDir(projectPath);
        
        workspaceWriteFilesTool.setCurrentProject(project);
        
        diskFileWriteTool.setWorkingDir(projectPath);
        diskFileReadTool.setWorkingDir(projectPath);
    }

    /**
     * Propagates a new {@link LlmConfig} to all chat services and refreshes skills.
     * Safe to call from any thread.
     */
    public void updateConfig(LlmConfig config) {
        currentConfig.set(config);
        developerService.updateConfig(config);
        plannerService.updateConfig(config);
        agentMode.updateConfig(config);
        if (config.skillDirectory() != null && !config.skillDirectory().isBlank()) {
            try {
                skillService.refresh(config.skillDirectory());
            } catch (IOException e) {
                throw new RuntimeException("Failed to load skills from " + config.skillDirectory(), e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Business logic
    // -------------------------------------------------------------------------

    /**
     * Performs the PLAN → DEV handoff: clears the developer service, adds the
     * "Start Implementation" trigger, and appends the last planner message (the
     * self-contained implementation plan) if one exists.
     */
    public void startImplementation() {
        if (currentMode == PeonMode.AGENT) {
            aiService.getAgentMode().startImplementation();
            refreshChat();
            return;
        }
        developerService.clear();
        // LM Studio is sometimes bugged, if the first message is no user message ... :-/
        developerService.addMessage(UserMessage.from("Reading this plan:"));
        plannerService.extractLastPlan().ifPresent(developerService::addMessage);
        // some Qwen model need a use message as last message
        developerService.addMessage(UserMessage.from("Start implementation"));
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
        return currentConfig.get();
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

    public TemplateContext getTemplateContext() {
        return context;
    }

    public AgentsMdService getAgentsMdService() {
        return agentsMdService;
    }

    public McpConnectionService getMcpConnectionService() {
        return mcpConnectionService;
    }
}
