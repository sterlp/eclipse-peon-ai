package org.sterl.llmpeon.parts.widget;

import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.sterl.llmpeon.parts.shared.ImageUtil;
import org.sterl.llmpeon.parts.shared.SwtUtil;

/**
 * User input area: file chips bar (hidden until files attached), auto-resizing
 * StyledText (min 2 / max 7 rows), mic button, and Send/Stop button.
 *
 * <p>No backgrounds are set anywhere inside this widget — the StyledText keeps
 * its native OS white, and the paint-based Buttons from {@link SwtUtil#createIconButton}
 * inherit the same background so the whole area reads as one flat field.
 */
public class UserInputWidget extends Composite {

    private static final int MAX_ROWS = 7;

    private final StyledText styledText;
    private final Composite rightColumn;
    private final Button sendButton;
    private Button micButton;   // null until voice is configured

    private final Image micImage;
    private final Image sendImage;  // shared registry — must NOT be disposed
    private final Image stopImage;
    private boolean working = false;
    private final Runnable onMicClick;

    private final Color colorRecording;

    public UserInputWidget(Composite parent, int style, Runnable onSend, Runnable onStop, Runnable onMicClick) {
        super(parent, style);
        this.onMicClick = onMicClick;

        colorRecording = new Color(200, 0, 0);
        addDisposeListener(e -> colorRecording.dispose());

        micImage  = ImageUtil.loadImage(this, ImageUtil.MICROPHONE);
        sendImage = DebugUITools.getImage(IDebugUIConstants.IMG_ACT_RUN);
        stopImage = ImageUtil.loadImage(this, ImageUtil.STOP);

        GridLayout outerLayout = new GridLayout(1, false);
        outerLayout.marginWidth = 0;
        outerLayout.marginHeight = 0;
        outerLayout.verticalSpacing = 2;
        setLayout(outerLayout);

        // --- Text row: StyledText | right icon column ---
        Composite textRow = new Composite(this, SWT.NONE);
        GridLayout textRowLayout = new GridLayout(2, false);
        textRowLayout.marginWidth = 2;
        textRowLayout.marginHeight = 2;
        textRowLayout.horizontalSpacing = 0;
        textRow.setLayout(textRowLayout);
        textRow.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        styledText = new StyledText(textRow, SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        styledText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        styledText.addModifyListener(e -> refreshHeight());

        // Ctrl/Cmd+Enter sends; plain Enter inserts newline
        styledText.addKeyListener(KeyListener.keyPressedAdapter(e -> {
            if (e.keyCode == SWT.CR || e.keyCode == SWT.LF) {
                boolean send = (e.stateMask & SWT.CTRL) != 0 || (e.stateMask & SWT.COMMAND) != 0;
                if (send) {
                    e.doit = false;
                    onSend.run();
                }
            }
        }));

        // Right icon column — mic on top (optional), send/stop at bottom
        rightColumn = new Composite(textRow, SWT.NONE);
        GridLayout rcLayout = new GridLayout(1, false);
        rcLayout.marginWidth = 0;
        rcLayout.marginHeight = 0;
        rcLayout.verticalSpacing = 0;
        rightColumn.setLayout(rcLayout);
        rightColumn.setLayoutData(new GridData(SWT.CENTER, SWT.FILL, false, true));

        sendButton = SwtUtil.createIconButton(rightColumn, sendImage, "Send (Ctrl+Enter)");
        sendButton.setLayoutData(new GridData(SWT.CENTER, SWT.BOTTOM, false, true));
        sendButton.addListener(SWT.Selection, e -> {
            if (working) onStop.run();
            else onSend.run();
        });
    }

    private void requestReflow() {
        layout(true, true);
        Composite p = getParent();
        if (p == null) return;
        p.layout(new Control[]{ this });
        Composite pp = p.getParent();
        if (pp != null) pp.layout(new Control[]{ p });
    }

    private void refreshHeight() {
        int width = styledText.getSize().x;
        if (width <= 0) return;
        Point size = styledText.computeSize(width, SWT.DEFAULT);
        GridData gd = (GridData) styledText.getLayoutData();
        int lineH = styledText.getLineHeight();
        int minHeight = lineH * 2;
        int maxHeight = lineH * MAX_ROWS;
        int newHint = Math.max(minHeight, Math.min(maxHeight, size.y));
        if (gd.heightHint != newHint) {
            gd.heightHint = newHint;
            requestReflow();
        }
    }


    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public String getText() {
        return styledText.getText();
    }

    public void clearText() {
        styledText.setText("");
        requestReflow();
    }

    public void setText(String text) {
        styledText.setText(text != null ? text : "");
    }

    /** Show or hide the mic button. Created on first show, disposed on hide so the slot is truly empty. */
    public void setVoiceInputVisible(boolean visible) {
        if (visible && (micButton == null || micButton.isDisposed())) {
            micButton = SwtUtil.createIconButton(rightColumn,
                    micImage,
                    "Click to start recording — click again to stop and transcribe");
            // Place mic before sendButton and sit at the top
            micButton.moveAbove(sendButton);
            micButton.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, false));
            micButton.addListener(SWT.Selection, e -> onMicClick.run());
            rightColumn.layout(true, true);
            requestReflow();
        } else if (!visible && micButton != null && !micButton.isDisposed()) {
            micButton.dispose();
            micButton = null;
            rightColumn.layout(true, true);
            requestReflow();
        }
    }

    public void setRecording(boolean recording) {
        if (micButton != null && !micButton.isDisposed()) {
            micButton.setBackground(recording ? colorRecording : null);
            micButton.setToolTipText(recording
                ? "Recording... click to stop and transcribe"
                : "Click to start recording — click again to stop and transcribe");
            micButton.redraw();
        }
    }

    /** Switch the Send/Stop button between idle and working state. */
    public void setWorking(boolean working) {
        this.working = working;
        if (working) {
            sendButton.setImage(stopImage);
            sendButton.setToolTipText("Cancel current request");
        } else {
            sendButton.setImage(sendImage);
            sendButton.setToolTipText("Send (Ctrl+Enter)");
        }
        sendButton.redraw();
    }

    @Override
    public boolean setFocus() {
        return styledText.setFocus();
    }
}
