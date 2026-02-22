package org.sterl.llmpeon.parts.shared;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.sterl.llmpeon.shared.StringUtil;

public class EclipseUtil {

    public static Optional<IFile> getOpenFile() {
        if (PlatformUI.getWorkbench() == null) return Optional.empty();
        var aww = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (aww == null) return Optional.empty();
        var ap = aww.getActivePage();
        if (ap == null) return Optional.empty();
        IEditorPart editor = ap.getActiveEditor();

        if (editor != null) {
            IEditorInput input = editor.getEditorInput();
            IFile file = input.getAdapter(IFile.class);
            return Optional.ofNullable(file);
        }
        return Optional.empty();
    }
    
    public static Optional<IProject> findOpenProject(String path) {
        if (StringUtil.hasNoValue(path)) return Optional.empty();
        var projectPath = Path.of(path).getName(0).toString();

        for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (!p.isOpen()) continue;
            if (p.getName().equalsIgnoreCase(projectPath)) return Optional.of(p);
        }
        return Optional.empty();
    }
    
    
    public static List<IProject> openProjects() {
        var result = new ArrayList<IProject>();
        for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (!p.isOpen()) continue;
            result.add(p);
        }
        return result;
    }
}
