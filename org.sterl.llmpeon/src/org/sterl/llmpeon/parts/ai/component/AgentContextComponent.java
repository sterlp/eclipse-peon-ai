package org.sterl.llmpeon.parts.ai.component;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.context.AgentsMdContextItem;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.context.EclipseFileContextItem;
import org.sterl.llmpeon.context.SimpleContextItem;
import org.sterl.llmpeon.context.StaticContextItem;
import org.sterl.llmpeon.context.UserContext;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.tools.PlanTool;
import org.sterl.llmpeon.parts.tools.memory.WorkspaceMemoryTool;
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.llmpeon.scaffold.AiScaffoldAgent;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;

/**
 * Owns the agent context assembly: the per-turn context (history injection), the Env-only
 * static-context bake and the plan/handoff state. Pure component — all collaborators are
 * injected as values or lazy suppliers, never the service itself.
 */
public class AgentContextComponent {

    private static final ILog LOG = Platform.getLog(AgentContextComponent.class);

    private final Supplier<IProject> projectRef;
    private final WorkspaceMemoryTool workspaceMemoryTool;
    private final UserContext userContext;
    private final AiScaffoldAgent scaffoldAgent;
    private final ToolService sharedToolService;
    private final Supplier<AiAgent> activeAgent;
    private final Supplier<LlmConfig> config;
    private final Supplier<List<AiAgent>> agents;

    /** Last saved plan file (workspace-relative reference, not disk — avoids stale project refs). */
    private IFile plan;

    /** Transient standing-order line set on handoff, consumed once by {@link #turnContext()}. */
    private volatile SimpleContextItem handoffLine;

    public AgentContextComponent(Supplier<IProject> projectRef,
            WorkspaceMemoryTool workspaceMemoryTool,
            UserContext userContext,
            AiScaffoldAgent scaffoldAgent,
            ToolService sharedToolService,
            Supplier<AiAgent> activeAgent,
            Supplier<LlmConfig> config,
            Supplier<List<AiAgent>> agents) {
        this.projectRef = projectRef;
        this.workspaceMemoryTool = workspaceMemoryTool;
        this.userContext = userContext;
        this.scaffoldAgent = scaffoldAgent;
        this.sharedToolService = sharedToolService;
        this.activeAgent = activeAgent;
        this.config = config;
        this.agents = agents;
    }

    /**
     * Bakes the Env info into the static context (system prompt) of every agent — the Workspace-Memory
     * is NOT part of it anymore (ADR-0032 Rev: dynamic turn-context only).
     * Jon's slaves share his list via {@link AiPoAgent#setStaticContext}.
     */
    public void initStaticContext() {
        var context = new ArrayList<ContextItem>();
        context.add(new StaticContextItem());

        for (var agent : agents.get()) {
            agent.setStaticContext(context);
        }
    }

    /** Arms the one-time handoff standing order shown instead of the plan reference on the next turn. */
    public void armHandoffLine(String agentName, String planPath) {
        handoffLine = new SimpleContextItem("Handover reference " + agentName,
                "Handover from " + agentName + " " + planPath);
    }

    public IFile planRef() {
        return plan;
    }

    public void setPlan(IFile planFile) {
        this.plan = planFile;
    }

    public void clearPlan() {
        this.plan = null;
    }

    public String readPlan() {
        if (this.plan == null || !this.plan.exists()) return "";
        return "Plan: " + JdtUtil.pathOf(plan) + System.lineSeparator() + "---" + System.lineSeparator() + System.lineSeparator()
            + IoUtils.readString(plan);
    }

    /**
     * Assembles the per-turn context items (injected after compact or on first call):
     * plan reference or handoff line, AGENTS.md, live workspace memory, Jon's docs, user context.
     * Scaffold agents get their own env/tool listing instead.
     */
    public List<ContextItem> turnContext() {
        var result = new LinkedList<ContextItem>();

        var agent = activeAgent.get();

        if ((agent instanceof AiScaffoldAgent)) {
            appendScaffoldContext(result);
        } else {
            if (handoffLine == null) {
                appendPlanReference(result);
            } else {
                // Consume handoff line once (set by onHandoff, survives compaction)
                result.add(handoffLine);
                handoffLine = null;
            }
            result.addAll(AgentsMdContextItem.itemsFor(agent.getName(), projectRef));
        }

        // Shared memory live per turn (ADR-0032): the dedupKey carries an entries-hash, so an
        // unchanged memory is skipped (already in history) while a change reinjects a fresh snapshot.
        result.add(workspaceMemoryTool);

        if ((agent instanceof AiPoAgent)) {
            result.add(new EclipseFileContextItem("docs/memory.md", projectRef));
            result.add(new EclipseFileContextItem("docs/index.md", projectRef));
        }
        result.addAll(userContext.get());
        return result;
    }

    private void appendScaffoldContext(LinkedList<ContextItem> result) {
        var configDir = config.get().getConfigDir();
        if (configDir == null) {
            result.add(new SimpleContextItem("No config dir set -- inform the user to check the config"));
            return;
        }

        result.add(new SimpleContextItem("Parent folder of disk tools set to the config dir you should work with relative paths directly in this folder only."));

        var orders = new StringBuilder();
        try {
            var readTool = scaffoldAgent.getToolService().getTool(DiskFileReadTool.class);
            if (readTool.isPresent()) {
                orders.append("Directory listing of the config dir ").append(configDir).append(":").append(System.lineSeparator());
                orders.append(readTool.get().diskListDirectory(LlmConfig.AGENT_DIRECTORY)).append(System.lineSeparator());
                orders.append(readTool.get().diskListDirectory(LlmConfig.COMMAND_DIRECTORY)).append(System.lineSeparator());
                orders.append(readTool.get().diskListDirectory(LlmConfig.SKILL_DIRECTORY)).append(System.lineSeparator());
            }
            result.add(new SimpleContextItem("Scaffold env. info", orders.toString()));
            orders.setLength(0);
        } catch (IllegalArgumentException e) {
            LOG.info("Directories missing " + e.getMessage());
        }

        // Available tools from sharedToolService
        orders.append("Available tools:").append(System.lineSeparator());
        for (var spec : sharedToolService.toolSpecifications()) {
            orders.append("- ").append(spec.name()).append(": ").append(spec.description()).append(System.lineSeparator());
        }

        result.add(new SimpleContextItem("Scaffold tool names", orders.toString()));

        // Open projects (R2d): project skills live in <disk path>/.agents/skills — the list
        // lets the scaffold pick a target project when creating project-local skills.
        var openProjects = EclipseUtil.openProjects();
        if (!openProjects.isEmpty()) {
            orders.setLength(0);
            orders.append("Open projects (project skills live in <disk path>/.agents/skills):").append(System.lineSeparator());
            for (var p : openProjects) {
                var disk = JdtUtil.diskPathOf(p);
                orders.append("- ").append(disk != null ? disk : JdtUtil.pathOf(p))
                        .append(" — ").append(p.getName());
                if (p == projectRef.get()) orders.append(" (current)");
                orders.append(System.lineSeparator());
            }
            result.add(new SimpleContextItem("Open projects", orders.toString()));
        }
    }

    private void appendPlanReference(LinkedList<ContextItem> result) {
        var project = projectRef.get();
        if (project == null) return;

        final var planFile = project.getFile(PlanTool.OVERVIEW_FILE);
        if (planFile == null || !planFile.exists()) return;

        result.add(new ContextItem() {
            @Override
            public String label() {
                return "Plan reference " + dedupKey();
            }
            @Override
            public String dedupKey() {
                return JdtUtil.pathOf(planFile);
            }
            @Override
            public String render() {
                if (!planFile.exists()) return null;
                return "Existing plan found: " + JdtUtil.pathOf(planFile);
            };
        });
    }
}
