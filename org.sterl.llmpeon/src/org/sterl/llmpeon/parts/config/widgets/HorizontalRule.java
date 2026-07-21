package org.sterl.llmpeon.parts.config.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

public class HorizontalRule extends Composite {
    public HorizontalRule(Composite parent) {
        super(parent, SWT.NONE);
        int numColumns = 1;
        if (parent.getLayout() instanceof GridLayout gl) {
            numColumns = gl.numColumns;
        }
        setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, numColumns, 1));
        setLayout(new GridLayout());
        Label sep = new Label(this, SWT.SEPARATOR | SWT.HORIZONTAL);
        sep.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }
}