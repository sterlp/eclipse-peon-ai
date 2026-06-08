package org.sterl.llmpeon.parts.widget;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.sterl.llmpeon.shared.model.SimplePromptFile;
import org.sterl.llmpeon.skill.SkillPromptFile;

/**
 * Lightweight popup that lists slash-commands matching the current prefix.
 *
 * <p>The popup is a borderless on-top {@link Shell} anchored to the chat input. It never takes
 * focus; the host widget keeps focus on its text control and forwards arrow / Enter / Escape
 * keys via {@link #moveSelection(int)}, {@link #commitSelection()} and {@link #hide()}.</p>
 */
public class SlashMenuPopup {

    private static final int NAME_COL_WIDTH = 200;
    
    /** Maximum rows shown without scrolling. */
    private static final int MAX_VISIBLE_ROWS = 8;
    /** Minimum popup width in pixels. */
    private static final int MIN_WIDTH = 240;
    /** Maximum popup width in pixels. */
    private static final int MAX_WIDTH = 520;

    private final Composite anchor;
    private final Consumer<SimplePromptFile> onSelect;
    private final Listener anchorShellMoveListener;
    private final Shell anchorShell;

    private Shell popup;
    private Table table;
    private List<SimplePromptFile> filtered = List.of();
    private List<SimplePromptFile> source = List.of();
    private String currentPrefix = "";

    public SlashMenuPopup(Composite anchor, Consumer<SimplePromptFile> onSelect) {
        this.anchor = anchor;
        this.onSelect = onSelect;
        this.anchorShell = anchor.getShell();
        this.anchorShellMoveListener = event -> {
            if (isOpen()) hide();
        };
        anchorShell.addListener(SWT.Move, anchorShellMoveListener);
        anchorShell.addListener(SWT.Resize, anchorShellMoveListener);

        anchor.addDisposeListener(e -> {
            if (!anchorShell.isDisposed()) {
                anchorShell.removeListener(SWT.Move, anchorShellMoveListener);
                anchorShell.removeListener(SWT.Resize, anchorShellMoveListener);
            }
            hide();
        });
    }

    public boolean isOpen() {
        return popup != null && !popup.isDisposed() && popup.isVisible();
    }

    /**
     * Shows or refreshes the popup with the given commands filtered by {@code prefix}.
     * If no commands match, the popup is hidden.
     *
     * @param commands     full list of available commands
     * @param prefix       prefix already typed after the slash, may be empty
     * @param anchorScreen anchor position in display coordinates (typically the caret location)
     */
    public void show(List<SimplePromptFile> commands, String prefix, Point anchorScreen) {
        this.source = commands == null ? List.of() : commands;
        this.currentPrefix = prefix == null ? "" : prefix;
        var matches = filter(this.source, this.currentPrefix);
        if (matches.isEmpty()) {
            hide();
            return;
        }
        this.filtered = matches;
        ensurePopup();
        rebuildItems();
        layoutAt(anchorScreen);
        popup.setVisible(true);
    }

    /** Updates the prefix filter while the popup is visible. */
    public void updatePrefix(String prefix) {
        this.currentPrefix = prefix == null ? "" : prefix;
        if (popup == null || popup.isDisposed()) return;
        var matches = filter(source, currentPrefix);
        if (matches.isEmpty()) {
            hide();
            return;
        }
        this.filtered = matches;
        rebuildItems();
    }

    /** Moves selection by {@code delta} rows; wraps at boundaries. */
    public void moveSelection(int delta) {
        if (!isOpen() || filtered.isEmpty()) return;
        int idx = table.getSelectionIndex();
        if (idx < 0) idx = 0;
        int next = (idx + delta) % filtered.size();
        if (next < 0) next += filtered.size();
        table.setSelection(next);
        table.showSelection();
    }

    /** Triggers the consumer with the currently selected command, then hides the popup. */
    public boolean commitSelection() {
        if (!isOpen() || filtered.isEmpty()) return false;
        int idx = table.getSelectionIndex();
        if (idx < 0) idx = 0;
        var selected = filtered.get(idx);
        hide();
        onSelect.accept(selected);
        return true;
    }

    public void hide() {
        if (popup != null && !popup.isDisposed()) {
            popup.setVisible(false);
            popup.dispose();
        }
        popup = null;
        table = null;
        filtered = List.of();
    }

    private void ensurePopup() {
        if (popup != null && !popup.isDisposed()) return; 

        popup = new Shell(anchorShell, SWT.NO_TRIM | SWT.ON_TOP | SWT.TOOL);
        popup.setLayout(new FillLayout());

        table = new Table(popup, SWT.SINGLE | SWT.FULL_SELECTION | SWT.V_SCROLL);
        table.setHeaderVisible(false);
        table.setLinesVisible(false);

        var colName = new TableColumn(table, SWT.LEFT);
        colName.setWidth(NAME_COL_WIDTH);

        var colDesc = new TableColumn(table, SWT.LEFT);
        colDesc.setWidth(MAX_WIDTH - 164);

        table.addListener(SWT.MouseDoubleClick, e -> commitSelection());
        table.addListener(SWT.DefaultSelection, e -> commitSelection());

        PaintListener border = e -> {
            var size = popup.getSize();
            e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_BORDER));
            e.gc.drawRectangle(0, 0, size.x - 1, size.y - 1);
        };
        popup.addPaintListener(border);
    }

    private void rebuildItems() {
        table.removeAll();
        for (var cmd : filtered) {
            var item = new TableItem(table, SWT.NONE);
            item.setText(0, "/" + cmd.name());
            item.setText(1, cmd instanceof SkillPromptFile ? "SKILL" : "Command (replace system message)");
            // optionally mute the description color:
            item.setForeground(1, table.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
        }
        if (!filtered.isEmpty()) table.setSelection(0);
    }

    private void layoutAt(Point anchorScreen) {
        int rows = Math.min(filtered.size(), MAX_VISIBLE_ROWS);
        int rowHeight = table.getItemHeight();
        if (rowHeight <= 0) rowHeight = 18;
        int height = rowHeight * rows + 6;

        int width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, anchor.getSize().x));
        table.getColumn(0).setWidth(NAME_COL_WIDTH);
        table.getColumn(1).setWidth(width - NAME_COL_WIDTH - 4);

        var bounds = new Rectangle(0, 0, width, height);
        if (anchorScreen != null) {
            bounds.x = anchorScreen.x;
            bounds.y = anchorScreen.y - height; // above the caret
            if (bounds.y < 0) bounds.y = anchorScreen.y + 16; // fall back below
        }
        // Clamp to the monitor that contains the anchor.
        var monitorBounds = anchorShell.getMonitor().getClientArea();
        if (bounds.x + bounds.width > monitorBounds.x + monitorBounds.width) {
            bounds.x = monitorBounds.x + monitorBounds.width - bounds.width;
        }
        if (bounds.x < monitorBounds.x) bounds.x = monitorBounds.x;
        if (bounds.y < monitorBounds.y) bounds.y = monitorBounds.y;
        popup.setBounds(bounds);
    }

    private static List<SimplePromptFile> filter(List<SimplePromptFile> source, String prefix) {
        if (prefix == null || prefix.isEmpty()) return source;
        var lower = prefix.toLowerCase(Locale.ROOT);
        return source.stream()
                .filter(c -> c.name().toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }

    /** Detach the anchor-shell listeners and dispose the popup if alive. */
    public void dispose() {
        if (!anchorShell.isDisposed()) {
            anchorShell.removeListener(SWT.Move, anchorShellMoveListener);
            anchorShell.removeListener(SWT.Resize, anchorShellMoveListener);
        }
        hide();
    }

    Control getPopupShellForTests() { return popup; }
}
