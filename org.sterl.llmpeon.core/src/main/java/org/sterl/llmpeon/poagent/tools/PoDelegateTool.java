package org.sterl.llmpeon.poagent.tools;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;

import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.context.SimpleContextItem;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.prompt.PeonPaths;
import org.sterl.llmpeon.prompt.PromptLoader;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.CallStats;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.tools.AbstractTool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Jon's delegate tools. Each drives one of his own, RAM-only slaves — a
 * Plan, a Review and a Dev member
 * instance — for a single turn and returns the slave's reply verbatim as the
 * tool result. Verbs per slave: a direct question ({@code talkPlan} /
 * {@code askDev}), the real work ({@code planWithPlanAgent} writes the plan,
 * {@code reviewPlanAgent} reviews it, {@code buildWithDev} builds it) and
 * context management (clear/compact per slave).
 *
 * <p>
 * The three slaves are shared, eager singletons: the layer that wires the tool
 * creates them (core supplies disk-tool slaves, the Eclipse plugin supplies
 * workspace-tool slaves with the memory-write tool filtered out) and hands them
 * in as {@link AiAgent}s — the very same instances Jon exposes via
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
 * discipline (dev-build-loop.txt); {@code reviewPlanAgent} reuses the same
 * sticky plan path — all survive the slaves' own compaction.
 * The base orders are resolved per slave via the {@code ordersFor} function
 * (e.g. the agent-specific AGENTS-<agent>.md), still lazily per dispatch().
 * Jon judges "done vs. still working" from the reply and always has the plan
 * reviewed after the build before the Dev slave calls planImplemented to
 * archive it (steered by po-delegation.txt).
 */
public class PoDelegateTool extends AbstractTool {

    public static final String TALK_PLAN = "talkPlan";
    public static final String REVIEW_PLAN_AGENT = "reviewPlanAgent";
    public static final String PLAN_WITH_PLAN_AGENT = "planWithPlanAgent";
    public static final String ASK_DEV = "askDev";
    public static final String BUILD_WITH_DEV = "buildWithDev";

    /**
     * Build discipline injected into the Dev slave only when it builds from a
     * released plan.
     */
    private static final String DEV_BUILD_LOOP = PromptLoader
            .load("dev-build-loop.md");
    /**
     * Plan-writing discipline injected into the Plan slave when Jon asks it to
     * write the plan.
     */
    private static final String PLAN_WRITE_LOOP = PeonPaths
            .resolve(PromptLoader.load("plan-write-loop.md"));

    /**
     * Paul: the slaves compact at 70 % of the shared budget — before a new task lands, so the
     * throw-away context never chokes the delegation (A-0021 factor, now owned by this tool).
     */
    private static final double SLAVE_COMPACT_FACTOR = 0.7;

    /** Base turn orders, resolved per slave (e.g. the agent-specific AGENTS-<agent>.md). */
    private final Function<AiAgent, List<ContextItem>> ordersFor;

    /**
     * Live supplier for the shared staging budget ({@code autoCompactAfter}) — read per
     * delegation so a config reload changes the threshold without a rebuild (0 = off, R-CIB-1).
     */
    private final IntSupplier autoCompactAfter;

    private final AiAgent plan; // the Plan slave
    private final AiAgent review; // the Review slave
    private final AiAgent dev; // the Dev slave

    /**
     * Sticky plan path shared by the Dev and Review slaves — survives across
     * dispatches and the slaves' compaction.
     */
    private String planPath;

    public PoDelegateTool(AiAgent plan, AiAgent review, AiAgent dev,
            Function<AiAgent, List<ContextItem>> ordersFor, IntSupplier autoCompactAfter) {
        this.plan = plan;
        this.dev = dev;
        this.review = review;
        this.ordersFor = ordersFor;
        this.autoCompactAfter = autoCompactAfter;
    }

    @Tool(name = PoDelegateTool.TALK_PLAN, value = "Ask your Plan team member a direct question or discuss an approach — no plan is written. Use planWithPlanAgent when you want the plan itself. Returns the team member's reply.")
    public String talkPlan(@P(name = "prompt") String prompt) {
        return dispatch(plan, prompt, ordersFor.apply(plan));
    }
    
    @Tool("Wipe Plan back to blank — the next task is UNRELATED and the old state would only create drift. Use compactPlan instead when the same task continues.")
    public void clearPlan() {
        plan.getMemory().clear();
        reportAction(plan, "reset");
    }
    
    @Tool("Compact Plan — the SAME task continues but the history got long. Keeps the gist, frees context. Use resetPlan instead when the next task is unrelated.")
    public String compactPlan() {
        return compact(plan);
    }

    @Tool(name = PoDelegateTool.PLAN_WITH_PLAN_AGENT, 
            value = "Have your Plan team member write/refine the plan into "
            + PeonPaths.PLAN_FILE
            + " with the plan tools (can only write a plan file), it plans continuously and asks you if something is unclear. Returns the team member's reply.")
    public String planWithPlanAgent(@P(name = "prompt") String prompt) {
        var orders = new LinkedList<>(ordersFor.apply(plan));
        orders.add(new SimpleContextItem("Plan instructions", PLAN_WRITE_LOOP));
        return dispatch(plan, prompt, orders);
    }
    
    @Tool(name = PoDelegateTool.REVIEW_PLAN_AGENT,
            value = "Review team member reviews a plan's implementation. "
                  + "Pass planPath (" + PeonPaths.PLAN_FILE + ") to set the plan — stays sticky across calls. "
                  + "Omit to reuse the last plan. Use for review questions, feedback, or approval.")
    public String reviewPlanAgent(@P(name = "prompt") String prompt,
            @P(name = "planPath", required = false) String planPath) {
        if (StringUtil.hasValue(planPath)) this.planPath = planPath.trim(); // sticky across calls

        var orders = new LinkedList<>(ordersFor.apply(review));
        if (StringUtil.hasValue(this.planPath)) {
            orders.add(new SimpleContextItem("Plan path to review", this.planPath));
        }
        return dispatch(review, prompt, orders);
    }

    @Tool("Wipe Review back to blank — the next task is UNRELATED and the old state would only create drift. Use compactReview instead when the same task continues.")
    public void clearReview() {
        review.getMemory().clear();
        reportAction(review, "reset");
    }

    @Tool("Compact Review — the SAME task continues but the history got long. Keeps the gist, frees context. Use clearReview instead when the next task is unrelated.")
    public String compactReview() {
        return compact(review);
    }

    @Tool(name = PoDelegateTool.ASK_DEV, value = "Ask your Dev team member a direct question about the code or its progress — no build is triggered. Use buildWithDev to make it implement the plan. Returns the team member's reply.")
    public String askDev(@P(name = "prompt") String prompt) {
        return dispatch(dev, prompt, ordersFor.apply(dev));
    }

    @Tool(name = PoDelegateTool.BUILD_WITH_DEV, 
            value = "Have your Dev team member implement the released plan, increment by increment. Pass planPath (" 
                    + PeonPaths.PLAN_FILE
                    + ") — it stays sticky as a standing order so it survives the team member's compaction. Returns the team member's reply.")
    public String buildWithDev(@P(name = "prompt") String prompt,
            @P(name = "planPath", required = false) String planPath) {
        if (StringUtil.hasValue(planPath)) this.planPath = planPath.trim(); // sticky across calls

        var orders = new LinkedList<>(ordersFor.apply(dev));
        // Hand Dev the path AND the build discipline (task-by-task, green
        // gate, compactSession) as its way of working — never the whole plan; the file is the durable handover.
        if (StringUtil.hasValue(this.planPath)) {
            // the plan itself is injected by the agentOrders
            orders.add(new SimpleContextItem("Reading dev loop instructions",
                    "The released plan to implement: " + this.planPath
                    + System.lineSeparator()
                    + DEV_BUILD_LOOP));
        }
        return dispatch(dev, prompt, orders);
    }
    
    @Tool("Wipe Dev back to blank — the next task is UNRELATED and the old state would only create drift. Use compactDev instead when the same task continues.")
    public void clearDev() {
        dev.getMemory().clear();
        reportAction(dev, "reset");
    }
    
    @Tool("Compact Dev — the SAME task continues but the history got long. Keeps the gist, frees context. Use resetDev instead when the next task is unrelated.")
    public String compactDev() {
        return compact(dev);
    }
    
    private String compact(AiAgent slave) {
        return switch (slave.compact(monitor).status()) {
            case COMPACTED -> {
                // R-CT-8: no own line — the slave's compact already sent the one end line via onTool
                var toolResult = slave.getName() + " compacted. " + contextUsed(slave);
                yield toolResult;
            }
            // honest skip (R16 guard) — memory untouched, so N is exact
            case SKIPPED_SMALL -> "Nothing to compact (" + slave.getMemory().size() + " messages)";
            case FAILED_EMPTY -> "Compact failed: compressor returned no summary for " + slave.getName();
        };
    }

    /**
     * The Plan slave, an eager shared instance also exposed via the
     * PO team.
     */
    public AiAgent getPlanSlave() {
        return plan;
    }

    /**
     * The Review slave, an eager shared instance also exposed via the
     * PO team.
     */
    public AiAgent getReviewSlave() {
        return review;
    }

    /**
     * The Dev slave, an eager shared instance also exposed via the PO
     * team.
     */
    public AiAgent getDevSlave() {
        return dev;
    }

    private String dispatch(AiAgent target, String prompt,
            List<ContextItem> orders) {
        ArgsUtil.requireNonBlank(prompt, "prompt");
        target.setTurnContextSupplier(() -> orders);
        compactSlaveBeforeDelegation(target);

        // TODO custom style for the UI as agent message ...
        onTool(target.getName() + " start: " + System.lineSeparator() + prompt);
        var stats = CallStats.start();
        // The slave streams through Jon's monitor (this.monitor) — that is what
        // refreshes its 🟢 in the
        // header status widget while it works (ADR-0025). Keep the monitor
        // passed through.
        try {
            final var response = target.call(prompt, this.monitor);
            final var statsLine = target.getName() + " done. " + contextUsed(target) + " " + stats.suffix();
            final var answer = response != null
                    ? response.aiMessage().text()
                    : null;

            final var result = StringUtil.hasValue(answer)
                    ? answer + System.lineSeparator() + statsLine
                    : target.getName() + " returned no result " + statsLine;
            onTool(statsLine + " " + StringUtil.charsSuffix(result));
            return result;
        } catch (IllegalStateException e) {
            onProblem(target.getName() + " " + e.getMessage());
            return "Failed: " + target.getName() + " " + e.getMessage();
        }
    }

    /** Paul: 0.7 des geteilten Budgets — vor der Delegation compacten, sonst stirbt der Task. */
    private void compactSlaveBeforeDelegation(AiAgent slave) {
        var budget = autoCompactAfter.getAsInt();
        if (budget <= 0) return;                                   // R-CIB-1: 0 = off
        if (slave.getMemory().getTotalTokenUsed() <= (int) (budget * SLAVE_COMPACT_FACTOR)) return;
        var result = slave.compact(monitor);                       // R-CT-8: its compact sends the one end line
        if (result.status() != CompactResult.Status.COMPACTED) return;
    }

    private void reportAction(AiAgent target, String action) {
        onTool(target.getName() + " " + action + ".");
    }

    private String contextUsed(AiAgent agent) {
        var memory = agent.getMemory();
        return "Context: " + StringUtil.estimateAware(memory.isTokenEstimate(), String.valueOf(memory.getTotalTokenUsed()))
                + " token - " + agent.tokenContextUsedInPercent() + "% used.";
    }
}
