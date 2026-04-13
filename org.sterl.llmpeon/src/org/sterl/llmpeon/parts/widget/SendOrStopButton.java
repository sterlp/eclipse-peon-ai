package org.sterl.llmpeon.parts.widget;

import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.sterl.llmpeon.parts.shared.ImageUtil;

/**
 * A single push button that acts as Send when idle and Stop when a request is
 * in flight. Call {@link #setWorking(boolean)} to toggle between the two states.
 */
public class SendOrStopButton extends Composite {

    private final Button btn;
    private final Image sendImage;  // shared registry image — must NOT be disposed
    private final Image stopImage;  // owned — disposed with this composite
    private boolean working = false;

    public SendOrStopButton(Composite parent, int style, Runnable onSend, Runnable onStop) {
        super(parent, style);
        setLayout(new FillLayout());

        btn = new Button(this, SWT.PUSH);
        sendImage = DebugUITools.getImage(IDebugUIConstants.IMG_ACT_RUN);
        stopImage = ImageUtil.loadImage(this, ImageUtil.STOP);

        applySendState();

        btn.addListener(SWT.Selection, e -> {
            if (working) onStop.run();
            else onSend.run();
        });
    }

    /** Switch between send (idle) and stop (working) appearance and behavior. */
    public void setWorking(boolean working) {
        this.working = working;
        if (working) applyStopState();
        else applySendState();
    }

    private void applySendState() {
        btn.setImage(sendImage);
        btn.setToolTipText("Send (Ctrl+Enter)");
    }

    private void applyStopState() {
        btn.setImage(stopImage);
        btn.setToolTipText("Cancel current request");
    }
}
