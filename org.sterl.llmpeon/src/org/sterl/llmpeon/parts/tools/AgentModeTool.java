package org.sterl.llmpeon.parts.tools;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.sterl.llmpeon.parts.PeonAiService;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.shared.AiMonitor.AiFileUpdate;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.FileUtils;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Tools available exclusively in AGENT mode.
 * Registered in ToolService only when the user switches to Peon-Agent mode.
 * Uses Eclipse IFile APIs so workspace history and refresh notifications are triggered.
 */
public class AgentModeTool extends AbstractEclipseTool {
    
    public static final String PLAN_DIR = "peon-plan";
    public static final String OVERVIEW_FILE = PLAN_DIR + "/overview.md";
    public static final String PROBLEM_FILE = PLAN_DIR + "/problem.md";

    private final PeonAiService peonAiService;

    public AgentModeTool(PeonAiService peonAiService) {
        this.peonAiService = peonAiService;
    }
    
    @Tool("Read the current plan.")
    public String planRead() {
        var project = getProject();
        var plan = project.getFile(OVERVIEW_FILE);
        if (plan == null || !plan.exists()) return "No plan exisits yet for project: " + JdtUtil.pathOf(plan);
        return IoUtils.readString(plan);
    }

    @Tool("Save the final implementation plan to " + OVERVIEW_FILE + ". Call only after all design decisions are resolved.")
    public void planSave(@P(description = "complete plan in markdown", name = "content") String content) {
        ArgsUtil.requireNonBlank(content, "content");
        var project = getProject();
        var planFile = IoUtils.writeProjectFile(project, OVERVIEW_FILE, content, getProgressMonitor());
        onTool("Plan saved to " + JdtUtil.pathOf(planFile));
        peonAiService.onPlanSaved(planFile);
    }

    @Tool("Update the current plan.")
    public void planUpdate(
            @P(description = "exact text to replace", name = "oldString") String oldString,
            @P(name = "newString", required = false) String newString) {
        
        ArgsUtil.requireNonBlank(oldString, "oldString");
        if (newString == null) newString = "";

        var content = planRead();
        var planFile = getProject().getFile(OVERVIEW_FILE);
        String newContent = FileUtils.applyEdit(JdtUtil.pathOf(planFile), content, oldString, newString);
        IoUtils.writeFile(planFile, newContent, getProgressMonitor());
        var editResult = new AiFileUpdate(JdtUtil.pathOf(planFile), oldString, newString);
        monitor.onFileUpdate(editResult);
        
        peonAiService.onPlanSaved(planFile);
    }
    
    public boolean hasPlan() {
        if (peonAiService.getProject() == null) return false;
        var planFile = getProject().getFile(OVERVIEW_FILE);
        return planFile != null && planFile.exists(); 
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
