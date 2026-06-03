package org.sterl.llmpeon.parts.widget;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.VerifyKeyListener;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

/**
 * Reusable auto-growing StyledText widget. The text area grows from a minimum of
 * 2 rows up to {@code maxRows}, then scrolls. Height changes are propagated by
 * calling the {@code onReflow} callback so the parent controls layout propagation.
 */
public class TextInputWidget extends Composite {

    private final StyledText styledText;
    private final int maxRows;
    private final Runnable onReflow;

    public TextInputWidget(Composite parent, int style, int maxRows, Runnable onReflow) {
        super(parent, style);
        this.maxRows = maxRows;
        this.onReflow = onReflow;

        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        setLayout(layout);

        styledText = new StyledText(this, SWT.MULTI | SWT.WRAP);
        styledText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        styledText.addModifyListener(e -> refreshHeight());
    }

    private void refreshHeight() {
        if (styledText.isDisposed()) return;
        int width = styledText.getSize().x;
        if (width <= 0) return;
        Point size = styledText.computeSize(width, SWT.DEFAULT);
        GridData gd = (GridData) styledText.getLayoutData();
        int lineH = styledText.getLineHeight();
        int minHeight = lineH * 2;
        int maxHeight = lineH * maxRows;
        int newHint = Math.max(minHeight, Math.min(maxHeight, size.y));
        if (gd.heightHint != newHint) {
            gd.heightHint = newHint;
            onReflow.run();
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public String getText() {
        return styledText.getText();
    }

    public void setText(String text) {
        styledText.setText(text != null ? text : "");
    }

    public void clearText() {
        styledText.setText("");
    }

    @Override
    public boolean setFocus() {
        if (styledText.isDisposed()) return false;
        return styledText.setFocus();
    }

    public void addModifyListener(ModifyListener listener) {
        styledText.addModifyListener(listener);
    }

    public void removeModifyListener(ModifyListener listener) {
        styledText.removeModifyListener(listener);
    }

    public void addKeyListener(KeyListener listener) {
        styledText.addKeyListener(listener);
    }

    /**
     * Adds a verify-key listener that runs BEFORE the StyledText consumes the key. Setting
     * {@code event.doit = false} suppresses the default behavior (e.g. arrow navigation). This is
     * the only reliable hook for stealing arrow / Enter keys to drive an external popup.
     */
    public void addVerifyKeyListener(VerifyKeyListener listener) {
        styledText.addVerifyKeyListener(listener);
    }

    /** Sets the background on the underlying StyledText (safe — not a Composite). */
    public void setTextBackground(Color color) {
        styledText.setBackground(color);
    }

    /** Display coordinates of the current caret, suitable for anchoring an external popup. */
    public Point getCaretDisplayLocation() {
        if (styledText.isDisposed()) return null;
        var local = styledText.getLocationAtOffset(styledText.getCaretOffset());
        return styledText.toDisplay(local.x, local.y);
    }
}
