package org.sterl.llmpeon.parts.widget;

import java.util.List;
import java.util.function.Supplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.sterl.llmpeon.agent.NamedAgent;

/**
 * Header status of the active orchestrator's team: one row per {@link NamedAgent} it exposes (e.g.
 * {@code Da Boss (12k)} · {@code Da Thinka (0k)} · {@code Da Mek (0k)}). A green ball 🟢 marks the
 * working leaf — a busy slave glows, while Da Boss glows only when he works with nothing delegated
 * (the leaf rule lives in {@link AiAgentStatusModel}). An empty team (any non-PO agent) renders
 * nothing.
 * <p>
 * A plain {@link Label} on the native white background (mirrors {@link TokenHeaderWidget}) — no
 * {@code StyledText}, which rendered a grey box on macOS. Holds no state: every {@link #refresh()}
 * re-reads the supplier, so it always shows the live IST. All methods assume the SWT UI thread.
 */
public class AiAgentStatusWidget extends Composite {

    private static final String SEP = "   ·   ";
    private static final String WORKING = "🟢 ";

    private final Supplier<List<NamedAgent>> team;
    private final Label label;

    public AiAgentStatusWidget(Composite parent, int style, Supplier<List<NamedAgent>> team) {
        super(parent, style);
        this.team = team;

        Color bgWhite = getDisplay().getSystemColor(SWT.COLOR_WHITE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginHeight = 0;
        layout.marginWidth = 0;
        setLayout(layout);
        setBackground(bgWhite);
        setBackgroundMode(SWT.INHERIT_DEFAULT);

        label = new Label(this, SWT.NONE);
        label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
        label.setBackground(bgWhite);

        refresh();
    }

    /** Rebuilds the status line from the active agent's current team snapshot. UI-thread only. */
    public void refresh() {
        if (isDisposed()) return;

        var entries = AiAgentStatusModel.rows(team.get());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(SEP);
            var e = entries.get(i);
            if (e.working()) sb.append(WORKING);
            sb.append(e.text());
        }
        label.setText(sb.toString());

        requestReflow();
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
