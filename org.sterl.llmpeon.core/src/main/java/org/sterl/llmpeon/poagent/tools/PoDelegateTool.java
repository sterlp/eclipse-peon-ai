package org.sterl.llmpeon.poagent.tools;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.NamedAgent;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.context.SimpleContextItem;
import org.sterl.llmpeon.prompt.PeonPaths;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.tools.AbstractTool;

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
 * memory — its content rides in their system prompt (static context, the same list
 * Jon's static context is set to). They cannot write it; only Jon curates the shared
 * memory (and thereby steers the other agents).
 *
 * <p>
 * <b>Working orders:</b> {@code planWithPlanAgent} injects a plan-writing
 * discipline (plan-write-loop.txt) as a one-shot standing order;
 * {@code buildWithDev} keeps its {@code planPath} sticky and injects the build
 * discipline (dev-build-loop.txt) — both survive the slave's own compaction.
 * The base orders are resolved per slave via the {@code ordersFor} function
 * (e.g. the agent-specific AGENTS-<agent>.md), still lazily per dispatch().
 * Jon judges "done vs. still working" from the reply and always has the plan
 * reviewed after the build before the Dev slave calls planImplemented to
 * archive it (steered by po-delegation.txt).
 */
public class PoDelegateTool extends AbstractTool {

    public static final String TALK_PLAN = "talkPlan";
    public static final String PLAN_WITH_PLAN_AGENT = "planWithPlanAgent";
    public static final String ASK_DEV = "askDev";
    public static final String BUILD_WITH_DEV = "buildWithDev";

    private static final DateTimeFormatter COMPLETION_TIME = DateTimeFormatter.ofPattern("HH:mm");

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

    /** Base turn orders, resolved per slave (e.g. the agent-specific AGENTS-<agent>.md). */
    private final Function<NamedAgent, List<ContextItem>> ordersFor;

    private final NamedAgent plan; // "Da Thinka" — the Peon-Plan slave
    private final NamedAgent dev; // "Da Mek" — the Peon-Dev slave

    /**
     * Sticky plan path for the Dev slave — survives across dispatches and the
     * slave's compaction.
     */
    private String devPlanPath;

    public PoDelegateTool(NamedAgent plan, NamedAgent dev,
            Function<NamedAgent, List<ContextItem>> ordersFor) {
        this.plan = plan;
        this.dev = dev;
        this.ordersFor = ordersFor;
    }

    @Tool(name = PoDelegateTool.TALK_PLAN, value = "Ask your Peon-Plan team member (Da Thinka) a direct question or discuss an approach — no plan is written. Use planWithPlanAgent when you want the plan itself. Returns the team member's reply.")
    public String talkPlan(@P(name = "prompt") String prompt) {
        return dispatch(plan, prompt, ordersFor.apply(plan));
    }
    
    @Tool("Wipe Da Thinka back to blank — the next task is UNRELATED and the old state would only create drift. Use compactPlan instead when the same task continues.")
    public void resetPlan() {
        plan.agent().getMemory().clear();
        reportAction(plan, "reset");
    }
    
    @Tool("Compact Da Thinka — the SAME task continues but the history got long. Keeps the gist, frees context. Use resetPlan instead when the next task is unrelated.")
    public String compactPlan() {
        plan.agent().compressContext(monitor);
        reportAction(plan, "compacted");
        return "Da Thinka compacted. " + contextUsed(plan.agent());
    }

    @Tool(name = PoDelegateTool.PLAN_WITH_PLAN_AGENT, 
            value = "Have your Peon-Plan team member (Da Thinka) write/refine the plan into "
            + PeonPaths.PLAN_FILE
            + " with the plan tools (can only write a plan file), it plans continuously and asks you if something is unclear. Returns the team member's reply.")
    public String planWithPlanAgent(@P(name = "prompt") String prompt) {
        var orders = new LinkedList<>(ordersFor.apply(plan));
        orders.add(new SimpleContextItem("Plan instructions", PLAN_WRITE_LOOP));
        return dispatch(plan, prompt, orders);
    }

    @Tool(name = PoDelegateTool.ASK_DEV, value = "Ask your Peon-Dev team member (Da Mek) a direct question about the code or its progress — no build is triggered. Use buildWithDev to make it implement the plan. Returns the team member's reply.")
    public String askDev(@P(name = "prompt") String prompt) {
        return dispatch(dev, prompt, ordersFor.apply(dev));
    }

    @Tool(name = PoDelegateTool.BUILD_WITH_DEV, 
            value = "Have your Peon-Dev team member (Da Mek) implement the released plan, increment by increment. Pass planPath (" 
                    + PeonPaths.PLAN_FILE
                    + ") — it stays sticky as a standing order so it survives the team member's compaction. Returns the team member's reply.")
    public String buildWithDev(@P(name = "prompt") String prompt,
            @P(name = "planPath", required = false) String planPath) {
        if (StringUtil.hasValue(planPath)) devPlanPath = planPath.trim(); // sticky across calls

        var orders = new LinkedList<>(ordersFor.apply(dev));
        // Hand Da Mek the path AND the build discipline (task-by-task, green
        // gate, compactSession) as its way of working — never the whole plan; the file is the durable handover.
        if (StringUtil.hasValue(devPlanPath)) {
            // the plan itself is injected by the agentOrders
            orders.add(new SimpleContextItem("Reading dev loop instructions",
                    "The released plan to implement: " + devPlanPath
                    + System.lineSeparator()
                    + DEV_BUILD_LOOP));
        }
        return dispatch(dev, prompt, orders);
    }
    
    @Tool("Wipe Da Mek back to blank — the next task is UNRELATED and the old state would only create drift. Use compactDev instead when the same task continues.")
    public void resetDev() {
        dev.agent().getMemory().clear();
        reportAction(dev, "reset");
    }
    
    @Tool("Compact Da Mek — the SAME task continues but the history got long. Keeps the gist, frees context. Use resetDev instead when the next task is unrelated.")
    public String compactDev() {
        dev.agent().compressContext(monitor);
        reportAction(dev, "compacted");
        return "Da Mek compacted. " + contextUsed(dev.agent());
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

    private String dispatch(NamedAgent target, String prompt,
            List<ContextItem> orders) {
        ArgsUtil.requireNonBlank(prompt, "prompt");
        AiAgent slave = target.agent();
        slave.setTurnContextSupplier(() -> orders);

        // TODO custom style for the UI as agent message ...
        onTool(target.uiName() + " start: " + System.lineSeparator() + prompt);
        long startNanos = System.nanoTime();
        // The slave streams through Jon's monitor (this.monitor) — that is what
        // refreshes its 🟢 in the
        // header status widget while it works (ADR-0025). Keep the monitor
        // passed through.
        try {
            ChatResponse response = slave.call(prompt, this.monitor);
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
            String stats = dispatchStats(slave, elapsedMillis);
            onTool(target.uiName() + " done. " + stats);
            String answer = response != null
                    ? response.aiMessage().text()
                    : null;
            return StringUtil.hasValue(answer)
                    ? answer + System.lineSeparator() + stats
                    : slave.getName() + " team member returned no result";
        } catch (IllegalStateException e) {
            onProblem(target.uiName() + " " + e.getMessage());
            return "Failed: " + target.uiName() + e.getMessage();
        }
    }

    private String dispatchStats(AiAgent agent, long elapsedMillis) {
        return contextUsed(agent) + " (" + StringUtil.humanElapsed(elapsedMillis)
                + ", " + LocalTime.now().format(COMPLETION_TIME) + ")";
    }

    private void reportAction(NamedAgent target, String action) {
        onTool(target.uiName() + " " + action + ".");
    }



    private String contextUsed(AiAgent agent) {
        return "Context: " + agent.getMemory().getTotalTokenUsed() + " token - " + agent.tokenContextUsedInPercent() + "% used."; 
    }
}
