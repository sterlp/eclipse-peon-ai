package org.sterl.llmpeon.parts.config;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public class AiPeonPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
    @Override
    public void init(IWorkbench workbench) {}

    @Override
    protected Control createContents(Composite parent) {
        return new Composite(parent, SWT.NONE);
    }
}
