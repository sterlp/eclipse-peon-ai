package org.sterl.llmpeon.parts.shared;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

public class EclipseUiUtil {

    public static Label newSeparator(Composite parent) {
        var result = new Label(parent, SWT.SEPARATOR | SWT.VERTICAL);
        result.setLayoutData(new RowData(SWT.DEFAULT, 16));
        return result;
    }
}
