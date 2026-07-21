package org.sterl.llmpeon.parts.config;

import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

/**
 * A {@link FieldEditor} backed by an <b>editable</b> combo box: it offers a list of preset values to
 * pick from, but the user can still type any value. Unlike JFace's {@code ComboFieldEditor} (which is
 * read-only and maps labels to values), the typed text is stored verbatim.
 *
 * <p>Used for the per-agent reasoning on/off strings so common values are one click away while
 * arbitrary provider-specific values (e.g. LM Studio's custom {@code reasoning} property) keep
 * working.</p>
 */
public class EditableComboFieldEditor extends FieldEditor {

    private final String[] presets;
    private Combo combo;

    public EditableComboFieldEditor(String preferenceName, String labelText, String[] presets, Composite parent) {
        this.presets = presets;
        init(preferenceName, labelText);
        createControl(parent);
    }

    @Override
    protected void doFillIntoGrid(Composite parent, int numColumns) {
        Control labelControl = getLabelControl(parent);
        GridData labelGd = new GridData();
        labelGd.horizontalSpan = 1;
        labelGd.verticalAlignment = SWT.CENTER;
        labelControl.setLayoutData(labelGd);

        combo = new Combo(parent, SWT.DROP_DOWN);
        combo.setItems(presets);
        GridData comboGd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        comboGd.horizontalSpan = numColumns - 1;
        comboGd.minimumWidth = 150;
        combo.setLayoutData(comboGd);
    }

    @Override
    public int getNumberOfControls() {
        return 2;
    }

    @Override
    protected void adjustForNumColumns(int numColumns) {
        ((GridData) combo.getLayoutData()).horizontalSpan = numColumns - 1;
    }

    @Override
    protected void doLoad() {
        combo.setText(getPreferenceStore().getString(getPreferenceName()));
    }

    @Override
    protected void doLoadDefault() {
        combo.setText(getPreferenceStore().getDefaultString(getPreferenceName()));
    }

    @Override
    protected void doStore() {
        getPreferenceStore().setValue(getPreferenceName(), combo.getText().trim());
    }

    @Override
    public void setFocus() {
        if (combo != null && !combo.isDisposed()) {
            combo.setFocus();
        }
    }
}
