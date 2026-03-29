package org.sterl.llmpeon.parts.agent;

import java.util.ArrayList;
import java.util.function.Consumer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.swt.widgets.Display;
import org.sterl.llmpeon.AbstractChatService;
import org.sterl.llmpeon.AiDeveloperService;
import org.sterl.llmpeon.AiPlannerService;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.tools.AgentModeTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.shared.AiMonitor;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Orchestrates the AGENT mode plan→dev loop.
 * Plan files live inside the current Eclipse project under .plan/.
 */
public class AgentModeService {

    public enum Phase { PLANNING, IMPLEMENTING }

    private static final int MAX_RETRIES = 3;

    private static final SystemMessage PLANNER_AGENT_MESSAGE = SystemMessage.from("""
            AGENT MODE — when your plan is complete, call savePlan with the full plan markdown.
            Do not ask — call it automatically as the last action before your reply.
            The plan file path is also available for incremental updates via disk tools.
            If a problem was reported in the conversation, update the plan to address it before saving.
            """);

    private static final SystemMessage DEVELOPER_AGENT_MESSAGE = SystemMessage.from("""
            AGENT MODE — if you cannot proceed after 2 attempts (build failure, missing context,
            conflicting requirements), call reportProblem with a detailed description.
            Do not retry indefinitely. Escalate early so the plan agent can revise the plan.
            """);

    private final AiPlannerService plannerService;
    private final AiDeveloperService developerService;
    private final Runnable sendTrigger;
    private final Consumer<IFile> openInEditorCallback;

    private volatile IProject project;
    private Phase phase = Phase.PLANNING;
    private boolean autonomous = false;
    private int retryCount = 0;
    private volatile boolean implementationRequested = false;

    public AgentModeService(AiPlannerService plannerService, AiDeveloperService developerService,
            Runnable sendTrigger) {
        this(plannerService, developerService, sendTrigger, null);
    }

    public AgentModeService(AiPlannerService plannerService, AiDeveloperService developerService,
            Runnable sendTrigger, Consumer<IFile> openInEditorCallback) {
        this.plannerService = plannerService;
        this.developerService = developerService;
        this.sendTrigger = sendTrigger;
        this.openInEditorCallback = openInEditorCallback;
    }

    // -------------------------------------------------------------------------
    // Project binding
    // -------------------------------------------------------------------------

    public void setProject(IProject project) {
        this.project = project;
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    public boolean currentAgentIsPlanning() {
        return phase == Phase.PLANNING;
    }

    public AbstractChatService getActiveService() {
        return phase == Phase.PLANNING ? plannerService : developerService;
    }

    public ChatResponse call(String message, AiMonitor monitor) {
        var service = getActiveService();
        var orders = new ArrayList<>(service.getStandingOrders());
        orders.add(0, phase == Phase.PLANNING ? PLANNER_AGENT_MESSAGE : DEVELOPER_AGENT_MESSAGE);
        service.setStandingOrders(orders);
        return service.call(message, monitor);
    }

    public void updateConfig(LlmConfig config) {
        plannerService.updateConfig(config);
        developerService.updateConfig(config);
    }

    public void setAutonomous(boolean autonomous) {
        this.autonomous = autonomous;
    }

    public void reset() {
        this.phase = Phase.PLANNING;
        retryCount = 0;
    }

    // -------------------------------------------------------------------------
    // Plan file helpers
    // -------------------------------------------------------------------------

    public boolean overviewExists() {
        if (project == null || !project.isOpen()) return false;
        return project.getFile(AgentModeTool.OVERVIEW_FILE).exists();
    }

    public String readOverview() {
        return IoUtils.readFile(getOverviewFile());
    }

    public String readProblem() {
        IFile f = getProblemFile();
        return f.exists() ? IoUtils.readFile(f) : "";
    }

    public boolean hasPlan() {
        return project != null && getOverviewFile().exists();
    }

    public String planPathHint() {
        if (project == null) return "";
        return "Plan file: " + JdtUtil.pathOf(getOverviewFile())
                + " — use " + EclipseWorkspaceReadFileTool.READ_ECLIPSE_FILE_TOOL
                + " to read it, savePlan to update it.";
    }

    private IFile getOverviewFile() {
        return project.getFile(AgentModeTool.OVERVIEW_FILE);
    }

    private IFile getProblemFile() {
        return project.getFile(AgentModeTool.PROBLEM_FILE);
    }

    public IProject getProject() {
        return project;
    }

    // -------------------------------------------------------------------------
    // Tool callbacks (called from AgentModeTool, on background thread)
    // -------------------------------------------------------------------------

    /** Called by savePlan tool after writing the file. */
    public void onPlanSaved() {
        openInEditor(getOverviewFile());
        if (autonomous) {
            implementationRequested = true;
        }
    }

    /**
     * Returns true if the planner saved a plan while autonomous mode was active.
     * Consumed once by the job-completion handler in AIChatView — resets the flag.
     */
    public boolean consumeImplementationRequest() {
        if (implementationRequested) {
            implementationRequested = false;
            return true;
        }
        return false;
    }

    /** Called by reportProblem tool after writing problem.md. */
    public void onProblemReported() {
        retryCount++;
        if (retryCount >= MAX_RETRIES) {
            openInEditor(getProblemFile());
            plannerService.addMessage(UserMessage.from(
                    "Max retries (" + MAX_RETRIES + ") reached. "
                    + "Review plan problem file `" + AgentModeTool.PROBLEM_FILE + "` — manual intervention required."));
            this.phase = Phase.PLANNING;
            retryCount = 0;
        } else {
            this.phase = Phase.PLANNING;
            plannerService.clear();
            String msg = "Plan review needed (attempt " + retryCount + "/" + MAX_RETRIES + ").\n\n"
                    + "## Current Plan\n" + readOverview() + "\n\n"
                    + "## Problem Reported\n" + readProblem();
            plannerService.addMessage(UserMessage.from(msg));
            Display.getDefault().asyncExec(sendTrigger);
        }
    }

    // -------------------------------------------------------------------------
    // Start implementation (called by "Start Impl." button or autonomous trigger)
    // -------------------------------------------------------------------------

    public void startImplementation() {
        this.phase = Phase.IMPLEMENTING;
        retryCount = 0;
        developerService.clear();
        String plan = overviewExists() ? readOverview() : "(no plan file found)";
        developerService.addMessage(UserMessage.from("Start Implementation\n\n" + plan));
        sendTrigger.run();
    }

    public void openOverviewInEditor() {
        if (overviewExists()) openInEditor(getOverviewFile());
    }

    // -------------------------------------------------------------------------
    // Eclipse editor helper
    // -------------------------------------------------------------------------

    private void openInEditor(IFile file) {
        if (openInEditorCallback != null) openInEditorCallback.accept(file);
    }
}
