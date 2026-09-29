package org.sterl.llmpeon.parts.config.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.sterl.llmpeon.provider.ExtraBodyExamples;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * The extra-body JSON editor (multi-line field + paste-ready examples + status label) as a plain
 * controller (no SWT parent of its own, like {@link ModelConfigWidget}): it creates its controls
 * directly in the given 2-column parent grid.
 *
 * <p><b>Live provider gate (R-DEF-11):</b> the controls are created once and toggled via
 * {@code GridData.exclude} — the caller switches the gate through {@link #applyGate(boolean)}
 * (initial state via the constructor's {@code visible}). Hiding never deletes: the last visible
 * value (loaded or typed) is captured in the preserved value and comes back when the provider
 * supports extra body again; while hidden, {@link #getExtraBody()} keeps returning it, so a page
 * OK never removes a stored value (hidden ≠ delete).</p>
 *
 * <p>Examples (caching.md R3): a single compact row (label + one button per example, tooltip =
 * description) and a status label. A paste simply replaces the field content (2c D2, no dialog).</p>
 */
public class ExtraBodyWidget {

    private final Composite parent;
    private final int labelStyle;
    private final Label label;
    private final Text jsonText;
    private final Composite examplesRow;
    private final Label statusLabel;
    private boolean statusShown; // a paste happened → the status label carries feedback
    private boolean gateOpen;
    private String preservedBody; // the last visible value — the truth while the gate is closed

    /**
     * @param parent the 2-column grid to build into
     * @param visible whether the provider can carry extra body params (initial gate state)
     */
    public ExtraBodyWidget(Composite parent, boolean visible) {
        this(parent, visible, SWT.END);
    }

    /**
     * @param parent the 2-column grid to build into
     * @param visible whether the provider can carry extra body params (initial gate state)
     * @param labelStyle the label alignment: {@code SWT.LEFT} (basic page, JFace Field-Editor
     *             style, no GridData) or {@code SWT.END} (advanced page / agent sections, GridData)
     */
    public ExtraBodyWidget(Composite parent, boolean visible, int labelStyle) {
        this.parent = parent;
        this.labelStyle = labelStyle;
        label = addLabel("Extra body (JSON):");
        jsonText = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.WRAP);
        var gd = new GridData(SWT.FILL, SWT.FILL, true, false);
        gd.horizontalSpan = 2;
        gd.heightHint = 80;
        jsonText.setLayoutData(gd);
        examplesRow = buildExamplesRow();
        statusLabel = new Label(parent, SWT.NONE);
        var statusGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        statusGd.horizontalSpan = 2;
        statusGd.exclude = true; // no extra space until a paste happened
        statusLabel.setLayoutData(statusGd);
        statusLabel.setVisible(false);
        applyGate(visible);
    }

    /** Populates the preserved value and — while the gate is open — the field (null-safe). */
    public void setBody(String body) {
        preservedBody = StringUtil.stripToNull(body);
        if (gateOpen) {
            jsonText.setText(StringUtil.stripToEmpty(body));
        }
    }

    /**
     * The field content while the gate is open, the preserved value while it is closed
     * (hidden ≠ delete, R-DEF-11) — null when neither carries a value.
     */
    public String getExtraBody() {
        return gateOpen ? StringUtil.stripToNull(jsonText.getText()) : preservedBody;
    }

    /**
     * The live provider gate (R-DEF-11): closing captures the field content in the preserved
     * value, opening restores it. The controls stay in place (created once, toggled via
     * {@code GridData.exclude} — dispose + recreate would break the binding field order).
     */
    public void applyGate(boolean supports) {
        if (gateOpen != supports) {
            if (supports) {
                jsonText.setText(StringUtil.stripToEmpty(preservedBody)); // was closed: the preserved value is truth
            } else {
                preservedBody = StringUtil.stripToNull(jsonText.getText()); // about to close: the field is truth
            }
            gateOpen = supports;
        }
        setGateVisible(label, supports);
        setGateVisible(jsonText, supports);
        setGateVisible(examplesRow, supports);
        setGateVisible(statusLabel, supports && statusShown);
        parent.layout();
    }

    private Composite buildExamplesRow() {
        var examples = ExtraBodyExamples.all();
        var row = new Composite(parent, SWT.NONE);
        var rowGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        rowGd.horizontalSpan = 2;
        row.setLayoutData(rowGd);
        row.setLayout(new GridLayout(examples.size() + 1, false));
        var label = new Label(row, SWT.NONE);
        label.setText("Examples:");
        for (var example : examples) {
            var button = new Button(row, SWT.PUSH);
            button.setText(example.name());
            button.setToolTipText(example.description());
            button.addListener(SWT.Selection, e -> pasteExample(example));
        }
        return row;
    }

    private void pasteExample(ExtraBodyExamples.Example example) {
        jsonText.setText(example.json());
        statusLabel.setText(example.name() + " example inserted.");
        statusShown = true;
        setGateVisible(statusLabel, true);
        parent.layout();
    }

    private Label addLabel(String text) {
        var label = new Label(parent, labelStyle);
        label.setText(text);
        if (labelStyle == SWT.END) {
            label.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false)); // agent-section style
        } else {
            label.setLayoutData(new GridData()); // plain GridData — the instance is needed for the exclude toggle
        }
        return label;
    }

    private static void setGateVisible(Control control, boolean visible) {
        ((GridData) control.getLayoutData()).exclude = !visible; // GridLayout honors only exclude
        control.setVisible(visible);
    }
}
