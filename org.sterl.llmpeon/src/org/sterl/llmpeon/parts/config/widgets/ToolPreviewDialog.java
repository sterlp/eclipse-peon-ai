package org.sterl.llmpeon.parts.config.widgets;

import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

/**
 * R-ATC-8: the read-only preview of the tools that would be active for the TYPED allowlist — the
 * names come from {@code resolveActiveTools} (the same composition as the runtime filter, so the
 * preview never drifts from what the agent actually sends). OK only: there is nothing to confirm,
 * just to close. An empty list is shown as-is (zero matching tools is a valid answer).
 */
public class ToolPreviewDialog extends Dialog {

    private final String title;
    private final List<String> names;

    private ToolPreviewDialog(Shell parentShell, String title, List<String> names) {
        super(parentShell);
        this.title = title;
        this.names = names;
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    /** Opens the preview modal (blocks until OK). */
    public static void open(Shell shell, String title, List<String> names) {
        new ToolPreviewDialog(shell, title, names).open();
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(title);
        shell.setSize(480, 360);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        var area = (Composite) super.createDialogArea(parent);
        area.setLayout(new GridLayout(1, false));
        var list = new org.eclipse.swt.widgets.List(area, SWT.BORDER | SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL);
        list.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        for (var name : names) {
            list.add(name);
        }
        if (!names.isEmpty()) {
            list.setSelection(0);
        }
        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
    }
}
