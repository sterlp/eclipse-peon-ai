package org.sterl.llmpeon.parts.widget;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.util.LocalSelectionTransfer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.part.ResourceTransfer;
import org.eclipse.swt.custom.StyledText;
import org.sterl.llmpeon.parts.shared.JdtUtil;

final class FileDropSupport {

    private FileDropSupport() {
    }

    static void install(Control dropControl, StyledText targetText) {
        var dropTarget = new DropTarget(dropControl, DND.DROP_COPY);
        dropControl.addDisposeListener(e -> {
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
                insertAtSelection(targetText, text);
                targetText.setFocus();
            }
        });
    }

    private static void acceptCopy(DropTargetEvent event) {
        if (event.detail == DND.DROP_DEFAULT || event.detail == DND.DROP_NONE) {
            event.detail = DND.DROP_COPY;
        }
    }

    private static void insertAtSelection(StyledText targetText, String text) {
        Point selection = targetText.getSelection();
        targetText.replaceTextRange(selection.x, selection.y - selection.x, text);
        targetText.setSelection(selection.x + text.length());
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
}
