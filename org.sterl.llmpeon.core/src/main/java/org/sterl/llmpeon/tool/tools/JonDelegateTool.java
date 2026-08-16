package org.sterl.llmpeon.tool.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.context.SimpleContextItem;
import org.sterl.llmpeon.agent.NamedAgent;
import org.sterl.llmpeon.prompt.PeonPaths;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Jon's delegate tools. Each drives one of his own, RAM-only slaves — a
 * Peon-Plan ("Da Thinka") and a Peon-Dev ("Da Mek") instance — for a single
 * turn and returns the slave's reply verbatim as the tool result. Two verbs per
 * slave: a plain question ({@code talkPlan} / {@code askDev}) and the real work
 * ({@code planWithPlanAgent} writes the plan, {@code buildWithDev} builds it).
 *
 * <p>
 * The two slaves are shared, eager singletons: the layer that wires the tool
 * creates them (core supplies disk-tool slaves, the Eclipse plugin supplies
 * workspace-tool slaves with the memory-write tool filtered out) and hands them
 * in as {@link NamedAgent}s — the very same instances Jon exposes via
 * {@code AiPoAgent.getTeam()} (ADR-0025). Their in-RAM context carries across
 * calls. Slaves use a RAM-only memory (no history file); the durable handover
 * is the plan file (see ADR-0024).
 *
 * <p>
 * <b>Shared memory:</b> the slaves only ever <em>read</em> the shared workspace
 * memory — its content is injected into their standing orders before each
 * dispatch. They cannot write it; only Jon curates the shared memory (and
 * thereby steers the other agents).
 *
 * <p>
 * <b>Working orders:</b> {@code planWithPlanAgent} injects a plan-writing
 * discipline (plan-write-loop.txt) as a one-shot standing order;
 * {@code buildWithDev} keeps its {@code planPath} sticky and injects the build
 * discipline (dev-build-loop.txt) — both survive the slave's own compaction.
 * Jon judges "done vs. still working" from the reply and always has the plan
 * reviewed after the build before the Dev slave calls planImplemented to
 * archive it (steered by po-delegation.txt).
 */
public class JonDelegateTool extends AbstractTool {

    public static final String TALK_PLAN = "talkPlan";
    public static final String PLAN_WITH_PLAN_AGENT = "planWithPlanAgent";
    public static final String ASK_DEV = "askDev";
    public static final String BUILD_WITH_DEV = "buildWithDev";

    /**
     * Build discipline injected into the Dev slave only when it builds from a
     * released plan.
     */
    private static final String DEV_BUILD_LOOP = PromptLoader
            .load("dev-build-loop.txt");
    /**
     * Plan-writing discipline injected into the Plan slave when Jon asks it to
     * write the plan.
     */
    private static final String PLAN_WRITE_LOOP = PeonPaths
            .resolve(PromptLoader.load("plan-write-loop.txt"));

    private final NamedAgent plan; // "Da Thinka" — the Peon-Plan slave
    private final NamedAgent dev; // "Da Mek" — the Peon-Dev slave
    private final Supplier<List<String>> memoryProvider;
    /**
     * Sticky plan path for the Dev slave — survives across dispatches and the
     * slave's compaction.
     */
    private String devPlanPath;

    /**
     * Additional context items (e.g. AGENTS-&lt;agent&gt;.md, the plan file) merged into
     * every dispatch's turn context, applied per slave agent name. Set by the plugin layer.
     */
    private Function<String, List<ContextItem>> additionalContext = name -> List.of();

    public JonDelegateTool(NamedAgent plan, NamedAgent dev,
            Supplier<List<String>> memoryProvider) {
        this.plan = plan;
        this.dev = dev;
        this.memoryProvider = memoryProvider;
    }

    /** Sets additional context items merged into every dispatch's turn context, applied per slave agent name (e.g. AGENTS-&lt;agent&gt;.md, plan file). */
    public void setAdditionalContext(Function<String, List<ContextItem>> function) {
        this.additionalContext = function;
    }

    @Tool(name = JonDelegateTool.TALK_PLAN, value = "Ask your Peon-Plan team member (Da Thinka) a direct question or discuss an approach — no plan is written. Use planWithPlanAgent when you want the plan itself. Returns the team member's reply.")
    public String talkPlan(@P(name = "prompt") String prompt) {
        return dispatch(plan, prompt, baseOrders());
    }
    
    @Tool("Wipes all stored memory/chat-history of the Peon-Plan (Da Thinka) agent back to a blank state.")
    public void resetPlan() {
        plan.agent().getMemory().clear();
    }
    
    @Tool("Compact the session of Peon-Plan (Da Thinka), e.g. before creating a new plan - if the old context still matters.")
    public String compactPlan() {
        return plan.agent().compressContext(monitor).aiMessage().text()
                + System.lineSeparator() + contextUsed(plan.agent());
    }

    @Tool(name = JonDelegateTool.PLAN_WITH_PLAN_AGENT, value = "Have your Peon-Plan team member (Da Thinka) write/refine the plan into "
            + PeonPaths.PLAN_FILE
            + " with the plan tools, sliced into small green increments; it plans continuously and asks you if something is unclear. Returns the team member's reply.")
    public String planWithPlanAgent(@P(name = "prompt") String prompt) {
        var orders = baseOrders();
        orders.add(PLAN_WRITE_LOOP); // one-shot standing order (deduped by the
                                     // slave)
        return dispatch(plan, "Plan " + prompt, orders);
    }

    @Tool(name = JonDelegateTool.ASK_DEV, value = "Ask your Peon-Dev team member (Da Mek) a direct question about the code or its progress — no build is triggered. Use buildWithDev to make it implement the plan. Returns the team member's reply.")
    public String askDev(@P(name = "prompt") String prompt) {
        return dispatch(dev, prompt, baseOrders());
    }

    @Tool(name = JonDelegateTool.BUILD_WITH_DEV, value = "Have your Peon-Dev team member (Da Mek) implement the released plan, increment by increment. Pass planPath ("
            + PeonPaths.PLAN_FILE
            + ") — it stays sticky as a standing order so it survives the team member's compaction. Returns the team member's reply.")
    public String buildWithDev(@P(name = "prompt") String prompt,
            @P(name = "planPath", required = false) String planPath) {
        if (StringUtil.hasValue(planPath))
            devPlanPath = planPath.trim(); // sticky across calls
        var orders = baseOrders();
        // Hand Da Mek the path AND the build discipline (task-by-task, green
        // gate, compactSession) as its
        // way of working — never the whole plan; the file is the durable
        // handover.
        if (StringUtil.hasValue(devPlanPath)) {
            orders.add("The released plan to implement: " + devPlanPath);
            orders.add(DEV_BUILD_LOOP);
        }
        return dispatch(dev, prompt, orders);
    }
    
    @Tool("Wipes all stored memory/chat-history of the Peon-Dev (Da Mek) agent back to a blank state.")
    public void resetDev() {
        dev.agent().getMemory().clear();
    }
    
    @Tool("Compact the session of Peon-Dev (Da Mek), e.g. before implementing a new plan - if the old context still matters.")
    public String compactDev() {
        return dev.agent().compressContext(monitor).aiMessage().text()
                + System.lineSeparator() + contextUsed(dev.agent());
    }

    /**
     * The Plan slave (Da Thinka), an eager shared instance also exposed via the
     * PO team.
     */
    public AiAgent getPlanSlave() {
        return plan.agent();
    }

    /**
     * The Dev slave (Da Mek), an eager shared instance also exposed via the PO
     * team.
     */
    public AiAgent getDevSlave() {
        return dev.agent();
    }

    /**
     * Shared memory + injected context (AGENTS.md, selected project) —
     * read-only for the slaves.
     */
    private ArrayList<String> baseOrders() {
        return new ArrayList<>(memoryProvider.get());
    }

    private String dispatch(NamedAgent target, String prompt,
            List<String> orders) {
        ArgsUtil.requireNonBlank(prompt, "prompt");
        AiAgent slave = target.agent();
        List<ContextItem> items = new ArrayList<>(orders.size());
        for (String text : orders) items.add(new SimpleContextItem(text));
        items.addAll(additionalContext.apply(slave.getName()));
        List<ContextItem> captured = items;
        slave.setTurnContextSupplier(() -> captured);

        onTool(target.uiName() + " start:\n" + prompt);
        long startNanos = System.nanoTime();
        // The slave streams through Jon's monitor (this.monitor) — that is what
        // refreshes its 🟢 in the
        // header status widget while it works (ADR-0025). Keep the monitor
        // passed through.
        try {
            ChatResponse response = slave.call(prompt, this.monitor);
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
            onTool(target.uiName() + " done. ("
                    + StringUtil.humanElapsed(elapsedMillis) + ")");
            String answer = response != null
                    ? response.aiMessage().text()
                    : null;
            return StringUtil.hasValue(answer)
                    ? answer + System.lineSeparator() + contextUsed(slave)
                    : slave.getName() + " team member returned no result";
        } catch (IllegalStateException e) {
            onProblem(target.uiName() + " " + e.getMessage());
            return "Failed: " + target.uiName() + e.getMessage();
        }
    }
    
    private String contextUsed(AiAgent agent) {
        return "Context: " + agent.getMemory().getTotalTokenUsed() + " token - " + agent.tokenContextUsedInPercent() + "% used."; 
    }
}
