package org.sterl.llmpeon.parts.tools;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.sterl.llmpeon.parts.ai.PeonAiService;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.shared.AiMonitor.AiFileUpdate;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.ArchiveName;
import org.sterl.llmpeon.shared.FileUtils;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Plan tools for the workspace `peon-plan/overview.md`. Registered in the shared
 * ToolService; which agent may call them is decided by the per-agent tool filter.
 * Uses Eclipse IFile APIs so workspace history and refresh notifications are triggered.
 */
public class PlanTool extends AbstractEclipseTool {
    
    public static final String PLAN_DIR = "peon-plan";
    public static final String OVERVIEW_FILE = PLAN_DIR + "/overview.md";
    public static final String PROBLEM_FILE = PLAN_DIR + "/problem.md";

    private final PeonAiService peonAiService;
    private final PlanFileView view;

    public PlanTool(PeonAiService peonAiService) {
        this(peonAiService, IResource::refreshLocal);
    }

    public PlanTool(PeonAiService peonAiService, PlanFileView.Refresh refresh) {
        this.peonAiService = peonAiService;
        this.view = new PlanFileView(refresh);
    }
    
    @Tool("Reads the current saved plan, if one exists.")
    public String planRead() {
        var plan = visiblePlan();
        if (plan == null) {
            // getProject() keeps the existing contract: no project selected stays an error
            return "No plan exists yet for project: " + JdtUtil.pathOf(getProject().getFile(OVERVIEW_FILE));
        }
        return IoUtils.readString(plan);
    }

    @Tool("Save or overwrite the implementation plan to " + OVERVIEW_FILE + ". Call only after all design decisions are resolved.")
    public String planSave(@P(description = "complete plan in markdown", name = "content") String content) {
        ArgsUtil.requireNonBlank(content, "content");
        var project = getProject();
        var planFile = IoUtils.writeProjectFile(project, OVERVIEW_FILE, content, getProgressMonitor());
        String result = "Saved " + JdtUtil.pathOf(planFile);
        onTool("Plan saved to " + JdtUtil.pathOf(planFile) + " " + StringUtil.charsSuffix(result));
        peonAiService.onPlanSaved(planFile);
        
        return result;
    }

    @Tool("Update the plan by replacing all exact text substring. For incremental refinements.")
    public String planUpdate(
            @P(description = "exact text to replace", name = "oldString") String oldString,
            @P(name = "newString", required = false) String newString) {
        
        ArgsUtil.requireNonBlank(oldString, "oldString");
        if (newString == null) newString = "";

        var content = planRead();
        var planFile = getProject().getFile(OVERVIEW_FILE);
        var edit = FileUtils.applyEdit(JdtUtil.pathOf(planFile), content, oldString, newString);
        IoUtils.writeFile(planFile, edit.content(), getProgressMonitor());
        var editResult = new AiFileUpdate(JdtUtil.pathOf(planFile), oldString, newString);
        monitor.onFileUpdate(editResult);
        
        peonAiService.onPlanSaved(planFile);
        
        return "Updated " + JdtUtil.pathOf(planFile);
    }
    
    @Tool("Archives the current plan with a timestamp once fully implemented. Call as the final tool call, or before starting a new plan to preserve the old one.")
    public void planImplemented() {
        var project = getProject();
        var plan = project.getFile(OVERVIEW_FILE);
        refreshPlanDir(project);
        if (!plan.exists()) return;

        try {
            var stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm"));
            String stem = "overview-done-" + stamp;
            String freeName = ArchiveName.firstFreeName(stem, n -> project.getFile(PLAN_DIR + "/" + n).exists());
            var target = project.getFile(PLAN_DIR + "/" + freeName);
            onTool("Plan finished: " + target.getFullPath());
            plan.move(target.getFullPath(), IResource.KEEP_HISTORY, getProgressMonitor());
            peonAiService.onPlanArchived();
        } catch (CoreException e) {
            throw new RuntimeException("Failed to mark file as done. " + JdtUtil.pathOf(plan) + " " + e.getMessage(), e);
        }
    }

    public boolean hasPlan() {
        return visiblePlan() != null;
    }

    /**
     * R-PT-3: the plan as the workspace currently sees it — refreshed, never throwing for a failed
     * refresh, and {@code null} when no project is selected. That makes it usable as the per-turn
     * plan supplier of {@link org.sterl.llmpeon.parts.ai.component.AgentContextComponent}, so the
     * turn context and {@link #hasPlan()} cannot drift apart (same view, same depth).
     */
    public IFile visiblePlan() {
        if (peonAiService.getProject() == null) return null;
        return view.visible(getProject(), getProgressMonitor(), this::onProblem);
    }
/*
    @Tool("Call this method to indicate that the plan is ready. Call only after all design decisions are resolved.")
    public void planComplete() {
        // TODO
    }


    @Tool("Escalate unresolvable blockers (build failures, missing context) to the planner agent. Use after 2 failed attempts.")
    public String planProblem(@P(description = "detailed problem description in markdown", name = "content") String content) {
        ArgsUtil.requireNonBlank(content, "content");
        
        var project = getProject();
        var f = IoUtils.writeProjectFile(project, PROBLEM_FILE, content, getProgressMonitor());
        onTool("Problem reported — returning to plan agent");
        agentMode.onProblemReported();
        return "Problem reported. Plan agent will review. " + JdtUtil.pathOf(f);
    }
*/
    /**
     * R-PT-1/R-PT-2 (org.sterl.llmpeon/docs/plan-tool.md): a plan last written by the disk tools is
     * unknown to the workspace tree — without this refresh exists() is a false negative ("No plan
     * exists yet") and move() fails on a file out of sync with the local file system.
     */
    private void refreshPlanDir(IProject project) {
        var dir = project.getFolder(PLAN_DIR);
        try {
            view.refreshDir(project, getProgressMonitor());
        } catch (CoreException e) {
            throw new RuntimeException("Failed to refresh " + JdtUtil.pathOf(dir) + " " + e.getMessage(), e);
        }
    }

    private IProject getProject() {
        var project = peonAiService.getProject();
        if (project == null) {
            throw new IllegalStateException("No Eclipse project selected. Ask user to select a project!");
        }
        try {
            project.open(getProgressMonitor());
        } catch (CoreException e) {
            throw new RuntimeException("Failed to open project with plan file " + project.getName(), e);
        }
        return project;
    }
}
