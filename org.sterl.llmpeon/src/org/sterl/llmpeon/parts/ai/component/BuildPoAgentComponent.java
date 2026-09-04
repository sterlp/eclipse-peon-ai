package org.sterl.llmpeon.parts.ai.component;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.eclipse.core.resources.IProject;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
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
import org.sterl.llmpeon.poagent.AiPoAgent;
import org.sterl.llmpeon.poagent.tools.PoDelegateTool;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.component.SmartToolExecutor;
import org.sterl.llmpeon.tool.tools.CompactSessionTool;
import org.sterl.llmpeon.tool.tools.SearchAgentTool;

public class BuildPoAgentComponent {
    
    /** Jon's RAM-only slaves compact at 70% of the shared budget so their throw-away context stays lean. */
    private static final double SLAVE_COMPACT_FACTOR = 0.7;
    private final ConfiguredChatModel configuredModel;
    private final LlmConfig config;
    private final Supplier<IProject> projectRef;
    private final ToolService sharedToolService;
    /**
     * Fallback static context (env info only) handed to the slaves in {@link #build()}. In fully wired
     * operation {@code PeonAiService.initStaticContext()} overrides it with the same Env-only list
     * Jon's static context is set to (memory rides dynamically per turn, ADR-0032 Rev); this list only
     * matters for headless builds without that wiring.
     */
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
        // steers his slaves and the other agents (they only ever READ it, injected per turn / delegation,
        // ADR-0032 Rev).
        var wmt = sharedToolService.getTool(WorkspaceMemoryTool.class).get();
        poToolService.addTool(wmt);
        // Read-only plan access: hasPlan (returns the path) + planRead. Jon reviews & hands the path
        // to buildWithDev but never writes the plan himself — that is his Peon-Plan agent's job.
        poToolService.addTool(new PlanReadTool(sharedToolService.getTool(PlanTool.class).get()));
        // Jon's Plan/Dev slaves: RAM-only (2-arg ctor — no history file, ADR-0024), reusing the shared
        // Eclipse tool set. Lazy singletons via the factory so Jon-in-core stays testable headless.
        // Slaves may READ the shared memory (injected into their turn orders per delegation) but
        // never WRITE it — strip the memory-write tool from their effective set; only Jon curates it.
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
        // Slaves also need the same relevant context as the active agent (Jon gets it via userContext).
        // The shared memory rides in their system prompt (static context — re-baked by
        // PeonAiService.initStaticContext); the turn orders below carry the plan file
        // + AGENTS.md (base + agent-specific AGENTS-<agent>.md, ADR-0029)
        // + the live Workspace-Memory snapshot (ADR-0032).
        var jonDelegateTool = new PoDelegateTool(thinka, mek, target -> {
            var orders = new LinkedList<ContextItem>();
            orders.add(new EclipseFileContextItem(PlanTool.OVERVIEW_FILE, projectRef));
            orders.addAll(AgentsMdContextItem.itemsFor(target.agent().getName(), projectRef));
            // Shared memory live per delegation (ADR-0032): the function runs lazy per dispatch(),
            // so the slaves always read the current snapshot (dedupKey carries the entries-hash).
            orders.add(wmt);
            return orders;
        });
        poToolService.addTool(jonDelegateTool);
        // Jon's own throw-away research sub-agent (Da Sniffa) — searches with his read/grep tool
        poToolService.addTool(sharedToolService.getTool(SearchAgentTool.class).get());
        poToolService.addTool(new CompactSessionTool());
        var poAgent = new AiPoAgent(configuredModel, poToolService, config.getConfigDir(), List.of(thinka, mek));

        return poAgent;
    }
}
