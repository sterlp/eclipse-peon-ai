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

    @Tool("Eclipse: Replace exact string in workspace file. Errors if 0 or >1 matches.")
    public String editWorkspaceFile(
            @P("workspace-relative path") String filePath,
            @P("exact string to replace") String oldString,
            @P("replacement text") String newString) {

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be empty");
        }
        if (oldString == null || oldString.isBlank()) {
            throw new IllegalArgumentException("oldString must not be empty");
        }
        if (newString == null) {
            throw new IllegalArgumentException("newString must not be null");
        }
        if (oldString.equals(newString)) {
            throw new IllegalArgumentException("oldString and newString are identical - nothing to change");
        }

        var inFile = EclipseUtil.resolveInEclipse(filePath);
        if (inFile.isEmpty() || !(inFile.get() instanceof IFile eclipseFile)) {
            // fallback to raw filesystem
            var f = java.nio.file.Path.of(filePath);
            if (java.nio.file.Files.isRegularFile(f)) {
                String content = org.sterl.llmpeon.shared.FileUtils.readString(f);
                String newContent = applyEdit(filePath, content, oldString, newString);
                org.sterl.llmpeon.shared.FileUtils.writeString(f, newContent);
                if (hasMonitor()) monitor.onFileUpdate(new AiFileUpdate(filePath, oldString, newString));
                return "File " + filePath + " edited successfully.";
            }
            throw new IllegalArgumentException(
                    "File not found: " + filePath + ". Use searchWorkspaceFiles to find the correct path.");
        }

        String content = IoUtils.readFile(eclipseFile);
        String newContent = applyEdit(filePath, content, oldString, newString);
        var result = writeEclipseFile(eclipseFile, newContent);
        var editResult = new AiFileUpdate(result.file(), oldString, newString);
        if (hasMonitor()) monitor.onFileUpdate(editResult);
        return "File " + editResult.file() + " edited successfully.";
    }

    private static String applyEdit(String filePath, String content, String oldString, String newString) {
        int count = 0;
        int idx = 0;
        while ((idx = content.indexOf(oldString, idx)) != -1) {
            count++;
            idx += oldString.length();
        }
        if (count == 0) {
            throw new IllegalArgumentException(
                    "old_string not found in " + filePath + ". Read the file first to verify the exact content.");
        }
        if (count > 1) {
            throw new IllegalArgumentException(
                    "old_string found " + count + " times in " + filePath
                            + ". Include more surrounding context to make the match unique.");
        }
        return content.replace(oldString, newString);
    }

    @Tool("Eclipse: Overwrite existing workspace file.")
    public String writeWorkspaceFile(
            @P("workspace-relative path") String filePath,
            @P("new content") String newContent) {

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

    @Tool("Eclipse: Create/overwrite workspace file. Creates parent dirs.")
    public String createWorkspaceFile(
            @P("workspace-relative path") String filePath,
            @P("file content") String content) {

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

    @Tool("Eclipse: Delete workspace file or directory recursively.")
    public String deleteWorkspaceResource(
            @P("workspace-relative path") String filePath) {

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
