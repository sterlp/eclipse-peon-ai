package org.sterl.llmpeon.parts.tools;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.sterl.llmpeon.agent.AiMonitor.AiFileUpdate;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.shared.FileUtils;
import org.sterl.llmpeon.tool.AbstractTool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseWorkspaceFilesTool extends AbstractTool {

    private IProject currentProject;

    public void setCurrentProject(IProject project) {
        this.currentProject = project;
    }

    public IProject getCurrentProject() {
        return currentProject;
    }

    @Tool("Reads a file from the Eclipse workspace. "
            + "Accepts workspace-relative paths (e.g. '/MyProject/src/Foo.java') "
            + "or project-relative paths (e.g. 'src/Foo.java'). "
            + "Use searchWorkspaceFiles first to find the correct path.")
    public String readWorkspaceFile(
            @P("Workspace-relative or project-relative path or disk absolute path as fallback") String filePath) {

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be empty");
        }

        monitorMessage("Reading " + filePath);
        var file = EclipseUtil.resolveInEclipse(filePath);
        if (file.isPresent() && file.get() instanceof IFile f) {
            return readEclipseFile(f);
        }
        // fallback to raw filesystem for absolute paths
        var fsPath = java.nio.file.Path.of(filePath);
        if (Files.exists(fsPath) && Files.isRegularFile(fsPath)) {
            return FileUtils.readString(fsPath);
        }
        onProblem("File not found: " + filePath);
        return "File not found: " + filePath;
    }

    @Tool("Updates the complete content of an existing file in the Eclipse workspace. "
            + "Only works for files that already exist - does not create new files. "
            + "Use searchWorkspaceFiles first to find the correct path.")
    public String writeWorkspaceFile(
            @P("Workspace-relative or project-relative path") String filePath,
            @P("The complete new file content") String newContent) {

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be empty");
        }
        if (newContent == null || newContent.isBlank()) {
            throw new IllegalArgumentException("newContent must not be empty");
        }

        monitorMessage("Writing " + filePath);
        var inFile = EclipseUtil.resolveInEclipse(filePath);
        if (inFile.isEmpty() || !(inFile.get() instanceof IFile)) {
            // fallback to raw filesystem
            var f = java.nio.file.Path.of(filePath);
            if (Files.isRegularFile(f)) {
                String oldContent = FileUtils.readString(f);
                FileUtils.writeString(f, newContent);
                var result = new AiFileUpdate(filePath, oldContent, newContent);
                if (hasMonitor()) monitor.onFileUpdate(result);
                return "File " + filePath + " updated.";
            }
            throw new IllegalArgumentException(
                    "File not found: " + filePath + ". Use searchWorkspaceFiles to find the correct path.");
        }
        var result = writeEclipseFile((IFile) inFile.get(), newContent);
        if (hasMonitor()) monitor.onFileUpdate(result);
        return "File " + result.file() + " updated.";
    }

    @Tool("Creates a new file or overwrites an existing one in the Eclipse workspace. "
            + "The path can include the project name ('myproject/src/Foo.java') or be project-relative ('src/Foo.java'). "
            + "Missing parent folders are created automatically.")
    public String createWorkspaceFile(
            @P("File path including project name or project-relative") String filePath,
            @P("File content to write") String content) {

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be empty");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be empty");
        }

        monitorMessage("Creating " + filePath);

        var targetProject = EclipseUtil.findOpenProject(filePath);
        String projectRelativePath = java.nio.file.Path.of(filePath).toString();

        if (targetProject.isPresent()) {
            // strip the project name prefix from the path
            var pathObj = java.nio.file.Path.of(filePath);
            if (pathObj.getNameCount() > 1) {
                projectRelativePath = pathObj.subpath(1, pathObj.getNameCount()).toString();
            }
        } else if (currentProject != null && currentProject.isOpen()) {
            targetProject = java.util.Optional.of(currentProject);
            projectRelativePath = filePath.startsWith("/") || filePath.startsWith("\\")
                    ? filePath.substring(1) : filePath;
        }

        if (targetProject.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot determine target project for path: " + filePath
                    + ". Use listAllOpenEclipseProjects to find available projects.");
        }

        IFile file = writeFileToProject(targetProject.get(), projectRelativePath, content);
        return "Created file: " + file.getFullPath().toPortableString();
    }

    @Tool("Deletes a file in the Eclipse workspace. "
            + "Use searchWorkspaceFiles first to find the correct path.")
    public String deleteWorkspaceFile(
            @P("Workspace-relative or project-relative path") String filePath) {

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be empty");
        }

        monitorMessage("Deleting " + filePath);
        var file = EclipseUtil.resolveInEclipse(filePath);
        if (file.isEmpty()) {
            return "Not found: " + filePath;
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

    @Tool("Searches for files whose name contains the given query string in the Eclipse workspace. "
            + "Searches all open projects, skips derived resources (target/, bin/).")
    public String searchWorkspaceFiles(
            @P("Part of the file name to search for, e.g. 'Controller' or '.xml'") String query) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be empty");
        }

        monitorMessage("Searching for " + query);
        String lowerQuery = query.toLowerCase();
        var matches = new ArrayList<String>();

        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            if (!project.isOpen()) continue;
            try {
                project.accept(new IResourceVisitor() {
                    @Override
                    public boolean visit(IResource resource) {
                        if (resource.isDerived()) return false;
                        if (resource.getType() == IResource.FILE
                                && resource.getName().toLowerCase().contains(lowerQuery)) {
                            matches.add(resource.getFullPath().toPortableString());
                        }
                        return true;
                    }
                });
            } catch (CoreException e) {
                throw new RuntimeException(e);
            }
        }

        if (matches.isEmpty()) {
            return "No files found matching '" + query + "'";
        }
        monitorMessage("Found " + matches.size() + " files");
        return "Found " + matches.size() + " file(s):\n" + String.join("\n", matches);
    }

    @Tool("Lists files and folders directly in a workspace directory (non-recursive). "
            + "Use this to navigate and explore the workspace structure. "
            + "If the path is empty or root, lists all open Eclipse projects. "
            + "Returns entries prefixed with [DIR] or [FILE].")
    public String listWorkspaceDirectory(
            @P("Workspace-relative path, e.g. '/MyProject/src'. Empty or '/' lists projects.") String path) {

        // root: list open projects
        if (path == null || path.isBlank() || path.length() == 1) {
            var t = new EclipseBuildTool();
            t.withMonitor(monitor);
            return t.listAllOpenEclipseProjects();
        }

        var resource = EclipseUtil.resolveInEclipse(path);
        if (resource.isEmpty()) {
            onProblem("Listing " + path + " not found");
            return "Directory not found: " + path;
        }

        var res = resource.get();
        if (!(res instanceof IContainer container)) {
            onProblem("Listing " + path + " is not a directory");
            return path + " is a file, not a directory. Use readWorkspaceFile to read it.";
        }

        try {
            var entries = new ArrayList<String>();
            for (IResource member : container.members()) {
                if (member.isDerived()) continue;
                String prefix = (member.getType() == IResource.FILE) ? "[FILE] " : "[DIR]  ";
                entries.add(prefix + member.getFullPath().toPortableString());
            }
            monitorMessage("Listing " + path + " found " + entries.size() + " elements");
            if (entries.isEmpty()) return "Directory is empty: " + path;
            return "Contents of " + res.getFullPath().toPortableString() + ":\n" + String.join("\n", entries);
        } catch (CoreException e) {
            throw new RuntimeException("Failed to list " + path, e);
        }
    }

    private AiFileUpdate writeEclipseFile(IFile file, String content) {
        var oldContent = readEclipseFile(file);
        try {
            var charset = Charset.forName(file.getCharset());
            file.write(content.getBytes(charset), false, false, true, new NullProgressMonitor());
            file.refreshLocal(IResource.DEPTH_ZERO, new NullProgressMonitor());
        } catch (CoreException e) {
            throw new RuntimeException("Failed to write " + file.getFullPath(), e);
        }
        return new AiFileUpdate(file.getFullPath().toPortableString(), oldContent, content);
    }

    private IFile writeFileToProject(IProject targetProject, String projectRelativePath, String content) {
        IFile file = targetProject.getFile(projectRelativePath);
        try {
            ensureFolders(file.getParent());
            Charset charset = Charset.forName(targetProject.getDefaultCharset());
            byte[] bytes = content.getBytes(charset);
            if (file.exists()) {
                file.setContents(new ByteArrayInputStream(bytes), IResource.FORCE, new NullProgressMonitor());
            } else {
                file.create(new ByteArrayInputStream(bytes), IResource.FORCE, new NullProgressMonitor());
            }
            file.refreshLocal(IResource.DEPTH_ZERO, new NullProgressMonitor());
        } catch (CoreException e) {
            throw new RuntimeException("Failed to create/write " + file.getFullPath(), e);
        }
        return file;
    }

    private void ensureFolders(IContainer container) throws CoreException {
        if (container == null || container instanceof IProject) return;
        if (container instanceof IFolder folder) {
            ensureFolders(folder.getParent());
            if (!folder.exists()) {
                folder.create(IResource.FORCE, true, null);
            }
        }
    }

    private String readEclipseFile(IFile file) {
        try (InputStream contents = file.getContents()) {
            return IoUtils.toString(contents, file.getCharset());
        } catch (CoreException | IOException e) {
            throw new RuntimeException("Failed to read " + file.getFullPath(), e);
        }
    }
}
