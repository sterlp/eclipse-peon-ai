package org.sterl.llmpeon.parts.widget;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.util.LocalSelectionTransfer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.VerifyKeyListener;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.part.ResourceTransfer;
import org.sterl.llmpeon.parts.shared.JdtUtil;

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
        enableFilePathDrop(this);
        enableFilePathDrop(styledText);
    }

    private void enableFilePathDrop(Control control) {
        var dropTarget = new DropTarget(control, DND.DROP_COPY);
        control.addDisposeListener(e -> {
            if (!dropTarget.isDisposed()) dropTarget.dispose();
        });
        dropTarget.setTransfer(new Transfer[]{
                ResourceTransfer.getInstance(),
                LocalSelectionTransfer.getTransfer(),
                FileTransfer.getInstance()
        });
        dropTarget.addDropListener(new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetEvent event) {
                acceptCopy(event);
            }

            @Override
            public void dragOperationChanged(DropTargetEvent event) {
                acceptCopy(event);
            }

            @Override
            public void dragOver(DropTargetEvent event) {
                acceptCopy(event);
            }

            @Override
            public void dropAccept(DropTargetEvent event) {
                acceptCopy(event);
            }

            @Override
            public void drop(DropTargetEvent event) {
                String text = formatDroppedFilePaths(pathsFromDropData(event.data));
                if (text.isEmpty()) return;
                insertAtSelection(text);
                styledText.setFocus();
            }
        });
    }

    private static void acceptCopy(DropTargetEvent event) {
        if (event.detail == DND.DROP_DEFAULT || event.detail == DND.DROP_NONE) {
            event.detail = DND.DROP_COPY;
        }
    }

    private void insertAtSelection(String text) {
        Point selection = styledText.getSelection();
        styledText.replaceTextRange(selection.x, selection.y - selection.x, text);
        styledText.setSelection(selection.x + text.length());
    }

    static String formatDroppedFilePaths(List<String> paths) {
        if (paths == null || paths.isEmpty()) return "";

        var result = new StringBuilder();
        for (String path : paths) {
            if (path == null || path.isBlank()) continue;
            if (result.length() > 0) result.append('\n');
            result.append('\'').append(path.replace("'", "\\'")).append('\'');
        }
        return result.toString();
    }

    static List<String> pathsFromDropData(Object data) {
        var paths = new ArrayList<String>();
        addDropPaths(paths, data);
        if (paths.isEmpty()) {
            addDropPaths(paths, LocalSelectionTransfer.getTransfer().getSelection());
        }
        return paths;
    }

    private static void addDropPaths(List<String> paths, Object data) {
        if (data == null) return;
        if (data instanceof String[] values) {
            for (String value : values) addPath(paths, value);
        } else if (data instanceof IResource[] resources) {
            for (IResource resource : resources) addResourcePath(paths, resource);
        } else if (data instanceof IStructuredSelection selection) {
            for (Object element : selection) addElementPath(paths, element);
        } else if (data instanceof ISelection) {
            return;
        } else if (data instanceof Object[] values) {
            for (Object value : values) addElementPath(paths, value);
        } else {
            addElementPath(paths, data);
        }
    }

    private static void addElementPath(List<String> paths, Object element) {
        if (element instanceof IResource resource) {
            addResourcePath(paths, resource);
        } else if (element instanceof IJavaElement javaElement) {
            addResourcePath(paths, javaElement.getResource());
        } else if (element instanceof IAdaptable adaptable) {
            var resource = adaptable.getAdapter(IResource.class);
            if (resource != null) {
                addResourcePath(paths, resource);
                return;
            }
            var javaElement = adaptable.getAdapter(IJavaElement.class);
            if (javaElement != null) addResourcePath(paths, javaElement.getResource());
        }
    }

    private static void addResourcePath(List<String> paths, IResource resource) {
        addPath(paths, JdtUtil.diskPathOf(resource));
    }

    private static void addPath(List<String> paths, String path) {
        if (path != null && !path.isBlank()) paths.add(path);
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
