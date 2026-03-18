package org.sterl.llmpeon.parts.widget;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

/**
 * Simple input area widget. Fires onSend when Ctrl+Enter (or Cmd+Enter) is pressed.
 */
public class ChatWidget extends Composite {

    private final Text inputArea;

    public ChatWidget(Composite parent, int style, Runnable onSend) {
        super(parent, style);
        setLayout(new GridLayout(1, false));

        inputArea = new Text(this, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        gd.heightHint = 80;
        inputArea.setLayoutData(gd);

        inputArea.addTraverseListener(e -> {
            if (e.detail == SWT.TRAVERSE_RETURN) {
                boolean send = (e.stateMask & SWT.CTRL) != 0 || (e.stateMask & SWT.COMMAND) != 0;
                if (send) {
                    e.doit = false;
                    onSend.run();
                }
            }
        });
    }

    public String getText() {
        return inputArea.getText();
    }

    public void clearText() {
        inputArea.setText("");
    }

    public void setText(String text) {
        inputArea.setText(text);
    }

    @Override
    public boolean setFocus() {
        return inputArea.setFocus();
    }
}
