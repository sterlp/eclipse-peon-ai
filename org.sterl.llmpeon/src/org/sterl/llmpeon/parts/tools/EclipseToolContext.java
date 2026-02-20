package org.sterl.llmpeon.parts.tools;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.sterl.llmpeon.agent.AiMonitor.AiFileUpdate;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.tool.model.ToolContext;

/**
 * Mutable contextFile that provides Eclipse workspace file operations.
 * All returned paths are workspace-relative (e.g. "/ProjectName/src/Foo.java")
 * so they can be used to locate files again.
 *
 * <p>Methods throw exceptions on real errors (IO, workspace failures).
 * Only LLM-actionable conditions (e.g. "file not found") return error strings
 * so the LLM can retry with a different path.
 */
public class EclipseToolContext implements ToolContext {

    private IProject currentProject;
    private String selectedFile; // workspace-relative path
    
    /**
     * Resolves a path to a workspace resource (file or folder).
     * Tries workspace-relative first, then project-relative in each open project.
     */
    public static IResource resolveInEclipse(String path) {
        IPath ipath = IPath.fromOSString(path);
        try {
            // workspace-relative: works for both files and folders
            var result = ResourcesPlugin.getWorkspace().getRoot().findMember(ipath);
            if (result != null && result.exists()) return result;
        } catch (Exception e) {
            // invalid workspace path, continue
        }
        for (var p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (!p.isOpen()) continue;
            var result = p.findMember(ipath);
            System.err.println(ipath + " -> " + p.findMember(ipath));
            if (result != null && result.exists()) return result;
        }
        return null;
    }

    public void setCurrentProject(IProject project) {
        this.currentProject = project;
        System.err.println("setCurrentProject: " + project);
    }

    public IProject getCurrentProject() {
        return currentProject;
    }

    public void setSelectedFile(String relativePath) {
        this.selectedFile = relativePath;
        System.err.println("setCurrentProject: " + selectedFile);
    }

    public String getSelectedFile() {
        return selectedFile;
    }

    /**
     * Reads a file by workspace-relative or absolute path.
     * Returns "File not found" string only when the LLM can retry with a different path.
     */
    public String readFile(String path) {
        IFile file = resolveFile(path);
        if (file != null && file.exists()) {
            return readEclipseFile(file);
        }
        // fallback to raw filesystem
        try {
            var fsPath = java.nio.file.Path.of(path);
            if (Files.exists(fsPath) && Files.isRegularFile(fsPath)) {
                return Files.readString(fsPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + path, e);
        }
        // LLM-actionable: it can try a different path
        return "File not found: " + path;
    }

    /**
     * Updates an existing workspace file. Returns an LLM-actionable error string if the
     * file cannot be found — the LLM should retry using searchFiles to get the correct path.
     * Does NOT create new files.
     */
    public AiFileUpdate writeFile(String path, String content) {
        IFile file = resolveFile(path);
        if (file == null) {
            throw new IllegalArgumentException("File not found: " + path + ". Use searchFiles to find the correct workspace path.");
        }

        // capture old content for diff
        String oldContent = readEclipseFile(file);

        try {
            String charset = file.getCharset();
            Files.writeString(file.getLocation().toPath(), content, Charset.forName(charset));
            file.refreshLocal(IResource.DEPTH_ZERO, null);
        } catch (CoreException | IOException e) {
            throw new RuntimeException("Failed to write " + file.getFullPath(), e);
        }

        return new AiFileUpdate(file.getFullPath().toString(), oldContent, content);
    }

    /**
     * Creates a new file or overwrites an existing one.
     * Resolves the target project from the path, then falls back to currentProject.
     * Returns an LLM-actionable error if no project can be determined.
     */
    public String createFile(String path, String content) {
        // resolve which project and what project-relative sub-path to use
        IProject targetProject = null;
        String projectRelativePath = path;

        for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (!p.isOpen()) continue;
            String name = p.getName();
            // match "projectname/..." or "/projectname/..."
            if (path.startsWith(name + "/") || path.startsWith("/" + name + "/")) {
                targetProject = p;
                projectRelativePath = path.startsWith("/") ? path.substring(name.length() + 2) : path.substring(name.length() + 1);
                break;
            }
        }

        if (targetProject == null) {
            if (currentProject != null && currentProject.isOpen()) {
                targetProject = currentProject;
                // strip leading slash if present
                projectRelativePath = path.startsWith("/") ? path.substring(1) : path;
            } else {
                // list available projects for the LLM to choose from
                var sb = new StringBuilder("No active project. Available projects: ");
                for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
                    if (p.isOpen()) sb.append(p.getName()).append(", ");
                }
                sb.append("Provide a path starting with one of these project names, or ask the developer which project to use.");
                return sb.toString();
            }
        }

        IFile file = targetProject.getFile(projectRelativePath);
        String oldContent = file.exists() ? readEclipseFile(file) : "";

        try {
            // create missing parent folders
            ensureFolders(file.getParent());

            Charset charset = Charset.forName(targetProject.getDefaultCharset());
            byte[] bytes = content.getBytes(charset);
            if (file.exists()) {
                file.setContents(new ByteArrayInputStream(bytes), IResource.FORCE, null);
            } else {
                file.create(new ByteArrayInputStream(bytes), IResource.FORCE, null);
            }
        } catch (CoreException e) {
            throw new RuntimeException("Failed to create/write " + file.getFullPath(), e);
        }

        return (oldContent.isEmpty() ? "Created" : "Updated") + " file: " + file.getFullPath();
    }

    private void ensureFolders(IContainer container) throws CoreException {
        if (container == null || container instanceof IProject) return;
        if (container instanceof IFolder folder) {
            ensureFolders(folder.getParent());
            if (!folder.exists()) {
                // force=true: handles the case where the directory exists on disk
                // but is not yet registered in the workspace model (out-of-sync)
                folder.create(IResource.FORCE, true, null);
            }
        }
    }

    public List<String> searchFiles(String query) {
        var matches = new ArrayList<String>();
        if (query == null || query.isBlank()) return matches;

        String lowerQuery = query.toLowerCase();
        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (!project.isOpen()) continue;
            try {
                project.accept(new IResourceVisitor() {
                    @Override
                    public boolean visit(IResource resource) {
                        if (resource.getType() == IResource.FILE
                                && resource.getName().toLowerCase().contains(lowerQuery)) {
                            matches.add(getFileInfo((IFile) resource));
                        }
                        return true;
                    }
                });
            } catch (CoreException e) {
                throw new RuntimeException(e);
            }
        }
        return matches;
    }

    public String readSelectedFile() {
        if (selectedFile == null) {
            return "No file is currently selected - you may use search file";
        }
        IFile file = resolveFile(selectedFile);
        if (file == null || !file.exists()) {
            return "Selected file not found: " + selectedFile;
        }
        return getFileInfo(file) + "Content:\n" + readEclipseFile(file);
    }

    /**
     * Resolves a path to an existing workspace IFile using the following order:
     * 1. Exact workspace-relative path (e.g. "/ProjectName/src/Foo.java")
     * 2. Project-relative path within currentProject (e.g. "src/Foo.java")
     * 3. Filename match within currentProject
     * 4. Filename match across all open workspace projects
     */
    private IFile resolveFile(String path) {
        if (path == null) return null;
        String name = IPath.fromOSString(path).lastSegment();

        // 1. exact workspace-relative path
        try {
            IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(IPath.fromOSString(path));
            if (file.exists()) return file;
        } catch (Exception e) {
            // not a valid workspace-absolute path, continue
        }

        // 2. project-relative path within currentProject
        if (currentProject != null && currentProject.isOpen()) {
            try {
                IFile file = currentProject.getFile(path);
                if (file.exists()) return file;
            } catch (Exception e) {
                // not a valid project-relative path, continue
            }
        }

        // 3. filename match within currentProject
        if (currentProject != null && currentProject.isOpen() && name != null) {
            IFile[] found = findByName(currentProject, name);
            if (found.length > 0) return found[0];
        }

        return null;
    }

    private IFile[] findByName(IProject project, String name) {
        var result = new ArrayList<IFile>();
        try {
            project.accept(resource -> {
                if (resource.getType() == IResource.FILE && resource.getName().equals(name)) {
                    result.add((IFile) resource);
                }
                return true;
            });
        } catch (CoreException e) {
            throw new RuntimeException(e);
        }
        return result.toArray(new IFile[0]);
    }

    private String readEclipseFile(IFile file) {
        try (InputStream contents = file.getContents()) {
            return IoUtils.toString(contents, file.getCharset());
        } catch (CoreException | IOException e) {
            throw new RuntimeException("Failed to read " + file.getFullPath(), e);
        }
    }

    public static String getFileInfo(IFile file) {
        if (file == null) return "No file selected";
        return String.format("""
            File: %s
            Full path: %s
            Extension: %s
            Project name: %s
            Project path: %s
            """,
            file.getName(),
            file.getFullPath().toString(),
            file.getFileExtension(),
            file.getProject().getName(),
            file.getProjectRelativePath().toString()
        );
    }
}
