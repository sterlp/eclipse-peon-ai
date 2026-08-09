package org.sterl.llmpeon.parts.tools;

import org.sterl.llmpeon.tool.tools.AbstractTool;

import dev.langchain4j.agent.tool.Tool;

/**
 * Read-only view onto {@link PlanTool} for Jon (Peon-PO): he reviews a plan and hands its path to his
 * Dev slave, but never writes plans himself — planning is delegated to his Peon-Plan slave. Delegates
 * to the shared {@link PlanTool} instance so it always sees the current project.
 */
public class PlanReadTool extends AbstractTool {

    private final PlanTool planTool;

    public PlanReadTool(PlanTool planTool) {
        this.planTool = planTool;
    }

    @Tool("Reads the current saved plan (" + PlanTool.OVERVIEW_FILE + "), if one exists.")
    public String planRead() {
        return planTool.planRead();
    }

    @Tool("Checks whether a saved plan exists; returns its path (" + PlanTool.OVERVIEW_FILE + ") to hand to buildWithDev, or states that none exists yet.")
    public String hasPlan() {
        return planTool.hasPlan() ? PlanTool.OVERVIEW_FILE : "No plan exists yet.";
    }
}
