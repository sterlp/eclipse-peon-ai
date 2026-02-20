package org.sterl.llmpeon.parts.tools;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.sterl.llmpeon.agent.AiMonitor.AiFileUpdate;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.shared.FileUtils;
import org.sterl.llmpeon.tool.model.FileContext;

/**
 * Eclipse workspace implementation of {@link FileContext}.
 * All returned paths are workspace-relative (e.g. "/ProjectName/src/Foo.java")
 * so they can be used to locate files again.
 *
 * <p>Methods throw exceptions on real errors (IO, workspace failures).
 * Only LLM-actionable conditions (e.g. "file not found") return error strings
 * so the LLM can retry with a different path.
 */
public class EclipseToolContext implements FileContext {

    private IProject currentProject;
    private String selectedFile; // workspace-relative path
    
    /**
     * Resolves a path to a workspace resource (file or folder).
     * Tries workspace-relative first, then project-relative in each open project.
     */
    public static Optional<IResource> resolveInEclipse(String path) {
        IPath ipath = IPath.fromOSString(path);
        try {
            // workspace-relative: works for both files and folders
            var result = ResourcesPlugin.getWorkspace().getRoot().findMember(ipath);
            if (result != null && result.exists()) return Optional.of(result);
        } catch (Exception e) {
            // invalid workspace path, continue
        }
        for (var p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (!p.isOpen()) continue;
            var result = p.findMember(ipath);
            System.err.println(ipath + " -> " + p.findMember(ipath));
            if (result != null && result.exists()) return Optional.of(result);
        }
        return Optional.empty();
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
        var file = resolveInEclipse(path);
        if (file.isPresent() && file.get() instanceof IFile f) {
            return readEclipseFile(f);
        }
        // fallback to raw filesystem
        var fsPath = java.nio.file.Path.of(path);
        if (Files.exists(fsPath) && Files.isRegularFile(fsPath)) {
            return FileUtils.readString(fsPath);
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
        var inFile = resolveInEclipse(path);
        String oldContent;
        if (inFile.isEmpty() || !(inFile.get() instanceof IFile)) {
            var f = Path.of(path);
            if (Files.isRegularFile(f)) {
                oldContent = FileUtils.readString(f);
                FileUtils.writeString(f, content);
            } else {
                throw new IllegalArgumentException("File not found: " + path + ". Use searchFiles to find the correct workspace path.");
            }
        } else {
            IFile file = (IFile)inFile.get();
            
            // capture old content for diff
            oldContent = readEclipseFile(file);
            path = file.getFullPath().toPortableString();
            try {
                var charset = Charset.forName(file.getCharset());
                file.write(content.getBytes(charset), false, false, true, new NullProgressMonitor());
                file.refreshLocal(IResource.DEPTH_ZERO, new NullProgressMonitor());
            } catch (CoreException  e) {
                throw new RuntimeException("Failed to write " + file.getFullPath(), e);
            }
        }

        return new AiFileUpdate(path, oldContent, content);
    }

    /**
     * Creates a new file or overwrites an existing one.
     * Resolves the target project from the path, then falls back to currentProject.
     * Returns an LLM-actionable error if no project can be determined.
     */
    public String createFile(String path, String content) {
        // resolve which project and what project-relative sub-path to use
        IProject targetProject = null;
        String projectRelativePath = Path.of(path).toString(); // fix any separatorChar

        for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (!p.isOpen()) continue;
            String name = p.getName();
            // match "projectname/..." or "/projectname/..."
            if (path.startsWith(name + File.separatorChar) || path.startsWith(File.separatorChar + name + File.separatorChar)) {
                targetProject = p;
                projectRelativePath = path.startsWith(File.separator) ? path.substring(name.length() + 2) : path.substring(name.length() + 1);
                break;
            }
        }

        if (targetProject == null) {
            if (currentProject != null && currentProject.isOpen()) {
                targetProject = currentProject;
                // strip leading slash if present
                projectRelativePath = path.startsWith(File.separator) ? path.substring(1) : path;
            } else {
                var writeTo = Path.of(path);
                System.err.println("File " + writeTo + " exists: " + Files.exists(writeTo));
                FileUtils.writeString(writeTo, content);
                return "Created file: " + writeTo.toAbsolutePath();
            }
        }

        IFile file = targetProject.getFile(projectRelativePath);

        try {
            // create missing parent folders
            ensureFolders(file.getParent());

            Charset charset = Charset.forName(targetProject.getDefaultCharset());
            byte[] bytes = content.getBytes(charset);
            System.err.println("Eclipse create file " + file.getFullPath().toPortableString() + " exists: " + file.exists());
            if (file.exists()) {
                file.setContents(new ByteArrayInputStream(bytes), IResource.FORCE, new NullProgressMonitor());
            } else {
                file.create(new ByteArrayInputStream(bytes), IResource.FORCE, new NullProgressMonitor());
            }
            file.refreshLocal(IResource.DEPTH_ZERO, new NullProgressMonitor());
        } catch (CoreException e) {
            throw new RuntimeException("Failed to create/write " + file.getFullPath(), e);
        }
        return "Created file: " + file.getFullPath();
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

    @Override
    public String deleteFile(String path) {
        var file = resolveInEclipse(path);
        if (file.isEmpty()) {
            return "Not found: " + path;
        }
        String fullPath = file.get().getFullPath().toPortableString();
        try {
            try {
                file.get().delete(IResource.KEEP_HISTORY, new NullProgressMonitor());
            } catch (Exception e) {
                file.get().delete(IResource.FORCE, new NullProgressMonitor());
            }
            return "Deleted file: " + fullPath;
        } catch (CoreException e) {
            throw new RuntimeException("Failed to delete " + fullPath, e);
        }
    }

    public String readSelectedFile() {
        if (selectedFile == null) {
            return "No file is currently selected - you may use search file";
        }
        var file = resolveInEclipse(selectedFile);
        if (file.isEmpty() || !(file.get() instanceof IFile)) {
            return "Selected file not found: " + selectedFile;
        }
        return getFileInfo((IFile)file.get()) + "Content:\n" + readEclipseFile((IFile)file.get());
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
