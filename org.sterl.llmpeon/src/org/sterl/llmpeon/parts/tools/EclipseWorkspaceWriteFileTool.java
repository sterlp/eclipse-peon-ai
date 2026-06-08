package org.sterl.llmpeon.parts.tools;

import java.nio.charset.Charset;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.shared.AiMonitor.AiFileUpdate;
import org.sterl.llmpeon.shared.ArgsUtil;
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
    public void replaceWorkspaceLine(
            @P(description = "workspace-relative path", name = "filePath") String filePath,
            @P(description = "line to replace (1-based)", name = "line") Integer line,
            @P(description = "replacement text", name ="newContent") String newContent) {

        ArgsUtil.requireNonBlank(filePath, "filePath");
        ArgsUtil.requireNonNull(line, "line");
        ArgsUtil.requireNonNull(newContent, "newContent");

        var inFile = EclipseUtil.resolveInEclipse(filePath);
        if (inFile.isEmpty() || !(inFile.get() instanceof IFile eclipseFile)) {
            throw new IllegalArgumentException("Cannot write unknown file in eclipse " + filePath);
        }
        String content = readFile(eclipseFile);
        String newFullContent = FileLines.replaceLines(content, line, line, newContent);
        var result = writeEclipseFile(eclipseFile, newFullContent);
        monitor.onFileUpdate(result);
    }

    private String readFile(IFile eclipseFile) {
        try {
            return eclipseFile.readString();
        } catch (CoreException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Tool("Eclipse: Replace exact string in workspace file. Errors if 0 or >1 matches.")
    public void editWorkspaceFile(
            @P(description = "workspace-relative path", name = "filePath") String filePath,
            @P(description = "exact text to replace", name = "oldString") String oldString,
            @P(name = "newString") String newString) {

        ArgsUtil.requireNonBlank(filePath, "filePath");
        ArgsUtil.requireNonBlank(oldString, "oldString");
        ArgsUtil.requireNonNull(newString, "newString");

        var inFile = EclipseUtil.resolveInEclipse(filePath);
        if (inFile.isEmpty() || !(inFile.get() instanceof IFile eclipseFile)) {
            throw new IllegalArgumentException("Cannot write unknown file in eclipse " + filePath);
        } else {
            String content = readFile(eclipseFile);
            String newContent = FileUtils.applyEdit(filePath, content, oldString, newString);
            var result = writeEclipseFile(eclipseFile, newContent);
            var editResult = new AiFileUpdate(result.file(), oldString, newString);
            
            monitor.onFileUpdate(editResult);
        }
    }

    @Tool("Eclipse: Write file to workspace. Creates parent dirs and overwrites if exists.")
    public void writeWorkspaceFile(
            @P(description = "workspace-relative path", name = "filePath") 
            String filePath,
            @P(name = "content") 
            String content) {

        ArgsUtil.requireNonBlank(filePath, "filePath");
        ArgsUtil.requireNonNull(content, "content");

        var inFile = EclipseUtil.resolveInEclipse(filePath);
        if (inFile.isPresent() && inFile.get() instanceof IFile eclipseFile) {
            var result = writeEclipseFile(eclipseFile, content);
            monitor.onFileUpdate(result);
            onTool("Updated file " + JdtUtil.pathOf(eclipseFile));
            return;
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
            String openProjects = EclipseUtil.openProjects().stream()
                    .map(p -> "/" + p.getName())
                    .collect(java.util.stream.Collectors.joining(", "));
            onProblem("Create File: unknown path for file: " + filePath);
            throw new IllegalArgumentException(
                    "Cannot determine target project for path: " + filePath 
                    + ". Open projects: [" + openProjects + "]");
        }

        IFile file = writeFileToProject(targetProject.get(), projectRelativePath, content);
        onTool("Created file " + JdtUtil.pathOf(file));
    }

    @Tool("Eclipse: Insert text after a 1-based line. If afterLine is omitted/0, appends at end of file.")
    public void insertWorkspaceLines(
            @P(description = "workspace-relative path", name = "filePath") String filePath,
            @P(description = "text to insert (may span multiple lines)", name = "newContent") String newContent,
            @P(description = "1-based line to insert after; omit or 0 to append at end of file",
               name = "afterLine", required = false) Integer afterLine) {

        ArgsUtil.requireNonBlank(filePath, "filePath");
        ArgsUtil.requireNonNull(newContent, "newContent");

        var inFile = EclipseUtil.resolveInEclipse(filePath);
        if (inFile.isEmpty() || !(inFile.get() instanceof IFile eclipseFile)) {
            throw new IllegalArgumentException("Cannot write unknown file in eclipse " + filePath);
        }
        String content = readFile(eclipseFile);
        String newFullContent = FileLines.insertLines(content, afterLine, newContent);
        var result = writeEclipseFile(eclipseFile, newFullContent);
        monitor.onFileUpdate(result);
    }

    @Tool("Eclipse: Rename or move a workspace file or directory. Creates target parent folders.")
    public void renameWorkspaceResource(
            @P(description = "existing workspace-relative path", name = "sourcePath") String sourcePath,
            @P(description = "new workspace-relative path", name = "targetPath") String targetPath) {

        ArgsUtil.requireNonBlank(sourcePath, "sourcePath");
        ArgsUtil.requireNonBlank(targetPath, "targetPath");

        var source = EclipseUtil.resolveInEclipse(sourcePath);
        if (source.isEmpty()) throw new IllegalArgumentException("Not found: " + sourcePath);

        var resource = source.get();
        if (EclipseUtil.resolveInEclipse(targetPath).isPresent()) {
            throw new IllegalArgumentException("Target already exists: " + targetPath);
        }

        var workspaceRoot = resource.getWorkspace().getRoot();
        org.eclipse.core.runtime.IPath destPath = resource.getFullPath()
                .removeLastSegments(resource.getFullPath().segmentCount())
                .append(org.eclipse.core.runtime.IPath.fromPortableString(
                        targetPath.startsWith("/") ? targetPath.substring(1) : targetPath));

        try {
            var parent = workspaceRoot.getFolder(destPath.removeLastSegments(1));
            if (!destPath.removeLastSegments(1).isEmpty() && !parent.exists()
                    && destPath.segmentCount() > 2) {
                IoUtils.ensureFolders(parent, getProgressMonitor());
            }
            resource.move(destPath, IResource.KEEP_HISTORY, getProgressMonitor());
            onTool("Renamed " + sourcePath + " -> " + destPath.toPortableString());
        } catch (CoreException e) {
            throw new RuntimeException("Failed to rename " + sourcePath + " -> " + targetPath, e);
        }
    }

    @Tool("Eclipse: Delete workspace file or directory recursively.")
    public void deleteWorkspaceResource(
            @P(description = "workspace-relative path", name = "filePath") String filePath) {

        ArgsUtil.requireNonBlank(filePath, "filePath");

        var file = EclipseUtil.resolveInEclipse(filePath);
        if (file.isEmpty()) throw new IllegalArgumentException("Not found: " + filePath);

        try {
            try {
                file.get().delete(IResource.KEEP_HISTORY, getProgressMonitor());
            } catch (Exception e) {
                file.get().delete(IResource.FORCE, getProgressMonitor());
            }
            onTool("Deleting " + JdtUtil.pathOf(file.get()));
        } catch (CoreException e) {
            throw new RuntimeException("Failed to delete " + filePath, e);
        }
    }

    private AiFileUpdate writeEclipseFile(IFile file, String content) {
        var oldContent = readFile(file);
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
