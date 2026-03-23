package org.sterl.llmpeon.parts.tools;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.sterl.llmpeon.parts.agent.AgentModeService;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.JdtUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Tools available exclusively in AGENT mode.
 * Registered in ToolService only when the user switches to Peon-Agent mode.
 * Uses Eclipse IFile APIs so workspace history and refresh notifications are triggered.
 */
public class AgentModeTools extends AbstractEclipseTool {
    
    public static final String PLAN_DIR = "peon-plan";
    public static final String OVERVIEW_FILE = PLAN_DIR + "/overview.md";
    public static final String PROBLEM_FILE = PLAN_DIR + "/problem.md";

    private final AgentModeService agentMode;

    public AgentModeTools(AgentModeService agentMode) {
        this.agentMode = agentMode;
    }
    
    @Tool("Save the implementation plan to peon-plan/overview.md in the current project. Call this when the plan is complete.")
    public String savePlan(@P("complete plan in markdown") String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be empty");
        }
        var project = getProject();
        var f = IoUtils.writeProjectFile(project, OVERVIEW_FILE, content, getProgressMonitor());
        monitorMessage("Plan saved to " + JdtUtil.pathOf(f));
        agentMode.onPlanSaved();
        return "Plan saved to " + JdtUtil.pathOf(f);
    }

    @Tool("Escalate an unresolvable problems to the plan agent. Call this after 2 failed attempts.")
    public String reportPlanProblems(@P("detailed problem description in markdown") String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be empty");
        }
        var project = getProject();
        var f = IoUtils.writeProjectFile(project, PROBLEM_FILE, content, getProgressMonitor());
        monitorMessage("Problem reported — returning to plan agent");
        agentMode.onProblemReported();
        return "Problem reported. Plan agent will review. " + JdtUtil.pathOf(f);
    }
    
    private IProject getProject() {
        var project = agentMode.getProject();
        if (project == null) {
            throw new IllegalStateException("No Eclipse project selected. Developer has to select a project.");
        }
        try {
            project.open(getProgressMonitor());
        } catch (CoreException e) {
            throw new RuntimeException("Failed to open project with plan file " + project.getName(), e);
        }
        return project;
    }
}
