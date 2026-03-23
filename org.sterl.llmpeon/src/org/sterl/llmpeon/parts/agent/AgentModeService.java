package org.sterl.llmpeon.parts.agent;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.sterl.llmpeon.ChatService;
import org.sterl.llmpeon.parts.shared.IoUtils;

import dev.langchain4j.data.message.UserMessage;

/**
 * Orchestrates the AGENT mode plan→dev loop.
 * Plan files live inside the current Eclipse project under .plan/.
 */
public class AgentModeService {

    public enum Phase { PLANNING, IMPLEMENTING }

    private static final int MAX_RETRIES = 3;
    private static final String PLAN_DIR = "peon-plan";
    private static final String OVERVIEW_FILE = PLAN_DIR + "/overview.md";
    private static final String PROBLEM_FILE = PLAN_DIR + "/problem.md";

    private final ChatService<?> chatService;
    private final Runnable sendTrigger;

    private IProject project;
    private Phase phase = Phase.PLANNING;
    private boolean autonomous = false;
    private int retryCount = 0;

    public AgentModeService(ChatService<?> chatService, Runnable sendTrigger) {
        this.chatService = chatService;
        this.sendTrigger = sendTrigger;
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

    public void setAutonomous(boolean autonomous) {
        this.autonomous = autonomous;
    }

    public void reset() {
        setPhase(Phase.PLANNING);
        retryCount = 0;
    }

    private void setPhase(Phase newPhase) {
        this.phase = newPhase;
    }

    // -------------------------------------------------------------------------
    // Plan file helpers
    // -------------------------------------------------------------------------

    public boolean overviewExists() {
        if (project == null || !project.isOpen()) return false;
        return project.getFile(OVERVIEW_FILE).exists();
    }

    public String readOverview() {
        return IoUtils.readFile(getOverviewFile());
    }

    public String readProblem() {
        IFile f = getProblemFile();
        return f.exists() ? IoUtils.readFile(f) : "";
    }

    public String planPathHint() {
        if (project == null) return "";
        return "Plan file: /" + project.getName() + "/" + OVERVIEW_FILE
                + " — use readWorkspaceFile / writeWorkspaceFile to read or update it.";
    }

    public IFile getOverviewFile() {
        return project.getFile(OVERVIEW_FILE);
    }

    public IFile getProblemFile() {
        return project.getFile(PROBLEM_FILE);
    }

    public IProject getProject() {
        return project;
    }

    // -------------------------------------------------------------------------
    // Tool callbacks (called from AgentModeTools, on background thread)
    // -------------------------------------------------------------------------

    /** Called by savePlan tool after writing the file. */
    public void onPlanSaved() {
        openInEditor(getOverviewFile());
        if (autonomous) {
            Display.getDefault().asyncExec(this::startImplementation);
        }
    }

    /** Called by reportProblem tool after writing problem.md. */
    public void onProblemReported() {
        retryCount++;
        if (retryCount >= MAX_RETRIES) {
            openInEditor(getProblemFile());
            chatService.addMessage(UserMessage.from(
                    "Max retries (" + MAX_RETRIES + ") reached. "
                    + "Review `" + PROBLEM_FILE + "` — manual intervention required."));
            setPhase(Phase.PLANNING);
            retryCount = 0;
        } else {
            setPhase(Phase.PLANNING);
            chatService.clear();
            String msg = "Plan review needed (attempt " + retryCount + "/" + MAX_RETRIES + ").\n\n"
                    + "## Current Plan\n" + readOverview() + "\n\n"
                    + "## Problem Reported\n" + readProblem();
            chatService.addMessage(UserMessage.from(msg));
            Display.getDefault().asyncExec(sendTrigger);
        }
    }

    // -------------------------------------------------------------------------
    // Start implementation (called by "Start Impl." button or autonomous trigger)
    // -------------------------------------------------------------------------

    public void startImplementation() {
        setPhase(Phase.IMPLEMENTING);
        chatService.clear();
        String plan = overviewExists() ? readOverview() : "(no plan file found)";
        chatService.addMessage(UserMessage.from("Start Implementation\n\n" + plan));
        sendTrigger.run();
    }

    public void openOverviewInEditor() {
        if (overviewExists()) openInEditor(getOverviewFile());
    }

    // -------------------------------------------------------------------------
    // Eclipse editor helper
    // -------------------------------------------------------------------------

    private void openInEditor(IFile file) {
        Display.getDefault().asyncExec(() -> {
            try {
                IWorkbenchPage page = PlatformUI.getWorkbench()
                        .getActiveWorkbenchWindow().getActivePage();
                if (page == null || !file.exists()) return;
                IDE.openEditor(page, file, true);
            } catch (Exception e) {
                System.err.println("AgentModeService: could not open editor for "
                        + file.getFullPath() + ": " + e.getMessage());
            }
        });
    }
}
