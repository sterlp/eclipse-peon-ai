package org.sterl.llmpeon.parts.tools;

import java.nio.charset.Charset;
import java.nio.file.Files;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.sterl.llmpeon.agent.AiMonitor.AiFileUpdate;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.shared.FileUtils;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseWorkspaceWriteFilesTool extends AbstractEclipseTool {

    private IProject currentProject;

    public void setCurrentProject(IProject project) {
        this.currentProject = project;
    }

    public IProject getCurrentProject() {
        return currentProject;
    }
    
    @Override
    public boolean isEditTool() {
        return true;
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

        var inFile = EclipseUtil.resolveInEclipse(filePath);
        if (inFile.isEmpty() || !(inFile.get() instanceof IFile)) {
            // fallback to raw filesystem
            var f = java.nio.file.Path.of(filePath);
            if (Files.isRegularFile(f)) {
                String oldContent = FileUtils.readString(f);
                FileUtils.writeString(f, newContent);
                if (hasMonitor()) monitor.onFileUpdate(new AiFileUpdate(filePath, oldContent, newContent));
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
        monitorMessage("Created file " + JdtUtil.pathOf(file));
        return "Created file: " + JdtUtil.pathOf(file);
    }

    @Tool("Deletes a file in the Eclipse workspace. "
            + "Use searchWorkspaceFiles first to find the correct path.")
    public String deleteWorkspaceFile(
            @P("Workspace-relative or project-relative path") String filePath) {

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be empty");
        }

        var file = EclipseUtil.resolveInEclipse(filePath);
        if (file.isEmpty()) throw new IllegalArgumentException("Not found: " + filePath);

        var pathToDelete = JdtUtil.pathOf(file.get());
        try {
            try {
                file.get().delete(IResource.KEEP_HISTORY, getProgressMonitor());
            } catch (Exception e) {
                file.get().delete(IResource.FORCE, getProgressMonitor());
            }
            return "Deleted file: " + pathToDelete;
        } catch (CoreException e) {
            throw new RuntimeException("Failed to delete " + pathToDelete, e);
        }
    }

    private AiFileUpdate writeEclipseFile(IFile file, String content) {
        var oldContent = IoUtils.readFile(file);
        try {
            var charset = Charset.forName(file.getCharset());
            file.write(content.getBytes(charset), false, false, true, getProgressMonitor());
            file.refreshLocal(IResource.DEPTH_ZERO, getProgressMonitor());
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
            file.write(bytes, true, false, true, getProgressMonitor());
            file.refreshLocal(IResource.DEPTH_ZERO, getProgressMonitor());
        } catch (CoreException e) {
            throw new RuntimeException("Failed to create/write " + file.getFullPath(), e);
        }
        return file;
    }

    private void ensureFolders(IContainer container) throws CoreException {
        if (container == null || container instanceof IProject) return;
        if (container instanceof IFolder folder) {
            if (!folder.exists()) {
                ensureFolders(folder.getParent());
                folder.create(IResource.FORCE, true, null);
            }
        }
    }
}
