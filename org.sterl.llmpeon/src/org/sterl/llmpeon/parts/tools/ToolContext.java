package org.sterl.llmpeon.parts.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.SimpleDiff;

/**
 * Mutable context that provides Eclipse workspace file operations.
 * All returned paths are workspace-relative (e.g. "/ProjectName/src/Foo.java")
 * so they can be used to locate files again.
 *
 * <p>Methods throw exceptions on real errors (IO, workspace failures).
 * Only LLM-actionable conditions (e.g. "file not found") return error strings
 * so the LLM can retry with a different path.
 */
public class ToolContext {

    private IProject currentProject;
    private String selectedFile; // workspace-relative path
    private Consumer<String> diffObserver;

    public void setCurrentProject(IProject project) {
        this.currentProject = project;
    }

    public IProject getCurrentProject() {
        return currentProject;
    }

    public void setSelectedFile(String relativePath) {
        this.selectedFile = relativePath;
    }

    public String getSelectedFile() {
        return selectedFile;
    }

    /**
     * Sets a callback that receives unified diff strings after file writes.
     */
    public void setDiffObserver(Consumer<String> observer) {
        this.diffObserver = observer;
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
    public String writeFile(String path, String content) {
        IFile file = resolveFile(path);
        if (file == null) {
            return "File not found: " + path + ". Use searchFiles to find the correct workspace path.";
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

        if (diffObserver != null) {
            String diff = SimpleDiff.unifiedDiff(file.getFullPath().toString(), oldContent, content);
            if (!diff.isEmpty()) {
                diffObserver.accept(diff);
            }
        }

        return "Successfully updated file: " + file.getFullPath();
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

        // 4. filename match across all open workspace projects
        if (name != null) {
            for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
                if (!project.isOpen() || project.equals(currentProject)) continue;
                IFile[] found = findByName(project, name);
                if (found.length > 0) return found[0];
            }
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
