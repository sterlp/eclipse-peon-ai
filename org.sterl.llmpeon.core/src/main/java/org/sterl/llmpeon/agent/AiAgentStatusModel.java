package org.sterl.llmpeon.agent;

import java.util.ArrayList;
import java.util.List;

import org.sterl.llmpeon.model.CompactResult;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * Pure (SWT-free) render model for the plugin's agent-status widget. Maps an orchestrator's visible team
 * (a {@link NamedAgent} list — see {@code AiPoAgent.getTeam()}) to display rows {@code uiName (Xk)}.
 * <p>
 * The first entry is the orchestrator itself (Jon); the rest are his team (Plan, Dev, Review).
 * The 🟢 sits on the working leaf: a slave glows while it works, and — the <b>leaf rule</b> — Jon
 * glows only when he works AND no slave is working. So Jon lights up while he composes himself, but
 * during delegation the busy slave carries the glow and his row stays calm. A member that is
 * compacting shows 🟡 instead of 🟢 (R-CT-4) — busy for the leaf rule, but visibly compacting.
 * <p>
 * The public {@link #rows(List)} reads each member's live {@code isWorking()}/{@code isCompacting()}/context; the pure
 * {@link #build(List)} core takes a value snapshot so the leaf-rule + formatting logic is unit-testable
 * without SWT or a live agent. See {@code org.sterl.llmpeon/docs/agenten-status-im-header-mvp-plan.md} / ADR-0025.
 */
public final class AiAgentStatusModel {

    /** Display state of a row — {@link State#COMPACTING} wins over {@link State#WORKING} (R-CT-4). */
    public enum State { IDLE, WORKING, COMPACTING }

    /** One rendered row: the label {@code uiName (Xk)}, its {@link State} (🟢 working, 🟡 compacting),
     *  and whether it is a slave row ({@code i > 0}) — only slaves carry a per-agent compact button
     *  (Jon stays compacted via the action bar). */
    public record Entry(String text, State state, boolean slave) {
        public boolean working() { return state == State.WORKING; }
        public boolean compacting() { return state == State.COMPACTING; }
    }

    /** Snapshot of one team member for the pure builder (name, context size, momentary work,
     *  momentary compact, estimate flag). */
    record Row(String uiName, long tokens, boolean working, boolean compacting, boolean estimate) {}

    private AiAgentStatusModel() {}

    /** Live entry point for the widget: reads {@code uiName} + live {@code isWorking()}/
     *  {@code isCompacting()}/context off each team member, then applies the pure {@link #build(List)} logic. */
    public static List<Entry> rows(List<NamedAgent> team) {
        if (team == null || team.isEmpty()) return List.of();
        var snapshot = new ArrayList<Row>(team.size());
        for (var member : team) {
            var agent = member.agent();
            snapshot.add(new Row(member.uiName(), agent.getMemory().getTotalTokenUsed(),
                    agent.isWorking(), agent.isCompacting(), agent.getMemory().isTokenEstimate()));
        }
        return build(snapshot);
    }

    /** Pure core: first row is Jon (glows only if he works AND no slave below is busy), the rest
     *  carry their own dot — 🟢 on work, 🟡 on a compact (R-CT-4). Package-private on purpose — the
     *  headless-testable seam. */
    static List<Entry> build(List<Row> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        // leaf rule: a busy slave keeps Jon dark — compacting counts as busy, not as working
        boolean anySlaveBusy = rows.stream().skip(1).anyMatch(r -> r.working() || r.compacting());
        var entries = new ArrayList<Entry>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            var state = r.compacting() ? State.COMPACTING
                    : r.working() && (i > 0 || !anySlaveBusy) ? State.WORKING
                    : State.IDLE;
            entries.add(new Entry(r.uiName() + " (" + StringUtil.estimateAware(r.estimate(), StringUtil.toK(r.tokens())) + ")", state, i > 0));
        }
        return entries;
    }

    /** Button only enabled when the agent IS idle and no turn/compact is in flight. */
    public static boolean compactEnabled(boolean agentWorking, boolean turnInFlight) {
        return !agentWorking && !turnInFlight;
    }

    /**
     * Status-line feedback of a slave-compact job (R16 skip → feedback, not silence).
     * A null result (the job threw before compact returned) keeps the legacy skip text —
     * the exception itself is surfaced by the status line. COMPACTED carries the same
     * numbers as the log and the tool result (R-CIB-6, {@link CompactResult#resultLine()}).
     */
    public static String compactResult(CompactResult result, String uiName) {
        if (result == null) return "Nothing to compact";
        return switch (result.status()) {
            case COMPACTED -> "Compacted " + uiName + ": " + result.resultLine();
            case SKIPPED_SMALL -> "Nothing to compact";
            case FAILED_EMPTY -> "Compact failed: no summary for " + uiName;
        };
    }
}
