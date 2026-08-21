package org.sterl.llmpeon.context;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IOrdinaryClassFile;
import org.eclipse.jface.text.ITextSelection;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.shared.FileLines;
import org.sterl.llmpeon.shared.StringUtil;

public class UserContext {
    public static final String PROJECT_TAG = "User selected project: ";

    private volatile IProject currentProject;
    private volatile boolean projectPinned = false;

    private volatile IResource selectedResource;
    private volatile IClassFile clazz;
    private volatile ITextSelection textSelection;

    private final Set<ContextItem> addOneTimeOrders = new LinkedHashSet<>();
    
    public void addOneTimeOrder(ContextItem item) {
        this.addOneTimeOrders.add(item);
    }

    public List<ContextItem> get() {
        if (currentProject == null && selectedResource == null) return List.of();

        var result = new LinkedList<ContextItem>();
        if (currentProject != null) {
            result.add(new SimpleContextItem("Project info " + currentProject.getName(),
                    PROJECT_TAG + System.lineSeparator() + EclipseUtil.projectInfo(currentProject)
                )
            );
        }
        addUserSelection(result);
        result.addAll(addOneTimeOrders);
        addOneTimeOrders.clear();
        return result;
    }

    private void addUserSelection(List<ContextItem> result) {
        if (hasTextSelection()) {
            var sb = new StringBuilder();
            String path = JdtUtil.pathOf(selectedResource);
            if (selectedResource == null || !(selectedResource instanceof IFile)) {
                sb.append("\n\n```\n" + FileLines.format(textSelection.getText(), textSelection.getStartLine() + 1) + "\n```");
                if (clazz != null) sb.append("\n").append(getSelectedFile());
                else sb.append("\nselected content not in a file.");
            } else {
                sb.append(System.lineSeparator()).append(path).append(" full content. Selected lines ")
                  .append(lines(textSelection))
                  .append(":").append(System.lineSeparator());
                try {
                    sb.append(((IFile)selectedResource).readString());
                } catch (CoreException e) {
                    throw new RuntimeException(e);
                }
            }
            result.add(new SimpleContextItem("User text selection", sb.toString()));
        } else if (selectedResource != null) {
            result.add(new SimpleContextItem("File selected: " + JdtUtil.pathOf(selectedResource)));
        } else if (clazz != null) {
            result.add(new SimpleContextItem("Java type selected: " + getName(clazz)));
        }
    }
    
    public String getSelectedFile() {
        var open = EclipseUtil.getOpenFile();
        if (hasTextSelection()) {
            if (open.isEmpty()) {
                var name = "Text lines ";
                if (clazz != null) name = getName(clazz);
                return name + ":" + lines(textSelection);
            }
            else {
                selectedResource = open.get();
                return open.get().getName() + ":" + lines(textSelection);
            }
        } else {
            if (selectedResource == null && open.isPresent()) return open.get().getName();
            if (selectedResource instanceof IFile rf) return rf.getName();
            if (clazz != null) return getName(clazz);
        }
        return null;
    }
    
    private static String getName(IClassFile file) {
        if (file instanceof IOrdinaryClassFile of) {
            return of.getType().getFullyQualifiedName();
        }
        var parent = file.getParent();
        var name = file.getElementName();
        if (name == null) return "";
        var parentName = parent == null ? "" : parent.getElementName() + ".";
        return parentName + name.replace(".class", "");
    }
    
    public boolean hasTextSelection() {
        return textSelection != null && !textSelection.isEmpty()
                && StringUtil.hasValue(textSelection.getText());
    }
    
    private static String lines(ITextSelection selection) {
        if (selection == null || selection.isEmpty()) return "";
        return (selection.getStartLine() + 1) + "-" + (selection.getEndLine() + 1);
    }
    
    public IResource getSelectedResource() {
        return selectedResource;
    }

    public IProject getCurrentProject() {
        return currentProject;
    }

    public boolean setCurrentProject(IProject currentProject) {
        if (this.currentProject != currentProject) {
            this.currentProject = currentProject;
            return true;
        }
        return false;
    }

    public ITextSelection getTextSelection() {
        return textSelection;
    }

    public void setTextSelection(ITextSelection textSelection) {
        this.textSelection = textSelection;
    }

    public void setSelectedResource(IResource selectedResource) {
        this.selectedResource = selectedResource;
        if (this.selectedResource != null) this.clazz = null;
    }

    public boolean isProjectPinned() {
        return projectPinned;
    }

    public void setProjectPinned(boolean projectPinned) {
        this.projectPinned = projectPinned;
    }

    public void setClassFile(IClassFile cf) {
        this.clazz = cf;
    }
}
