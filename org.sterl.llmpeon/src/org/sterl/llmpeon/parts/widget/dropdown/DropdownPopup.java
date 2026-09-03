// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package org.sterl.llmpeon.parts.widget.dropdown;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/**
 * Borderless popup shell listing {@link DropdownItem}s below an anchor control.
 *
 * <p>Adapted from the MIT-licensed copilot-for-eclipse {@code DropdownPopup}. Peon v1 keeps the
 * core — rows, hover highlight, click-select, Esc/Deactivate close, position under anchor — and
 * defers keyboard navigation, monitor flip/clamp, max-height scrolling and scrollbar
 * compensation to a later increment.</p>
 */
public class DropdownPopup {

    private static final int POPUP_MARGIN = 2;
    private static final int ITEM_H_PADDING = 8;
    private static final int ITEM_V_PADDING = 4;
    private static final int ICON_TEXT_GAP = 6;
    private static final int BORDER_ARC = 8;
    private static final int ICON_PLACEHOLDER = 16;

    private final Shell parentShell;
    private final Control anchorControl;
    private final DropdownTheme.Palette palette;

    private Shell shell;
    private Consumer<String> selectionListener;
    private String selectedItemId;

    private record ItemEntry(DropdownItem item, Composite composite) {}
    private final List<ItemEntry> items = new ArrayList<>();
    private int hoverIndex = -1;

    public DropdownPopup(Shell parentShell, Control anchorControl) {
        this.parentShell = parentShell;
        this.anchorControl = anchorControl;
        this.palette = DropdownTheme.current(parentShell.getDisplay());
    }

    public void setSelectionListener(Consumer<String> listener) {
        this.selectionListener = listener;
    }

    /**
     * Opens the popup at the given screen location with the provided items.
     *
     * @param location     screen position for the top-left of the popup
     * @param itemList     items to display
     * @param selectedItemId id of the currently selected item, or {@code null}
     */
    public void open(Point location, List<DropdownItem> itemList, String selectedItemId) {
        if (shell != null && !shell.isDisposed()) {
            close();
        }
        this.selectedItemId = selectedItemId;

        shell = new Shell(parentShell, SWT.NO_TRIM | SWT.ON_TOP);
        shell.setBackground(palette.popupBg());

        GridLayout shellLayout = new GridLayout(1, false);
        shellLayout.marginWidth = POPUP_MARGIN;
        shellLayout.marginHeight = POPUP_MARGIN;
        shellLayout.verticalSpacing = 0;
        shell.setLayout(shellLayout);

        var scrolled = new ScrolledComposite(shell, SWT.V_SCROLL);
        scrolled.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        scrolled.setExpandHorizontal(true);
        scrolled.setExpandVertical(true);
        scrolled.setBackground(palette.popupBg());

        var container = new Composite(scrolled, SWT.NONE);
        container.setBackground(palette.popupBg());
        GridLayout containerLayout = new GridLayout(1, false);
        containerLayout.marginWidth = 0;
        containerLayout.marginHeight = 0;
        containerLayout.verticalSpacing = 0;
        container.setLayout(containerLayout);
        scrolled.setContent(container);

        items.clear();
        hoverIndex = -1;
        for (var item : itemList) {
            addItem(container, item);
        }

        Point contentSize = container.computeSize(SWT.DEFAULT, SWT.DEFAULT);
        container.setSize(contentSize);
        scrolled.setMinSize(contentSize);

        // Highlight the currently selected row so the open popup shows the active choice.
        for (int i = 0; i < items.size(); i++) {
            if (selectedItemId != null && selectedItemId.equals(items.get(i).item().id())) {
                hoverIndex = i;
                paintRow(i, true);
                break;
            }
        }

        shell.addPaintListener(e -> {
            Rectangle bounds = shell.getClientArea();
            e.gc.setForeground(palette.border());
            e.gc.setLineWidth(1);
            e.gc.drawRoundRectangle(0, 0, bounds.width - 1, bounds.height - 1, BORDER_ARC, BORDER_ARC);
        });

        shell.addListener(SWT.Deactivate, e -> {
            if (anchorControl != null && !anchorControl.isDisposed() && isCursorInsideControl(anchorControl)) {
                return;
            }
            close();
        });

        shell.addListener(SWT.Traverse, e -> {
            if (e.detail == SWT.TRAVERSE_ESCAPE) {
                close();
                e.doit = false;
            }
        });

        shell.pack();
        shell.setLocation(location);
        shell.setVisible(true);
        shell.setFocus();
    }

    private void addItem(Composite parent, DropdownItem item) {
        Composite itemComp = new Composite(parent, SWT.NONE);
        itemComp.setBackground(palette.popupBg());
        itemComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        GridLayout itemLayout = new GridLayout(2, false);
        itemLayout.marginWidth = ITEM_H_PADDING;
        itemLayout.marginHeight = ITEM_V_PADDING;
        itemLayout.horizontalSpacing = ICON_TEXT_GAP;
        itemComp.setLayout(itemLayout);

        int itemIndex = items.size();

        List<Control> controls = new ArrayList<>();
        controls.add(itemComp);

        // Leading icon column (fixed width so labels align across items).
        Label leading = new Label(itemComp, SWT.NONE);
        leading.setBackground(palette.popupBg());
        GridData leadingGd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        if (item.icon() != null && !item.icon().isDisposed()) {
            leading.setImage(item.icon());
        } else {
            leadingGd.widthHint = ICON_PLACEHOLDER;
        }
        leading.setLayoutData(leadingGd);
        controls.add(leading);

        Label nameLabel = new Label(itemComp, SWT.NONE);
        nameLabel.setText(item.label());
        nameLabel.setForeground(palette.text());
        nameLabel.setBackground(palette.popupBg());
        nameLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        controls.add(nameLabel);

        items.add(new ItemEntry(item, itemComp));

        MouseTrackAdapter hover = new MouseTrackAdapter() {
            @Override
            public void mouseEnter(MouseEvent e) {
                setHover(itemIndex);
            }

            @Override
            public void mouseExit(MouseEvent e) {
                if (isCursorInsideControl(itemComp)) {
                    return;
                }
                if (itemIndex == hoverIndex) {
                    setHover(-1);
                }
            }
        };
        MouseAdapter click = new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                close();
                if (selectionListener != null && item.id() != null) {
                    selectionListener.accept(item.id());
                }
            }
        };
        for (Control c : controls) {
            c.addMouseTrackListener(hover);
            c.addMouseListener(click);
        }
    }

    private void setHover(int index) {
        if (index == hoverIndex) {
            return;
        }
        if (hoverIndex >= 0) {
            paintRow(hoverIndex, false);
        }
        hoverIndex = index;
        if (hoverIndex >= 0) {
            paintRow(hoverIndex, true);
        }
    }

    private void paintRow(int index, boolean hovered) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        ItemEntry entry = items.get(index);
        if (entry.composite().isDisposed()) {
            return;
        }
        Color bg = hovered ? palette.itemFocusBg() : palette.popupBg();
        entry.composite().setBackground(bg);
        for (Control child : entry.composite().getChildren()) {
            child.setBackground(bg);
        }
        entry.composite().redraw();
    }

    public void close() {
        if (shell != null && !shell.isDisposed()) {
            shell.dispose();
        }
        shell = null;
        items.clear();
        hoverIndex = -1;
    }

    public boolean isOpen() {
        return shell != null && !shell.isDisposed() && shell.isVisible();
    }

    private static boolean isCursorInsideControl(Control control) {
        Point cursor = control.getDisplay().getCursorLocation();
        Point loc = control.toDisplay(0, 0);
        Point size = control.getSize();
        return cursor.x >= loc.x && cursor.x < loc.x + size.x
                && cursor.y >= loc.y && cursor.y < loc.y + size.y;
    }
}
