// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package org.sterl.llmpeon.parts.widget.dropdown;

import java.util.List;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;

/**
 * Custom-painted dropdown button showing the selected {@link DropdownItem} label and a
 * dropdown arrow. Clicking (or Space / Enter / &darr;) opens a {@link DropdownPopup} below
 * the button.
 *
 * <p>Adapted from the MIT-licensed copilot-for-eclipse {@code DropdownButton}. Peon v1 keeps
 * the core — painted background (hover highlight), label + optional icon, GC-drawn arrow,
 * popup toggle, selection callback, natural width, dispose safety — and defers the keyboard
 * focus-frame polish to a later increment.</p>
 */
public class DropdownButton extends Composite {

    private static final int H_PADDING = 6;
    private static final int V_PADDING = 6;
    private static final int ICON_TEXT_GAP = 4;
    private static final int ARROW_AREA_WIDTH = 16;
    private static final int ARROW_WIDTH = 8;
    private static final int ARROW_HEIGHT = 5;

    private final DropdownPopup popup;
    private List<DropdownItem> items = List.of();
    private String selectedItemId;
    private boolean mouseHover;
    private boolean showFocusBorder;

    public DropdownButton(Composite parent, int style) {
        super(parent, style | SWT.NONE);
        popup = new DropdownPopup(getShell(), this);

        addPaintListener(e -> paintControl(e.gc));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                togglePopup();
            }
        });
        addMouseTrackListener(new MouseTrackAdapter() {
            @Override
            public void mouseEnter(MouseEvent e) {
                mouseHover = true;
                redraw();
            }

            @Override
            public void mouseExit(MouseEvent e) {
                mouseHover = false;
                redraw();
            }
        });
        addListener(SWT.KeyDown, event -> {
            if (event.keyCode == SWT.CR || event.keyCode == ' ' || event.keyCode == SWT.ARROW_DOWN) {
                togglePopup();
                event.doit = false;
            }
        });

        setCursor(getDisplay().getSystemCursor(SWT.CURSOR_HAND));
        addFocusBorder();
        addListener(SWT.Dispose, e -> popup.close());
    }

    /** Sets the items shown in the popup and updates the natural width. */
    public void setItems(List<DropdownItem> items) {
        this.items = items == null ? List.of() : items;
        updateWidthHint();
        redraw();
    }

    /** Sets the id of the selected item; the button shows that item's label. */
    public void setSelectedItemId(String selectedItemId) {
        this.selectedItemId = selectedItemId;
        updateWidthHint();
        redraw();
    }

    /** Returns the selected item id, or {@code null}. */
    public String getSelectedItemId() {
        return selectedItemId;
    }

    /** Sets the listener called with the selected item id. */
    public void setSelectionListener(Consumer<String> listener) {
        popup.setSelectionListener(listener);
    }

    /** Sets the accessible name used by screen readers. */
    public void setAccessibilityName(String name) {
        getAccessible().addAccessibleListener(new AccessibleAdapter() {
            @Override
            public void getName(AccessibleEvent e) {
                e.result = name;
            }
        });
    }

    private void togglePopup() {
        if (isDisposed() || !isEnabled()) {
            return;
        }
        if (popup.isOpen()) {
            popup.close();
        } else {
            openPopup();
        }
    }

    private void openPopup() {
        if (isDisposed() || items.isEmpty()) {
            return;
        }
        Rectangle bounds = getBounds();
        Point screenPos = toDisplay(0, bounds.height);
        popup.open(screenPos, items, selectedItemId);
    }

    private void paintControl(GC gc) {
        Rectangle bounds = getClientArea();
        var palette = DropdownTheme.current(getDisplay());
        DropdownItem selected = findItemById(selectedItemId);
        Image selectedIcon = nonDisposedIcon(selected);

        Color bg = mouseHover ? palette.buttonFocusBg() : getBackground();
        gc.setBackground(bg);
        gc.fillRectangle(bounds);

        gc.setForeground(getForeground());
        String text = selected != null ? selected.label() : "";
        Point textExtent = gc.textExtent(text);
        int contentHeight = Math.max(textExtent.y, ARROW_HEIGHT);
        if (selectedIcon != null) {
            contentHeight = Math.max(contentHeight, selectedIcon.getBounds().height);
        }
        int contentTop = Math.max(0, (bounds.height - contentHeight) / 2);

        int textX = H_PADDING;
        if (selectedIcon != null) {
            Rectangle iconBounds = selectedIcon.getBounds();
            int iconY = contentTop + (contentHeight - iconBounds.height) / 2;
            gc.drawImage(selectedIcon, textX, iconY);
            textX += iconBounds.width + ICON_TEXT_GAP;
        }
        int textY = contentTop + (contentHeight - textExtent.y) / 2;
        gc.drawText(text, textX, textY, true);

        int arrowX = bounds.width - H_PADDING - ARROW_WIDTH;
        int arrowY = contentTop + (contentHeight - ARROW_HEIGHT) / 2;
        gc.fillPolygon(new int[] { arrowX, arrowY, arrowX + ARROW_WIDTH, arrowY,
                arrowX + ARROW_WIDTH / 2, arrowY + ARROW_HEIGHT });

        if (showFocusBorder) {
            gc.setForeground(palette.focusBorder());
            gc.setLineWidth(1);
            gc.drawRectangle(0, 0, bounds.width - 1, bounds.height - 1);
        }
    }

    private DropdownItem findItemById(String id) {
        if (id == null) {
            return null;
        }
        for (DropdownItem item : items) {
            if (id.equals(item.id())) {
                return item;
            }
        }
        return null;
    }

    private static Image nonDisposedIcon(DropdownItem item) {
        if (item == null) {
            return null;
        }
        Image icon = item.icon();
        return icon != null && !icon.isDisposed() ? icon : null;
    }

    @Override
    public Point computeSize(int widthHint, int heightHint, boolean changed) {
        GC gc = new GC(this);
        try {
            DropdownItem selected = findItemById(selectedItemId);
            String text = selected != null ? selected.label() : "";
            Point textExtent = gc.textExtent(text.isEmpty() ? "M" : text);
            Image selectedIcon = nonDisposedIcon(selected);
            int iconWidth = selectedIcon != null ? selectedIcon.getBounds().width + ICON_TEXT_GAP : 0;
            int width = H_PADDING + iconWidth + textExtent.x + ARROW_AREA_WIDTH;
            int height = Math.max(textExtent.y, ARROW_HEIGHT) + 2 * V_PADDING;
            if (widthHint != SWT.DEFAULT) {
                width = Math.max(width, widthHint);
            }
            if (heightHint != SWT.DEFAULT) {
                height = Math.max(height, heightHint);
            }
            return new Point(width, height);
        } finally {
            gc.dispose();
        }
    }

    private void updateWidthHint() {
        if (isDisposed()) {
            return;
        }
        if (getLayoutData() instanceof GridData gridData) {
            Point preferred = computeSize(SWT.DEFAULT, SWT.DEFAULT, true);
            if (gridData.widthHint != preferred.x) {
                gridData.widthHint = preferred.x;
            }
        }
        requestLayout();
    }

    /** Keyboard focus frame, shown only when focus is gained via Tab (not mouse). */
    private void addFocusBorder() {
        boolean[] mousePressed = { false };
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                mousePressed[0] = true;
            }
        });
        addTraverseListener(e -> {
            if (e.detail == SWT.TRAVERSE_TAB_NEXT || e.detail == SWT.TRAVERSE_TAB_PREVIOUS) {
                e.doit = true;
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                // Defer the decision so a preceding mouseDown (click focus) is processed first.
                getDisplay().asyncExec(() -> {
                    if (isDisposed() || !isFocusControl()) {
                        return;
                    }
                    showFocusBorder = !mousePressed[0];
                    mousePressed[0] = false;
                    redraw();
                });
            }

            @Override
            public void focusLost(FocusEvent e) {
                showFocusBorder = false;
                mousePressed[0] = false;
                redraw();
            }
        });
    }
}
