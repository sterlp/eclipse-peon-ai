package org.sterl.llmpeon.parts.config.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.sterl.llmpeon.provider.ExtraBodyExamples;
import org.sterl.llmpeon.shared.StringUtil;

/**
 * The extra-body JSON editor (multi-line field + paste-ready examples + status label) as a plain
 * controller (no SWT parent of its own, like {@link ModelConfigWidget}): it creates its controls
 * directly in the given 2-column parent grid.
 *
 * <p>Examples (caching.md R3): a single compact row (label + one button per example, tooltip =
 * description) and a status label. Built only when the caller's provider can carry extra body
 * params (provider.md R3 — the caller passes the gate via {@code visible}). A paste simply
 * replaces the field content (2c D2, no dialog).</p>
 */
public class ExtraBodyWidget {

    private final Composite parent;
    private final Text jsonText;
    private Label examplesLabel; // assigned in buildJsonExamples (only when visible)

    /**
     * @param parent the 2-column grid to build into
     * @param visible whether the provider can carry extra body params (provider.md R3 gate);
     *                {@code false} builds nothing and the accessors are no-ops / null
     */
    public ExtraBodyWidget(Composite parent, boolean visible) {
        this.parent = parent;
        if (!visible) {
            jsonText = null;
            examplesLabel = null;
            return;
        }
        addLabel("Extra body (JSON):");
        jsonText = new Text(parent, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.WRAP);
        var gd = new GridData(SWT.FILL, SWT.FILL, true, false);
        gd.horizontalSpan = 2;
        gd.heightHint = 80;
        jsonText.setLayoutData(gd);
        buildJsonExamples();
    }

    /** Populates the field (null-safe; a no-op when the provider does not support extra body). */
    public void setBody(String body) {
        if (jsonText != null) {
            jsonText.setText(StringUtil.stripToEmpty(body));
        }
    }

    /** The field content (null when unsupported or empty). */
    public String getExtraBody() {
        return jsonText == null ? null : StringUtil.stripToNull(jsonText.getText());
    }

    private void buildJsonExamples() {
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
        examplesLabel = new Label(parent, SWT.NONE);
        var labelGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        labelGd.horizontalSpan = 2;
        labelGd.exclude = true; // no extra space until a paste happened (GridLayout only filters GridData.exclude)
        examplesLabel.setLayoutData(labelGd);
        examplesLabel.setVisible(false);
    }

    private void pasteExample(ExtraBodyExamples.Example example) {
        jsonText.setText(example.json());
        examplesLabel.setText(example.name() + " example inserted.");
        ((GridData) examplesLabel.getLayoutData()).exclude = false;
        examplesLabel.setVisible(true);
        parent.layout();
    }

    private void addLabel(String text) {
        var label = new Label(parent, SWT.NONE);
        label.setText(text);
        label.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
    }
}
