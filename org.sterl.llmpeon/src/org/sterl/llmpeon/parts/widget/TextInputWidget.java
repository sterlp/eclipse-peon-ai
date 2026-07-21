package org.sterl.llmpeon.parts.widget;

import java.util.LinkedList;
import java.util.List;

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
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

/**
 * Reusable auto-growing StyledText widget. The text area grows from {@code minRows} up to
 * {@code maxRows}, then scrolls. The minimum is applied immediately at construction (so the field
 * starts at {@code minRows} even while empty) and re-applied on every edit. Height changes are
 * propagated by calling the {@code onReflow} callback so the parent controls layout propagation.
 */
public class TextInputWidget extends Composite {

    private final StyledText styledText;
    private final int minRows;
    private final int maxRows;
    private final Runnable onReflow;

    private static final int MAX_STACK_SIZE = 25;
    private List<UndoRedoStack> undoStack;
    private List<UndoRedoStack> redoStack;

    private final Menu popupMenu;
    private boolean fullSelection = false;

    public TextInputWidget(Composite parent, int style, int minRows, int maxRows, Runnable onReflow) {
        super(parent, style);
        this.minRows = minRows;
        this.maxRows = maxRows;
        this.onReflow = onReflow;

        undoStack = new LinkedList<>();
        redoStack = new LinkedList<>();

        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        setLayout(layout);

        styledText = new StyledText(this, SWT.MULTI | SWT.WRAP);
        GridData textData = new GridData(SWT.FILL, SWT.FILL, true, true);
        // Apply the minimum height up front so the field starts at minRows while still empty —
        // refreshHeight() only runs on edits (and bails while width == 0), so it can't do it.
        textData.heightHint = styledText.getLineHeight() * minRows;
        styledText.setLayoutData(textData);
        styledText.addModifyListener(e -> refreshHeight());
        FileDropSupport.install(this, styledText);
        FileDropSupport.install(styledText, styledText);

        popupMenu = new Menu(parent.getShell(), SWT.POP_UP);
        addUndoRedoSupport(popupMenu);
    }

    private void refreshHeight() {
        if (styledText.isDisposed()) return;
        int width = styledText.getSize().x;
        if (width <= 0) return;
        Point size = styledText.computeSize(width, SWT.DEFAULT);
        GridData gd = (GridData) styledText.getLayoutData();
        int lineH = styledText.getLineHeight();
        int minHeight = lineH * minRows;
        int maxHeight = lineH * maxRows;
        int newHint = Math.max(minHeight, Math.min(maxHeight, size.y));
        if (gd.heightHint != newHint) {
            gd.heightHint = newHint;
            onReflow.run();
        }
    }

    // Add support functions for Undo/Redo with Popup Menu on text widget
    // https://fossies.org/linux/apache-hop/ui/src/main/java/org/apache/hop/ui/core/widget/StyledTextVar.java
    protected void addUndoRedoSupport(Menu popupMenu) {
        final MenuItem undoItem = new MenuItem(popupMenu, SWT.PUSH);
        undoItem.setText("Undo");
        undoItem.addListener(SWT.Selection, event -> undo());

        final MenuItem redoItem = new MenuItem(popupMenu, SWT.PUSH);
        redoItem.setText("Redo");
        redoItem.addListener(SWT.Selection, event -> redo());

        new MenuItem(popupMenu, SWT.SEPARATOR);

        final MenuItem cutItem = new MenuItem(popupMenu, SWT.PUSH);
        cutItem.setText("Cut");
        cutItem.addListener(SWT.Selection, event -> styledText.cut());

        final MenuItem copyItem = new MenuItem(popupMenu, SWT.PUSH);
        copyItem.setText("Copy");
        copyItem.addListener(SWT.Selection, event -> styledText.copy());

        final MenuItem pasteItem = new MenuItem(popupMenu, SWT.PUSH);
        pasteItem.setText("Paste");
        pasteItem.addListener(SWT.Selection, event -> styledText.paste());

        new MenuItem(popupMenu, SWT.SEPARATOR);

        final MenuItem selectAllItem = new MenuItem(popupMenu, SWT.PUSH);
        selectAllItem.setText("Select All");
        selectAllItem.addListener(SWT.Selection, event -> styledText.selectAll());

        styledText.setMenu(popupMenu);

        styledText.addListener(
            SWT.Selection,
            event -> {
                if (styledText.getSelectionCount() == styledText.getCharCount()) {
                    fullSelection = true;
                }
            });

		styledText.addListener(
            SWT.KeyDown,
            event -> {
                if (event.keyCode == 'z'
                    && (event.stateMask & SWT.MOD1) != 0
                    && (event.stateMask & SWT.MOD2) != 0) {
                    redo();
                } else if (event.keyCode == 'z'
                    && (event.stateMask & SWT.MOD1) != 0) {
                    undo();
                } else if (event.keyCode == 'a' && (event.stateMask & SWT.MOD1) != 0) {
                    styledText.selectAll();
                }
            });

        styledText.addExtendedModifyListener(
            event -> {
                int eventLength = event.length;
                int eventStartPostition = event.start;

                String newText = getText();
                String repText = event.replacedText;
                String oldText = "";
                int eventType = -1;

                if ((event.length != newText.length()) || (fullSelection)) {
                    if (repText != null && !repText.isEmpty()) {
                        oldText =
                              newText.substring(0, event.start)
                              + repText
                              + newText.substring(event.start + event.length);
                        eventType = UndoRedoStack.DELETE;
                        eventLength = repText.length();
                    } else {
                        oldText =
                              newText.substring(0, event.start) + newText.substring(event.start + event.length);
                        eventType = UndoRedoStack.INSERT;
                    }

                    if ((oldText != null && !oldText.isEmpty()) || (eventStartPostition == event.length)) {
                        UndoRedoStack urs =
                            new UndoRedoStack(eventStartPostition, newText, oldText, eventLength, eventType);

                        // Stack is full
                        if (undoStack.size() == MAX_STACK_SIZE) {
                            undoStack.remove(undoStack.size() - 1);
                        }
                        undoStack.add(0, urs);
                    }
                }
                fullSelection = false;
            });
    }

    protected void undo() {
        if (!undoStack.isEmpty()) {
            UndoRedoStack undo = undoStack.remove(0);
            if (redoStack.size() == MAX_STACK_SIZE) {
                redoStack.remove(redoStack.size() - 1);
            }
            UndoRedoStack redo =
                new UndoRedoStack(
                    undo.cursorPosition(),
                    undo.replacedText(),
                    getText(),
                    undo.eventLength(),
                    undo.type());
            fullSelection = false;
            setText(undo.replacedText());
            if (undo.type() == UndoRedoStack.INSERT) {
                styledText.setCaretOffset(undo.cursorPosition());
            } else if (undo.type() == UndoRedoStack.DELETE) {
                styledText.setCaretOffset(undo.cursorPosition() + undo.eventLength());
                styledText.setSelection(undo.cursorPosition(), undo.cursorPosition() + undo.eventLength());
                if (styledText.getSelectionCount() == styledText.getCharCount()) {
                    fullSelection = true;
                }
            }
            redoStack.add(0, redo);
        }
    }

    protected void redo() {
        if (!redoStack.isEmpty()) {
            UndoRedoStack redo = redoStack.remove(0);
            if (undoStack.size() == MAX_STACK_SIZE) {
                undoStack.remove(undoStack.size() - 1);
            }
            UndoRedoStack undo =
                new UndoRedoStack(
                    redo.cursorPosition(),
                    redo.replacedText(),
                    getText(),
                    redo.eventLength(),
                    redo.type());
            fullSelection = false;
            setText(redo.replacedText());
            if (redo.type() == UndoRedoStack.INSERT) {
                styledText.setCaretOffset(redo.cursorPosition());
            } else if (redo.type() == UndoRedoStack.DELETE) {
                styledText.setCaretOffset(redo.cursorPosition() + redo.eventLength());
                styledText.setSelection(redo.cursorPosition(), redo.cursorPosition() + redo.eventLength());
                if (styledText.getSelectionCount() == styledText.getCharCount()) {
                    fullSelection = true;
                }
            }
            undoStack.add(0, undo);
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
    
    public void setEditable(boolean editable) {
        styledText.setEditable(editable);
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
    
    public void setCaretOffset(int offset) {
        if (styledText.isDisposed()) return;
        int clamped = Math.max(0, Math.min(offset, styledText.getCharCount()));
        styledText.setCaretOffset(clamped);
    }

    public static record UndoRedoStack (int cursorPosition, String newText, String replacedText, int eventLength, int type) {
        public static final int DELETE = 0;
        public static final int INSERT = 1;

    }
}
