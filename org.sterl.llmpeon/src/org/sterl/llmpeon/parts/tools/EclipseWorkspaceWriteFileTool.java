package org.sterl.llmpeon.parts.tools;

import java.nio.charset.Charset;
import java.nio.file.Files;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.shared.AiMonitor.AiFileUpdate;
import org.sterl.llmpeon.shared.FileLines;
import org.sterl.llmpeon.shared.FileUtils;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EclipseWorkspaceWriteFileTool extends AbstractEclipseTool {

    private IProject currentProject;

    public void setCurrentProject(IProject project) {
        this.currentProject = project;
    }

    @Override
    public boolean isEditTool() {
        return true;
    }

    @Tool("Eclipse: Replace a single line by line number. newContent may span multiple lines.")
    public String replaceWorkspaceLine(
            @P("workspace-relative path") String filePath,
            @P("line to replace (1-based)") int line,
            @P("replacement text") String newContent) {

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be empty");
        }

        var inFile = EclipseUtil.resolveInEclipse(filePath);
        if (inFile.isEmpty() || !(inFile.get() instanceof IFile eclipseFile)) {
            throw new IllegalArgumentException("Cannot write unknown file in eclipse " + filePath);
        }
        String content = IoUtils.readFile(eclipseFile);
        String newFullContent = FileLines.replaceLines(content, line, line, newContent);
        var result = writeEclipseFile(eclipseFile, newFullContent);
        monitor.onFileUpdate(result);
        return "File " + result.file() + " line " + line + " replaced.";
    }

    @Tool("Eclipse: Replace exact string in workspace file. Errors if 0 or >1 matches.")
    public String editWorkspaceFile(
            @P("workspace-relative path") String filePath,
            @P("exact text to replace") String oldString,
            @P("new replacement text") String newString) {

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
            throw new IllegalArgumentException("Cannot write unknown file in eclipse " + filePath);
        } else {
            String content = IoUtils.readFile(eclipseFile);
            String newContent = FileUtils.applyEdit(filePath, content, oldString, newString);
            var result = writeEclipseFile(eclipseFile, newContent);
            var editResult = new AiFileUpdate(result.file(), oldString, newString);
            
            monitor.onFileUpdate(editResult);
            return "File " + editResult.file() + " edited successfully.";
        }

    }

    @Tool("Eclipse: Overwrite existing workspace file.")
    public String writeWorkspaceFile(
            @P("workspace-relative path") String filePath,
            @P("new content") String newContent) {

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("workspace-relative path must not be empty");
        }
        if (newContent == null || newContent.isBlank()) {
            throw new IllegalArgumentException("new content must not be empty");
        }

        var inFile = EclipseUtil.resolveInEclipse(filePath);
        if (inFile.isEmpty() || !(inFile.get() instanceof IFile)) {
            // fallback to raw file system
            var f = java.nio.file.Path.of(filePath);
            if (Files.isRegularFile(f)) {
                String oldContent = FileUtils.readString(f);
                FileUtils.writeString(f, newContent);
                
                monitor.onFileUpdate(new AiFileUpdate(filePath, oldContent, newContent));
                return "File " + filePath + " updated.";
            }
            throw new IllegalArgumentException(
                    "File not found: " + filePath + ". Use searchWorkspaceFiles to find the correct path.");
        } else {
            var result = writeEclipseFile((IFile) inFile.get(), newContent);
            
            monitor.onFileUpdate(result);
            return "File " + result.file() + " updated.";
        }
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
        onTool("Created file " + JdtUtil.pathOf(file));
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
            file.write(content.getBytes(charset), true, false, true, getProgressMonitor());
            file.refreshLocal(IResource.DEPTH_ZERO, getProgressMonitor());
        } catch (CoreException e) {
            throw new RuntimeException("Failed to write " + JdtUtil.pathOf(file), e);
        }
        return new AiFileUpdate(JdtUtil.pathOf(file), oldContent, content);
    }

    private IFile writeFileToProject(IProject targetProject, String projectRelativePath, String content) {
        return IoUtils.writeProjectFile(targetProject, projectRelativePath, content, getProgressMonitor());
    }
}
