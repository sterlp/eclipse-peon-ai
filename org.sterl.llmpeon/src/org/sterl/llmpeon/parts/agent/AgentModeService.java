package org.sterl.llmpeon.parts.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.swt.widgets.Display;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.memory.ThreadSafeMemory;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.tools.AgentModeTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.shared.AiMonitor;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Orchestrates the AGENT mode plan→dev loop.
 * Plan files live inside the current Eclipse project under .plan/.
 */
@Deprecated
public class AgentModeService implements AiAgent {

    public enum Phase { PLANNING, IMPLEMENTING }

    private static final int MAX_RETRIES = 3;

    private static final String SYS_PLAN = """
                When your plan is complete, instead of presenting the plan, call planSave with the full plan automatically as your last action with:
                1. Context
                2. Design decisions
                3. Affected files
                4. Step-by-step changes
                If a problem was reported in the conversation, update the plan to address it before saving.
                """;
    private static final String STANDING_ORDER_PLAN = """
                IN AUTONOMOUS MODE — the user is not at the keyboard. Do not ask clarifying questions.
                Proceed only if you have sufficient context; use the codebase to fill gaps.
                Document assumptions and any unresolved gaps in the Design decisions section.
                """;

    private static final String SYS_DEV = """
                If you cannot proceed after %d attempts (build failure, missing context,
                conflicting requirements), call reportProblem with a detailed description.
                Do not retry indefinitely. Escalate early so the plan agent can revise the plan.
                """.formatted(MAX_RETRIES - 1);
    
    private static final String STANDING_ORDER_DEV = """
                IN AUTONOMOUS MODE — execute the plan without asking question, confirmation or approval.
                """;

    private final AiPlanAgent plannerService;
    private final AiDevAgent developerService;
    private final Runnable sendTrigger;
    private final Consumer<IFile> openInEditorCallback;

    private volatile IProject project;
    private volatile Phase phase = Phase.PLANNING;
    private volatile boolean autonomous = false;
    private volatile int retryCount = 0;
    private volatile boolean implementationRequested = false;

    public AgentModeService(AiPlanAgent plannerService, AiDevAgent developerService,
            Runnable sendTrigger) {
        this(plannerService, developerService, sendTrigger, null);
    }

    public AgentModeService(AiPlanAgent plannerService, AiDevAgent developerService,
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

    public AiAgent getActiveService() {
        return phase == Phase.PLANNING ? plannerService : developerService;
    }

    public ChatResponse call(String message, AiMonitor monitor) {
        var service = getActiveService();
        var orders = new ArrayList<>(service.getUserContextInformations());
        orders.add(phase == Phase.PLANNING ? SYS_PLAN : SYS_DEV);
        if (autonomous) orders.add(phase == Phase.PLANNING ? STANDING_ORDER_PLAN : STANDING_ORDER_DEV);
        service.setUserContextInformations(orders);
        return service.call(message, monitor);
    }

    public void setAutonomous(boolean autonomous) {
        this.autonomous = autonomous;
    }

    public boolean getAutonomous() {
        return this.autonomous;
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
        if (overviewExists())
            try {
                return getOverviewFile().readString();
            } catch (CoreException e) {
                throw new RuntimeException(e);
            }
        else return "";
    }

    public String readProblem() {
        IFile f = getProblemFile();
        try {
            return f.exists() ? f.readString() : "";
        } catch (CoreException e) {
            throw new RuntimeException(e);
        }
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

    /** Called by savePlan tool after writing the file. 
     * @param f */
    public void onPlanSaved(IFile plan) {
        openInEditor(plan);
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

    /**
     * Returns <code>true</code> if a plan was found, otherwise <code>false</code>
     */
    public boolean startImplementation() {
        this.phase = Phase.IMPLEMENTING;
        retryCount = 0;
        if (overviewExists()) {
            developerService.clear();
            developerService.addMessage(UserMessage.from("Start Implementation\n\n" + readOverview()));
            sendTrigger.run();
            return true;
        } else {
            return false;
        }
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

    @Override
    public String getName() {
        return "Agent-Mode";
    }

    @Override
    public String getSystemPrompt() {
        return getActiveService().getSystemPrompt();
    }

    @Override
    public void setUserContextInformations(List<String> userContextInformations) {
        this.developerService.setUserContextInformations(userContextInformations);
        this.plannerService.setUserContextInformations(userContextInformations);
    }

    @Override
    public List<String> getUserContextInformations() {
        return getActiveService().getUserContextInformations();
    }

    @Override
    public void clear() {
        this.getActiveService().clear();
    }

    @Override
    public ThreadSafeMemory getMemory() {
        return getActiveService().getMemory();
    }

    @Override
    public void setStaticContext(List<ChatMessage> staticContext) {
        this.developerService.setStaticContext(staticContext);
        this.plannerService.setStaticContext(staticContext);
    }

    @Override
    public ChatResponse compressContext(AiMonitor monitor) {
        return getActiveService().compressContext(monitor);
    }
}
