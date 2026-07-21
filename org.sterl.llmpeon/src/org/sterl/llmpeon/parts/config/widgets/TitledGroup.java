package org.sterl.llmpeon.parts.config.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

public class TitledGroup extends Composite {
    private final Group group;

    public TitledGroup(Composite parent, String title) {
        super(parent, SWT.NONE);
        int numColumns = 2;
        setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false, numColumns, 1));
        setLayout(new FillLayout());

        group = new Group(this, SWT.NONE);
        group.setText(title);
        group.setLayout(new GridLayout());
    }

    public Group getGroup() {
        return group;
    }
}
