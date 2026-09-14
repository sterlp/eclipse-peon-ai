package org.sterl.llmpeon.parts.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.sterl.llmpeon.agent.AiAgentStatusModel;
import org.sterl.llmpeon.agent.AiAgentStatusModel.Entry;
import org.sterl.llmpeon.agent.NamedAgent;
import org.sterl.llmpeon.parts.shared.EclipseUiUtil;
import org.sterl.llmpeon.parts.shared.ImageUtil;
import org.sterl.llmpeon.parts.shared.SwtUtil;

/**
 * Header status of the active orchestrator's team: one row per {@link NamedAgent} it exposes (e.g.
 * {@code Da Boss (12k)} · {@code Da Thinka (0k)} · {@code Da Mek (0k)}). A green ball 🟢 marks the
 * working leaf — a busy slave glows, while Da Boss glows only when he works with nothing delegated
 * (the leaf rule lives in {@link AiAgentStatusModel}). Each slave row carries a flat compact
 * button (tooltip {@code Compact Da X}); clicking it compresses exactly that agent via the
 * {@code onSlaveCompactClick} callback — Da Boss has none (the action bar owns his compact).
 * An empty team (any non-PO agent) renders nothing.
 * <p>
 * Rows are structural: rebuilt only when the team size changes (agent switch PO ↔ non-PO),
 * otherwise each {@link #refresh()} updates label text and button enablement in place — the
 * monitor events call this often per turn. The widget renders and forwards clicks only; all
 * logic (leaf rule, enablement, feedback text) lives in {@link AiAgentStatusModel}. All methods
 * assume the SWT UI thread.
 */
public class AiAgentStatusWidget extends Composite {

    private static final String SEP = "   ·   ";
    private static final String WORKING = "🟢 ";

    /** One built row: the team member it renders, its composite, label and (slaves only) button. */
    private record Row(NamedAgent member, Composite composite, Label label, Button button) {}

    private final Supplier<List<NamedAgent>> team;
    private final Consumer<NamedAgent> onSlaveCompactClick;
    private final Supplier<Boolean> turnInFlight;
    private final List<Row> rows = new ArrayList<>();

    public AiAgentStatusWidget(Composite parent, int style,
            Supplier<List<NamedAgent>> team,
            Consumer<NamedAgent> onSlaveCompactClick,
            Supplier<Boolean> turnInFlight) {
        super(parent, style);
        this.team = team;
        this.onSlaveCompactClick = onSlaveCompactClick;
        this.turnInFlight = turnInFlight;

        RowLayout layout = new RowLayout(SWT.HORIZONTAL);
        layout.pack = true;
        // this SWT generation defaults wrap=true — the roster must never grow a second line,
        // overflow clips from the right (Da Dok drops first)
        layout.wrap = false;
        layout.center = true;
        layout.marginHeight = 0;
        layout.marginWidth = 0;
        setLayout(layout);
        setBackgroundMode(SWT.INHERIT_DEFAULT);

        refresh();
    }

    /** Rebuilds the status rows from the active agent's current team snapshot. UI-thread only. */
    public void refresh() {
        if (isDisposed()) return;

        var members = team.get();
        var entries = AiAgentStatusModel.rows(members);

        if (entries.size() != rows.size()) {
            rebuild(members, entries);
        }
        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            var e = entries.get(i);
            r.label().setText(text(i, e));
            if (r.button() != null) {
                r.button().setEnabled(
                        AiAgentStatusModel.compactEnabled(r.member().agent().isWorking(), turnInFlight.get()));
            }
        }

        requestReflow();
    }

    /** Label text for row {@code i}: the "·" prefix separates it from the row before it, 🟢 leads
     *  while the row works — the former single-label format, now per row. */
    private static String text(int i, Entry e) {
        var sb = new StringBuilder();
        if (i > 0) sb.append(SEP);
        if (e.working()) sb.append(WORKING);
        sb.append(e.text());
        return sb.toString();
    }

    /** Disposes and rebuilds every row — only when the team size changes (agent switch). */
    private void rebuild(List<NamedAgent> members, List<Entry> entries) {
        for (var r : rows) r.composite().dispose();
        rows.clear();
        for (int i = 0; i < members.size(); i++) {
            var member = members.get(i);
            var entry = entries.get(i);

            var row = new Composite(this, SWT.NONE);
            RowLayout rl = new RowLayout(SWT.HORIZONTAL);
            rl.pack = true;
            rl.wrap = false;
            rl.center = true;
            rl.spacing = 2;
            rl.marginHeight = 0;
            rl.marginWidth = 0;
            row.setLayout(rl);
            row.setBackgroundMode(SWT.INHERIT_DEFAULT);
            row.setData(WidgetCss.CSS_CLASS_NAME_KEY, EclipseUiUtil.CSS_CLASS_HEADER_BAR_WIDGET);

            var label = new Label(row, SWT.NONE);
            label.setData(WidgetCss.CSS_CLASS_NAME_KEY, EclipseUiUtil.CSS_CLASS_HEADER_BAR_WIDGET);

            Button button = null;
            if (entry.slave()) { // Da Boss row: no button — the action bar owns his compact
                button = SwtUtil.createIconButton(row,
                        ImageUtil.loadImage(row,
                                EclipseUiUtil.DARK_THEME_NAME.equals(EclipseUiUtil.resolveTheme())
                                        ? ImageUtil.COMPACT_DARK : ImageUtil.COMPACT),
                        "Compact " + member.uiName());
                button.setData(WidgetCss.CSS_CLASS_NAME_KEY, EclipseUiUtil.CSS_CLASS_HEADER_BAR_WIDGET);
                button.addListener(SWT.Selection, e -> {
                    if (!isDisposed()) onSlaveCompactClick.accept(member);
                });
            }
            rows.add(new Row(member, row, label, button));
        }
    }

    /**
     * Re-layout self and ask the header (and its parent) to recompute — a changing team changes the
     * preferred width, which only shows once the enclosing GridLayout re-runs. Mirrors
     * {@link TokenHeaderWidget}.
     */
    private void requestReflow() {
        layout(true, true);
        Composite p = getParent();
        if (p == null || p.isDisposed()) return;
        p.layout(true, true);
        Composite pp = p.getParent();
        if (pp != null && !pp.isDisposed()) pp.layout(new Control[] { p });
    }
}
