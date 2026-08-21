package org.sterl.llmpeon.parts.ai.component;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.eclipse.core.resources.IProject;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.AiPoAgent;
import org.sterl.llmpeon.agent.NamedAgent;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.context.AgentsMdContextItem;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.context.EclipseFileContextItem;
import org.sterl.llmpeon.context.StaticContextItem;
import org.sterl.llmpeon.parts.tools.AskUserTool;
import org.sterl.llmpeon.parts.tools.EclipseGrepTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceWriteFileTool;
import org.sterl.llmpeon.parts.tools.PlanReadTool;
import org.sterl.llmpeon.parts.tools.PlanTool;
import org.sterl.llmpeon.parts.tools.memory.WorkspaceMemoryTool;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;
import org.sterl.llmpeon.tool.tools.CompactSessionTool;
import org.sterl.llmpeon.tool.tools.JonDelegateTool;
import org.sterl.llmpeon.tool.tools.SearchAgentTool;

public class BuildPoAgentComponent {
    
    /** Jon's RAM-only slaves compact at 70% of the shared budget so their throw-away context stays lean. */
    private static final double SLAVE_COMPACT_FACTOR = 0.7;
    private final ConfiguredChatModel configuredModel;
    private final LlmConfig config;
    private final Supplier<IProject> projectRef;
    private final ToolService sharedToolService;
    private final List<ContextItem> staticContent = List.of(new StaticContextItem());

    public BuildPoAgentComponent(ConfiguredChatModel configuredModel,
            LlmConfig config, Supplier<IProject> projectRef,
            ToolService sharedToolService) {
        super();
        this.configuredModel = configuredModel;
        this.config = config;
        this.projectRef = projectRef;
        this.sharedToolService = sharedToolService;
    }

    public AiPoAgent build() {
        // Peon-PO (Jon): own curated tool set — docs read/write/grep only (no shell/build/test/plan).
        // Reuses the shared Eclipse tool instances so project updates + the per-request write validator
        // (WriteValidator.DOCS from AiPoAgent) apply automatically.
        var poToolService = new ToolService(false);
        poToolService.addTool(sharedToolService.getTool(EclipseWorkspaceReadFileTool.class).get());
        poToolService.addTool(sharedToolService.getTool(EclipseWorkspaceWriteFileTool.class).get());
        poToolService.addTool(sharedToolService.getTool(EclipseGrepTool.class).get());
        // Jon curates the shared memory (write) — he knows it is shared by all agents, so writing it
        // steers his slaves and the other agents (they only ever READ it, via standing-order injection).
        var wmt = sharedToolService.getTool(WorkspaceMemoryTool.class).get();
        poToolService.addTool(wmt);
        // Read-only plan access: hasPlan (returns the path) + planRead. Jon reviews & hands the path
        // to buildWithDev but never writes the plan himself — that is his Peon-Plan agent's job.
        poToolService.addTool(new PlanReadTool(sharedToolService.getTool(PlanTool.class).get()));
        // Jon's Plan/Dev slaves: RAM-only (2-arg ctor — no history file, ADR-0024), reusing the shared
        // Eclipse tool set. Lazy singletons via the factory so Jon-in-core stays testable headless.
        // Slaves may READ the shared memory (injected as a standing order) but never WRITE it — strip the
        // memory-write tool from their effective set; only Jon curates the shared memory.
        Predicate<SmartToolExecutor> noPrivilegedTools = t -> !(t.getTool() instanceof WorkspaceMemoryTool)
                && !(t.getTool() instanceof AskUserTool);
        // Eager shared slaves (ADR-0025): created once here and handed — as the same NamedAgent objects —
        // to both the delegate tool (which drives them) and AiPoAgent (which exposes them via getTeam()).
        AiAgent planSlave = new AiPlanAgent(configuredModel, sharedToolService, SLAVE_COMPACT_FACTOR) {
            @Override protected Predicate<SmartToolExecutor> getToolFilter() {
                return super.getToolFilter().and(noPrivilegedTools);
            }
        };
        planSlave.setStaticContext(staticContent);

        AiAgent devSlave = new AiDevAgent(configuredModel, sharedToolService, SLAVE_COMPACT_FACTOR) {
            @Override protected Predicate<SmartToolExecutor> getToolFilter() {
                return super.getToolFilter().and(noPrivilegedTools);
            }
        };
        devSlave.setStaticContext(staticContent);

        var thinka = new NamedAgent("Da Thinka", planSlave);
        var mek = new NamedAgent("Da Mek", devSlave);
        // Slaves also need the same relevant context as the active agent (Jon gets it via userContext;
        // they get it folded into their injected standing orders — read-only, like the shared memory):
        // the shared memory and the selected project. AGENTS-<agent>.md + the plan file ride in via
        // additionalContext as turn context (ADR-0029).
        var jonDelegateTool = new JonDelegateTool(thinka, mek, () -> {
            var orders = new LinkedList<ContextItem>();
            orders.add(new EclipseFileContextItem(PlanTool.OVERVIEW_FILE, projectRef));
            orders.add(new AgentsMdContextItem(projectRef));
            return orders;
        });
        poToolService.addTool(jonDelegateTool);
        // Jon's own throw-away research sub-agent (Da Sniffa) — searches with his read/grep tools to
        // save his context; stateless one-shot, not one of his persistent slaves.
        poToolService.addTool(new SearchAgentTool(poToolService));
        poToolService.addTool(new CompactSessionTool());
        var poAgent = new AiPoAgent(configuredModel, poToolService, config.getConfigDir(), List.of(thinka, mek));

        return poAgent;
    }
}
