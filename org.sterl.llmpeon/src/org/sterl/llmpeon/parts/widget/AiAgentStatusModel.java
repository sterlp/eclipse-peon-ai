package org.sterl.llmpeon.parts.widget;

import java.util.ArrayList;
import java.util.List;

import org.sterl.llmpeon.agent.NamedAgent;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * Pure (SWT-free) render model for {@link AiAgentStatusWidget}. Maps an orchestrator's visible team
 * (a {@link NamedAgent} list — see {@code AiPoAgent.getTeam()}) to display rows {@code uiName (Xk)}.
 * <p>
 * The first entry is the orchestrator itself (Da Boss); the rest are his slaves (Da Thinka, Da Mek).
 * The 🟢 sits on the working leaf: a slave glows while it works, and — the <b>leaf rule</b> — Da Boss
 * glows only when he works AND no slave is working. So Jon lights up while he composes himself, but
 * during delegation the busy slave carries the glow and his row stays calm.
 * <p>
 * The public {@link #rows(List)} reads each member's live {@code isWorking()}/context; the pure
 * {@link #build(List)} core takes a value snapshot so the leaf-rule + formatting logic is unit-testable
 * without SWT or a live agent. See {@code docs/agenten-status-im-header-mvp-plan.md} / ADR-0025.
 */
public final class AiAgentStatusModel {

    /** One rendered row: the label {@code uiName (Xk)} and whether it shows the 🟢 working dot. */
    public record Entry(String text, boolean working) {}

    /** Snapshot of one team member for the pure builder (name, context size, momentary work). */
    record Row(String uiName, long tokens, boolean working) {}

    private AiAgentStatusModel() {}

    /** Live entry point for the widget: reads {@code uiName} + live {@code isWorking()}/context off
     *  each team member, then applies the pure {@link #build(List)} logic. */
    public static List<Entry> rows(List<NamedAgent> team) {
        if (team == null || team.isEmpty()) return List.of();
        var snapshot = new ArrayList<Row>(team.size());
        for (var member : team) {
            var agent = member.agent();
            snapshot.add(new Row(member.uiName(), agent.getMemory().getTotalTokenUsed(), agent.isWorking()));
        }
        return build(snapshot);
    }

    /** Pure core: first row is Da Boss (glows only if working AND no slave below works), the rest glow
     *  on their own work. Package-private on purpose — the headless-testable seam. */
    static List<Entry> build(List<Row> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        boolean anySlaveWorking = rows.stream().skip(1).anyMatch(Row::working);
        var entries = new ArrayList<Entry>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            boolean working = i == 0 ? (r.working() && !anySlaveWorking) : r.working();
            entries.add(new Entry(r.uiName() + " (" + StringUtil.toK(r.tokens()) + ")", working));
        }
        return entries;
    }
}
