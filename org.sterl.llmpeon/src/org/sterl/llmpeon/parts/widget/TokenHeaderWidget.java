package org.sterl.llmpeon.parts.widget;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.shared.TokenStats;

import dev.langchain4j.model.output.TokenUsage;

/**
 * Self-contained readout of cumulative session token spend ({@code ↑ sent  ↓ received}).
 * <p>
 * Owns its own {@link TokenStats} — the widget's lifecycle equals the chat view's, so the totals are
 * cross-agent and never reset while the view is open (see {@code docs/token-usage.md} R3). Always
 * visible (shows {@code ↑ 0k  ↓ 0k} before any usage). Painted on the native white background so it
 * blends into the header strip. All methods assume the SWT UI thread.
 */
public class TokenHeaderWidget extends Composite {

    private final TokenStats stats = new TokenStats();
    private final Label label;

    public TokenHeaderWidget(Composite parent, int style) {
        super(parent, style);
        Color bgWhite = getDisplay().getSystemColor(SWT.COLOR_WHITE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginHeight = 0;
        layout.marginWidth = 0;
        setLayout(layout);
        setBackground(bgWhite);
        setBackgroundMode(SWT.INHERIT_DEFAULT);

        label = new Label(this, SWT.NONE);
        label.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, true));
        refresh();
    }

    /** Accumulates one LLM response's real usage and refreshes the readout. */
    public void addUsage(TokenUsage usage) {
        stats.add(usage);
        refresh();
    }

    private void refresh() {
        if (isDisposed()) return;
        long sent = stats.getSent();
        long received = stats.getReceived();
        label.setText("↑ " + StringUtil.toK(sent) + "  ↓ " + StringUtil.toK(received));
        label.setToolTipText("Session tokens — sent (input): " + sent
                + ", received (output): " + received);
        requestReflow();
    }

    /**
     * Re-layout self and ask the header (and its parent) to recompute — a growing count changes the
     * readout's preferred width, which only shows once the enclosing GridLayout re-runs.
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
