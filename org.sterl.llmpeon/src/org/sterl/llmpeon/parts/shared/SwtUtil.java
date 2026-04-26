package org.sterl.llmpeon.parts.shared;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

/**
 * SWT helpers. Techniques ported from Copilot's {@code UiUtils.createIconButton}
 * so buttons render flat (icon only) on both macOS and Windows without fighting
 * the native StyledText background.
 */
public class SwtUtil {

    /**
     * Creates an icon-only push button whose native OS frame is suppressed by a
     * custom PaintListener. The button fills its rectangle with its own
     * background color (inherited from the parent composite), then draws the
     * icon centered. A MouseTrackListener provides a subtle hover highlight.
     *
     * <p>Callers can still use {@link Button#setBackground(Color)} to indicate
     * state (e.g. red while recording) — the PaintListener picks up the new
     * background automatically.
     */
    public static Button createIconButton(Composite parent, Image icon, String tooltip) {
        final Button btn = new Button(parent, SWT.PUSH | SWT.NATIVE);
        if (icon != null) btn.setImage(icon);
        if (tooltip != null) btn.setToolTipText(tooltip);

        btn.addPaintListener(e -> {
            Rectangle b = btn.getBounds();
            e.gc.fillRectangle(0, 0, b.width, b.height);
            Image img = btn.getImage();
            if (img != null) {
                Rectangle ib = img.getBounds();
                int x = (b.width  - ib.width)  / 2;
                int y = (b.height - ib.height) / 2;
                int oldAlpha = e.gc.getAlpha();
                if (!btn.isEnabled()) e.gc.setAlpha(oldAlpha / 2);
                e.gc.drawImage(img, x, y);
                e.gc.setAlpha(oldAlpha);
            }
        });

        final Color hoverBg = new Color(btn.getDisplay(), 232, 232, 232);
        btn.addDisposeListener(e -> hoverBg.dispose());

        btn.addMouseTrackListener(new MouseTrackAdapter() {
            private Color original;
            @Override public void mouseEnter(MouseEvent e) {
                original = btn.getBackground();
                btn.setBackground(hoverBg);
            }
            @Override public void mouseExit(MouseEvent e) {
                btn.setBackground(original);
            }
        });

        return btn;
    }
}
