package org.sterl.llmpeon.context;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IOrdinaryClassFile;
import org.eclipse.jdt.core.IType;
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
    private volatile IJavaElement javaType;
    private volatile ITextSelection textSelection;

    private final Set<ContextItem> addOneTimeOrders = new LinkedHashSet<>();
    
    public void addOneTimeOrder(ContextItem item) {
        this.addOneTimeOrders.add(item);
    }

    public List<ContextItem> get() {
        // R-SEL-2/4: a pure text selection or type goes out even without project or resource.
        if (currentProject == null && selectedResource == null && !hasTextSelection() && javaType == null) return List.of();

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
                // R-SEL-4: text and type selection strictly alternate, so a type can never
                // coexist with a text selection here — the snippet stands alone.
                sb.append("\n\n```\n" + FileLines.format(textSelection.getText(), textSelection.getStartLine() + 1) + "\n```")
                  .append("\nselected content not in a file.");
            } else {
                // R-SEL-3: snippet with line numbers + path — never the full file content.
                sb.append(System.lineSeparator()).append(path)
                  .append(System.lineSeparator()).append("Selected lines ").append(lines(textSelection)).append(':')
                  .append(System.lineSeparator()).append("```").append('\n')
                  .append(FileLines.format(textSelection.getText(), textSelection.getStartLine() + 1))
                  .append("```");
            }
            result.add(new SimpleContextItem("User text selection", sb.toString()));
        } else if (selectedResource != null) {
            result.add(new SimpleContextItem("File selected: " + JdtUtil.pathOf(selectedResource)));
        } else if (javaType != null) {
            result.add(new SimpleContextItem("Java type selected: " + getName(javaType)));
        }
    }
    
    public String getSelectedFile() {
        var open = EclipseUtil.getOpenFile();
        if (hasTextSelection()) {
            if (open.isEmpty()) {
                var name = "Text lines ";
                if (javaType != null) name = getName(javaType);
                return name + ":" + lines(textSelection);
            }
            else {
                selectedResource = open.get();
                return open.get().getName() + ":" + lines(textSelection);
            }
        } else {
            if (selectedResource == null && open.isPresent()) return open.get().getName();
            if (selectedResource instanceof IFile rf) return rf.getName();
            if (javaType != null) return getName(javaType);
        }
        return null;
    }
    
    private static String getName(IJavaElement e) {
        // R-SEL-4: a type is a type — IType first (covers IOrdinaryClassFile's type).
        if (e instanceof IType t) return t.getFullyQualifiedName();
        if (e instanceof IOrdinaryClassFile of) {
            return of.getType().getFullyQualifiedName();
        }
        var parent = e.getParent();
        var name = e.getElementName();
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

    /**
     * R-SEL-4: every text selection event (also empty/caret) replaces a type selection.
     *
     * @return <code>true</code> if an UI update is needed due to line changes, otherwise <code>false</code>
     */
    public boolean setTextSelection(ITextSelection newText) {
        var old = this.textSelection;
        this.javaType = null;
        this.textSelection = newText;
        if (old == newText) return false;
        if (old == null || newText == null) return true;
        return old.getStartLine() != newText.getStartLine()
            || old.getEndLine() != newText.getEndLine();
    }

    /**
     * @return <code>true</code> if the selected resource changed (UI update needed), otherwise <code>false</code>
     */
    public boolean setSelectedResource(IResource newResource) {
        var changed = !Objects.equals(JdtUtil.pathOf(newResource), JdtUtil.pathOf(this.selectedResource));

        // R-SEL-1: only a switch to another concrete file clears the text selection —
        // null events (no resource) and the same file keep it.
        if (changed && newResource != null) this.textSelection = null;

        this.selectedResource = newResource;
        // R-SEL-4: a concrete file selection replaces a type selection.
        if (this.selectedResource != null) this.javaType = null;

        return changed;
    }

    public boolean isProjectPinned() {
        return projectPinned;
    }

    public void setProjectPinned(boolean projectPinned) {
        this.projectPinned = projectPinned;
    }

    /**
     * R-SEL-4: a type selection event replaces both the file and the text selection.
     *
     * @return <code>true</code> if an UI update is needed (the type changed), otherwise <code>false</code>
     */
    public boolean setJavaType(IJavaElement type) {
        // R-SEL-1 spirit: a null event is no selection event — keep the current state.
        if (type == null) return false;
        if (this.javaType == type) return false;
        this.selectedResource = null;
        this.textSelection = null;
        this.javaType = type;
        return true;
    }

    public IJavaElement getJavaType() {
        return javaType;
    }
}
