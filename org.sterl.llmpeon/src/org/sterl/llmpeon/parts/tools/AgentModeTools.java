package org.sterl.llmpeon.parts.tools;

import org.sterl.llmpeon.parts.agent.AgentModeService;
import org.sterl.llmpeon.parts.shared.IoUtils;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Tools available exclusively in AGENT mode.
 * Registered in ToolService only when the user switches to Peon-Agent mode.
 * Uses Eclipse IFile APIs so workspace history and refresh notifications are triggered.
 */
public class AgentModeTools extends AbstractEclipseTool {

    private final AgentModeService agentMode;

    public AgentModeTools(AgentModeService agentMode) {
        this.agentMode = agentMode;
    }

    @Override
    public boolean isEditTool() {
        return false;
    }

    @Tool("Save the implementation plan to .plan/overview.md in the current project. Call this when the plan is complete.")
    public String savePlan(@P("complete plan in markdown") String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be empty");
        }
        var project = agentMode.getProject();
        if (project == null || !project.isOpen()) {
            throw new IllegalStateException("No Eclipse project selected. Select a project resource first.");
        }
        IoUtils.writeProjectFile(project, ".plan/overview.md", content, getProgressMonitor());
        monitorMessage("Plan saved to /" + project.getName() + "/.plan/overview.md");
        agentMode.onPlanSaved();
        return "Plan saved to /" + project.getName() + "/.plan/overview.md";
    }

    @Tool("Escalate an unresolvable problem to the plan agent. Call this after 2 failed attempts.")
    public String reportProblem(@P("detailed problem description in markdown") String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be empty");
        }
        var project = agentMode.getProject();
        if (project == null || !project.isOpen()) {
            throw new IllegalStateException("No Eclipse project selected. Select a project resource first.");
        }
        IoUtils.writeProjectFile(project, ".plan/problem.md", content, getProgressMonitor());
        monitorMessage("Problem reported — returning to plan agent");
        agentMode.onProblemReported();
        return "Problem reported. Plan agent will review.";
    }
}
