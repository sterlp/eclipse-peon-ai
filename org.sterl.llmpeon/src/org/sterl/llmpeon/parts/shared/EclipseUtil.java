package org.sterl.llmpeon.parts.shared;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.sterl.llmpeon.shared.StringUtil;

public class EclipseUtil {
    // TODO move to EclipseUiUtil
    public static void runInUiThread(Composite parent, Runnable fn) {
        if (parent == null || parent.isDisposed()) return;
        parent.getDisplay().asyncExec(() -> {
            if (parent.isDisposed()) return;
            fn.run();
        });
    }
    
    public static Path workspacePath() {
        var root = ResourcesPlugin.getWorkspace().getRoot();
        IPath loc = root.getRawLocation();
        if (loc == null) {
            loc = root.getLocation();
        }
        if (loc == null) {
            throw new IllegalStateException("Workspace root has no filesystem location");
        }
        return loc.toFile().toPath();
    }

    /**
     * Opens the given workspace file in the workbench editor.
     * Must be called from the UI thread.
     * Throws {@link RuntimeException} if the editor cannot be opened.
     */
    // TODO move to EclipseUiUtil
    public static void openInEditor(IFile file) {
        var page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        if (page == null || !file.exists()) return;
        try {
            IDE.openEditor(page, file, true);
        } catch (Exception e) {
            throw new RuntimeException("Could not open editor for " + file.getFullPath(), e);
        }
    }

    public static IProject firstOpenOrSelectedProject() {
        var openFile = getOpenFile();
        if (openFile.isPresent()) return openFile.get().getProject();
        var open = openProjects();
        return open.isEmpty() ? null : open.getFirst();
    }
    
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
        var name = Path.of(path).normalize().getName(0).toString();

        for (var p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (!p.isOpen()) continue;
            if (p.getName().equalsIgnoreCase(name)
                    || p.getFullPath().toPortableString().contains(path)) {
                return Optional.of(p);
            }
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
    
    public static String openProjectsNames() {
        return EclipseUtil.openProjects().stream().map(IProject::getName).collect(Collectors.joining(", "));
    }
    
    public static String projectNatures(IProject project) {
        try {
            String[] ids = project.getDescription().getNatureIds();
            if (ids.length == 0) return "none";
            var sb = new StringBuilder();
            for (String id : ids) {
                if (!sb.isEmpty()) sb.append(", ");
                // show short name for well-known natures, full id otherwise
                sb.append(switch (id) {
                    case "org.eclipse.jdt.core.javanature" -> "java";
                    case "org.eclipse.pde.PluginNature" -> "pde-plugin";
                    case "org.eclipse.m2e.core.maven2Nature" -> "maven";
                    case "org.eclipse.buildship.core.gradleprojectnature" -> "gradle";
                    default -> id;
                });
            }
            return sb.toString();
        } catch (CoreException e) {
            return "unknown";
        }
    }
    public static String projectInfo(IProject p) {
        final var result = new StringBuilder();
        result.append("Project name: ").append(p.getName())
              .append("\nEclipse path: ").append(JdtUtil.pathOf(p))
              .append("\nDisk path: ").append(JdtUtil.diskPathOf(p))
              .append("\nNatures: ").append(projectNatures(p));
        
        var m = findMember(p, "pom.xml");
        if (m.isPresent()) result.append(JdtUtil.pathOf(m.get()));
        m = findMember(p, "package.json");
        if (m.isPresent()) result.append(JdtUtil.pathOf(m.get()));
              
        return result.toString();
    }

    /**
     * Resolves a path to a workspace resource (file or folder).
     * Tries workspace-relative first, then project-relative in each open project.
     */
    public static Optional<IResource> resolveInEclipse(String path) {
        if (StringUtil.hasNoValue(path)) return Optional.empty();
        IPath ipath = IPath.fromPortableString(path);
        try {
            var result = ResourcesPlugin.getWorkspace().getRoot().findMember(ipath);
            if (result != null && result.exists()) return Optional.of(result);
        } catch (Exception e) {
            // invalid workspace path, continue
        }
        
        for (var p : openProjects()) {
            var result = p.findMember(ipath);
            if (result != null && result.exists()) return Optional.of(result);
            // java src fallback
            result = p.findMember("src/" + ipath);
            if (result != null && result.exists()) return Optional.of(result);
        }
        return Optional.empty();
    }
    
    public static Optional<IFile> findMember(IContainer root, String path) {
        var f = root.findMember(path);
        if (f != null && f instanceof IFile ff) return Optional.of(ff);
        return Optional.empty();
    }

    /**
     * Extracts the project from a resource selection.
     */
    public static IProject resolveProject(IResource selection) {
        if (selection == null) return null;
        return selection.getProject();
    }

}
