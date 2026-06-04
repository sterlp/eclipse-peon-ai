package org.sterl.llmpeon.parts.model;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.text.ITextSelection;
import org.sterl.llmpeon.StandingOrdersBuilder.MessageProvider;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.shared.FileLines;
import org.sterl.llmpeon.shared.StringUtil;

public class UserContext implements MessageProvider {
    private volatile IProject currentProject;
    private volatile boolean projectPinned = false;

    private volatile IResource selectedResource;
    private volatile ITextSelection textSelection;

    @Override
    public String get() {
        if (currentProject == null && selectedResource == null) return null;
        
        var sb = new StringBuilder();
        if (currentProject != null) {
            sb.append("Select in Eclipse:\n");
            sb.append(EclipseUtil.projectInfo(currentProject));
        }
        if (selectedResource != null) {
            sb.append("\nFile selected: ").append(JdtUtil.pathOf(selectedResource));
            sb.append("\nDisk path:     ").append(JdtUtil.diskPathOf(selectedResource));
        }
        return sb.toString();
    }
    
    public String getUserSelection() {
        if (textSelection == null || StringUtil.hasNoValue(textSelection.getText())) return "";
        var file = EclipseUtil.getOpenFile();

        var extension = "\n";
        if (file.isPresent()) extension = file.get().getFileExtension() + "\n";

        String userIn = "\n\n```" + extension + FileLines.format(textSelection.getText(), textSelection.getStartLine() + 1) + "\n```";

        if (file.isPresent()) {
            userIn += "\n\nFile: `" + JdtUtil.pathOf(file.get()) + "`";
        }
        return userIn;
    }
    
    public IFile getSelectedFile() {
        if (selectedResource instanceof IFile rf) return rf;
        var open = EclipseUtil.getOpenFile();
        if (open.isPresent()) return open.get();
        return null;
    }
    
    public IResource getSelectedResource() {
        return selectedResource;
    }

    public IProject getCurrentProject() {
        return currentProject;
    }

    public void setCurrentProject(IProject currentProject) {
        this.currentProject = currentProject;
    }

    public ITextSelection getTextSelection() {
        return textSelection;
    }

    public void setTextSelection(ITextSelection textSelection) {
        this.textSelection = textSelection;
    }

    public void setSelectedResource(IResource selectedResource) {
        this.selectedResource = selectedResource;
    }

    public boolean isProjectPinned() {
        return projectPinned;
    }

    public void setProjectPinned(boolean projectPinned) {
        this.projectPinned = projectPinned;
    }
}
