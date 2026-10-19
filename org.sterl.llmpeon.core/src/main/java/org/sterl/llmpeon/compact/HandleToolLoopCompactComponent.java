package org.sterl.llmpeon.compact;

import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.shared.AiMonitor;
import org.sterl.llmpeon.tool.ToolLoopRequest;
import org.sterl.llmpeon.tool.ToolService;

import dev.langchain4j.data.message.UserMessage;

/**
 * State-based compact decision at the end of a tool round, before the next model call (R-CC-18,
 * ADR-0070): one component, one decision — the hint-only event gate
 * ({@code ranTool(compactSession)} + {@code isCompactedThisTurn}) is gone.
 *
 * <p>Over the raw config budget ({@code used > autoCompactAfter}) the conversation is
 * compacted DIRECTLY — no ask to the model, so a budget-exceeding context can no longer die at the
 * model limit. Inside the top 5 % band only the hint fires (R-CC-4 dedup, R-CC-15 fallback).
 * The consequence of a compact hangs on the one who runs it (ADR-0070): here the component, in
 * the tool path {@code CompactSessionTool}.</p>
 *
 * <p><b>One instance per tool loop</b> ({@code ToolService.executeLoop}): the
 * {@code attemptedDirect} flag gives "one direct attempt per turn" (R-CC-18.1) without leaking
 * into sub-loops or the next turn.</p>
 */
public class HandleToolLoopCompactComponent {

    /** One direct attempt per turn (R-CC-18.1): a SKIPPED_SMALL/FAILED_EMPTY is not retried in-loop. */
    private boolean attemptedDirect = false;

    /**
     * The state-based decision, run after every tool round's {@code addResult}, before the next
     * model call.
     *
     * @param hasCompactTool true when THIS loop's tool set offers compactSession (R-CC-15) —
     *                       false takes the honest fallback even when a budget applies
     */
    public void handle(ToolLoopRequest req, boolean hasCompactTool) {
        var memory = req.getMemory();
        var agent = req.getAgent();
        var budget = req.getConfig().getAutoCompactAfter(); // raw config — the PO slave factor applies only before a new task
        if (budget <= 0) return; // R-CIB-1: 0 = off

        var used = memory.getTotalTokenUsed();
        if (used > budget) { // R-CC-18.1 — over budget: compact directly, no hint
            if (agent != null && memory.size() > CompactConstants.MIN_COMPACT_MESSAGES // R-CC-8
                    && !attemptedDirect) {
                attemptedDirect = true;
                var result = agent.compact(req.getMonitor()); // AbstractAgent.compact null-safes the monitor itself
                if (result.status() == CompactResult.Status.COMPACTED) {
                    memory.reevaluateTokens(); // R-CC-2: the counter holds the small post-compact estimate
                    req.staticMessages(agent.buildStaticMessages(req.getMonitor()));
                    AiMonitor.nullSafety(req.getMonitor()).onTool("🗜 Direct compact for " + agent.getName()
                            + " (tool loop over budget): " + result.resultLine()
                            + memory.tokenDiagnosis()); // reason + numbers, R-CC-10
                }
                // SKIPPED_SMALL / FAILED_EMPTY: no retry (flag), no hint (over budget) — R-CC-18.1
            } else if (agent == null && memory.size() > CompactConstants.MIN_COMPACT_MESSAGES) {
                fallbackHint(req); // R-CC-15: no owning agent to compact — honest out
            }
            return;
        }
        if (used <= budget * 0.95) return; // R-CC-18.3: nothing below the band
        if (memory.size() <= CompactConstants.MIN_COMPACT_MESSAGES) return; // R-CC-8
        if (agent == null || !hasCompactTool) fallbackHint(req); // R-CC-15
        else compactHint(req, agent, budget); // R-CC-4 dedup; text shows the gate budget (D5)
    }

    /**
     * Stuck path (think-only rounds): force bypasses the off-switch and the 95 % band — the
     * context is stuck, the hint is the only escape. R-CC-8 and the R-CC-4 dedup stay.
     */
    public void forceHint(ToolLoopRequest req, boolean hasCompactTool) {
        var memory = req.getMemory();
        if (memory.size() <= CompactConstants.MIN_COMPACT_MESSAGES) return; // R-CC-8
        var agent = req.getAgent();
        if (agent == null || !hasCompactTool) {
            fallbackHint(req); // R-CC-15
        } else {
            compactHint(req, agent, req.getConfig().getAutoCompactAfter()); // the gate budget, same source as handle
        }
    }

    /**
     * The compact hint (R-CC-4): one per memory — a cleared memory (successful compact) re-arms
     * it. The text shows the BUDGET the gate compared against ({@code budget} — the raw config
     * value): the hint must never lie about the limit.
     */
    private void compactHint(ToolLoopRequest req, AiAgent agent, int budget) {
        var memory = req.getMemory();
        if (memory.containsMessage(ToolService.COMPACT_HINT)) return;
        var used = memory.getTotalTokenUsed() + " tokens of " + budget + " used.";
        // R-CC-10: the diagnosis rides on the onTool LOG line only — the UserMessage below must
        // stay clean (it becomes LLM context; the diagnosis is not for the model).
        AiMonitor.nullSafety(req.getMonitor()).onTool("🗜 Compact hint for " + agent.getName() + " added! " + used + memory.tokenDiagnosis());
        req.addMessage(new UserMessage(ToolService.COMPACT_HINT + System.lineSeparator() + used));
    }

    /**
     * R-CC-15: the honest fallback — the context is (almost) full but cannot be compacted
     * (no owning agent / the tool set does not offer compactSession). Once per memory.
     */
    private void fallbackHint(ToolLoopRequest req) {
        var memory = req.getMemory();
        // avoid that a search agent or any other agent without a compact tools get stuck
        final var msg = "Your context window is almost full and cannot be compacted. " +
                "Stop calling tools now and give your best final answer with what you have so far.";
        if (!memory.containsMessage(msg)) {
            req.addMessage(new UserMessage(msg));
            AiMonitor.nullSafety(req.getMonitor()).onTool("Info for search agent added to return now a result, context full: " + memory.getTotalTokenUsed());
        }
    }
}
