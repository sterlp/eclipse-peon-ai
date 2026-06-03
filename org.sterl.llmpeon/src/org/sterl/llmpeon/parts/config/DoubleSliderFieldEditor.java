package org.sterl.llmpeon.parts.config;

import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Scale;

public class DoubleSliderFieldEditor extends FieldEditor {

    private static final int MIN_INTERNAL = 0;
    private static final int MAX_INTERNAL = 20;
    private static final double STEP = 0.1;

    private Scale scale;
    private Label valueLabel;

    public DoubleSliderFieldEditor(String preferenceName, String labelText, Composite parent) {
        super(preferenceName, labelText, parent);
    }

    @Override
    protected void doFillIntoGrid(Composite parent, int numColumns) {
        Control labelControl = getLabelControl(parent);
        GridData labelGd = new GridData();
        labelGd.horizontalSpan = 1;
        labelGd.verticalAlignment = SWT.CENTER;
        labelGd.grabExcessHorizontalSpace = false;
        labelControl.setLayoutData(labelGd);

        Composite sliderRow = new Composite(parent, SWT.NONE);
        GridLayout rowLayout = new GridLayout(2, false);
        rowLayout.marginWidth = 0;
        rowLayout.marginHeight = 0;
        sliderRow.setLayout(rowLayout);
        GridData rowGd = new GridData(GridData.FILL_HORIZONTAL, GridData.CENTER, true, false);
        rowGd.horizontalSpan = numColumns - 1;
        sliderRow.setLayoutData(rowGd);

        scale = new Scale(sliderRow, SWT.HORIZONTAL);
        scale.setMinimum(MIN_INTERNAL);
        scale.setMaximum(MAX_INTERNAL);
        scale.setIncrement(1);
        scale.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        valueLabel = new Label(sliderRow, SWT.NONE);
        GridData valGd = new GridData(SWT.END, SWT.CENTER, false, false);
        valGd.minimumWidth = 40;
        valueLabel.setLayoutData(valGd);
        valueLabel.setText(formatDisplay(0));

        scale.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
            valueLabel.setText(formatDisplay(scale.getSelection()));
        }));
    }

    @Override
    public int getNumberOfControls() {
        return 2;
    }

    @Override
    public void adjustForNumColumns(int numColumns) {}

    @Override
    protected void doLoad() {
        // use getDouble — matches setDefault(key, double) in PreferenceInitializer
        double value = getPreferenceStore().getDouble(getPreferenceName());
        setScaleValue(value);
    }

    @Override
    protected void doLoadDefault() {
        double value = getPreferenceStore().getDefaultDouble(getPreferenceName());
        setScaleValue(value);
    }

    @Override
    protected void doStore() {
        double value = scale.getSelection() * STEP;
        // round to 1 decimal to avoid floating point drift
        value = Math.round(value * 10.0) / 10.0;
        getPreferenceStore().setValue(getPreferenceName(), value);
    }

    @Override
    public void setFocus() {
        if (scale != null && !scale.isDisposed()) {
            scale.setFocus();
        }
    }

    private void setScaleValue(double value) {
        value = Math.max(MIN_INTERNAL * STEP, Math.min(MAX_INTERNAL * STEP, value));
        int internal = (int) Math.round(value / STEP);
        scale.setSelection(internal);
        valueLabel.setText(formatDisplay(internal));
    }

    private String formatDisplay(int internalValue) {
        return String.format("%.1f", internalValue * STEP);
    }
}