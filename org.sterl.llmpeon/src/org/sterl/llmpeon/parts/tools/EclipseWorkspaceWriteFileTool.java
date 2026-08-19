package org.sterl.llmpeon.parts.tools;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;
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
    
    @Tool("Updates the content of current open eclipse workspace file - using the user access e.g. to help during coding or in edge cases e.g. ABAB the only way to change/checkout and lock")
    public String eclipseUpdateOpenFile(
            @P(description = "exact text to replace", name = "oldString", required = false) String inOldString,
            @P(name = "newString", required = false) String inNewString) {
        
        final CompletableFuture<String> result = new CompletableFuture<String>();

        if (inNewString == null && inOldString == null) throw new IllegalArgumentException("Provide a now or old string!");

        PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
            var newString = inNewString == null ? "" : inNewString;
            var oldString = inOldString == null ? "" : inOldString;
            

            onTool("Edit in editor");
            
            var e = EclipseUtil.getOpenEditor();
            if (e.isEmpty()) {
                result.complete("Nothing currently open.");
            } else {
                var editor = e.get();
                if (editor instanceof ITextEditor text) {
                    var openFile = EclipseUtil.getOpenFile();
                    var path = openFile.isPresent() ? JdtUtil.pathOf(openFile.get()) : "Open in editor";
                    validateWrite(path);
                    
                    IDocumentProvider provider = text.getDocumentProvider();
                    IDocument document = provider.getDocument(text.getEditorInput());
                    
                    var oldDoc = document.get();
                    var newDoc = FileUtils.applyEdit(path, document.get(), oldString, newString);
                    document.set(newDoc);
                    
                    var success = "Saved!";
                    if (!PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().saveEditor(editor, false)) {
                        if (!PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().saveEditor(editor, true)) {
                            success = "Save failed! Ask user to save editor.";
                        }
                    }
                    monitor.onFileUpdate(new AiFileUpdate(path, oldDoc, newDoc));
                    result.complete(openFile.isPresent() ? success + " of " + JdtUtil.pathOf(openFile.get()) : success);
                    
                } else {
                    throw new IllegalArgumentException("Cannot read from unknown editor " + editor.getClass().getName());
                }
            }
        });
        
        try {
            return result.get(2, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new IllegalStateException("Timeout during eclipse editor read", e);
        }

    }

    @Tool("Replace a single line in a workspace file by 1-based line number. newContent may span multiple lines.")
    public void eclipseReplaceLines(
            @P(description = "workspace-relative path", name = "filePath") String filePath,
            @P(description = "line to replace (1-based)", name = "line") Integer line,
            @P(description = "replacement text", name ="newContent") String newContent) {

        ArgsUtil.requireNonBlank(filePath, "filePath");
        ArgsUtil.requireNonNull(line, "line");
        ArgsUtil.requireNonNull(newContent, "newContent");
        validateWrite(filePath);

        var inFile = EclipseUtil.resolveInEclipse(filePath);
        if (inFile.isEmpty() || !(inFile.get() instanceof IFile eclipseFile)) {
            throw new IllegalArgumentException("Cannot write unknown file in eclipse " + filePath);
        }
        String content = readFile(eclipseFile);
        String newFullContent = FileLines.replaceLines(content, line, line, newContent);

        IoUtils.writeFile(eclipseFile, newFullContent, getProgressMonitor());
        monitor.onFileUpdate(new AiFileUpdate(JdtUtil.pathOf(eclipseFile), content, newFullContent));
    }

    private String readFile(IFile eclipseFile) {
        try {
            return eclipseFile.readString();
        } catch (CoreException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Tool("Replace the first occurrence of an exact string in a workspace file. newString=null deletes the match.")
    public void eclipseEditFile(
            @P(description = "workspace-relative path", name = "filePath") String filePath,
            @P(description = "exact text to replace", name = "oldString", required = false) String oldString,
            @P(name = "newString", required = false) String newString) {

        if (newString == null && oldString == null) throw new IllegalArgumentException("Provide a now or old string!");

        validateWrite(filePath);
        if (newString == null) newString = "";
        if (oldString == null) oldString = "";

        var inFile = EclipseUtil.resolveInEclipse(filePath);
        if (inFile.isEmpty() || !(inFile.get() instanceof IFile eclipseFile)) {
            throw new IllegalArgumentException("Cannot write unknown file in eclipse " + filePath);
        } else {
            String content = readFile(eclipseFile);
            String newFullContent = FileUtils.applyEdit(filePath, content, oldString, newString);

            IoUtils.writeFile(eclipseFile, newFullContent, getProgressMonitor());
            monitor.onFileUpdate(new AiFileUpdate(JdtUtil.pathOf(eclipseFile), content, newFullContent));
        }
    }

    @Tool("Write file to workspace. Creates parent dirs and overwrites if exists.")
    public void eclipseWriteFile(
            @P(description = "workspace-relative path", name = "filePath") 
            String filePath,
            @P(name = "content") 
            String content) {

        ArgsUtil.requireNonBlank(filePath, "filePath");
        ArgsUtil.requireNonNull(content, "content");
        validateWrite(filePath);

        var inFile = EclipseUtil.resolveInEclipse(filePath);
        if (inFile.isPresent() && inFile.get() instanceof IFile eclipseFile) {
            IoUtils.writeFile(eclipseFile, content, getProgressMonitor());
            onTool("Overwrite file " + JdtUtil.pathOf(eclipseFile));
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
            throw new IllegalArgumentException(
                    "Cannot determine target project for path: " + filePath 
                    + ". Open projects: [" + openProjects + "]");
        }

        IFile file = writeFileToProject(targetProject.get(), projectRelativePath, content);
        onTool("Created file " + JdtUtil.pathOf(file));
    }

    @Tool("precise, line-targeted updates/insert into a file at a specific position. Omit afterLine to append at end. 0 inserts before the first line (prepend). 1..n inserts after that line.")
    public void eclipseInsertLines(
            @P(description = "workspace-relative path", name = "filePath") String filePath,
            @P(description = "1-based line to insert after; omit to append, 0 to prepend",
               name = "afterLine", required = false) Integer afterLine,
            @P(description = "text to insert (may span multiple lines)", name = "newContent") String newContent) {

        ArgsUtil.requireNonBlank(filePath, "filePath");
        ArgsUtil.requireNonNull(newContent, "newContent");
        validateWrite(filePath);

        var inFile = EclipseUtil.resolveInEclipse(filePath);
        if (inFile.isEmpty() || !(inFile.get() instanceof IFile eclipseFile)) {
            throw new IllegalArgumentException("Cannot write unknown file in eclipse " + filePath);
        }
        String content = readFile(eclipseFile);
        String newFullContent = FileLines.insertLines(content, afterLine, newContent);
        
        IoUtils.writeFile(eclipseFile, newFullContent, getProgressMonitor());
        monitor.onFileUpdate(new AiFileUpdate(JdtUtil.pathOf(eclipseFile), content, newFullContent));
    }

    @Tool("Rename or move a workspace file or directory. Creates target parent folders.")
    public void eclipseRenameResource(
            @P(description = "existing workspace-relative path", name = "sourcePath") String sourcePath,
            @P(description = "new workspace-relative path", name = "targetPath") String targetPath) {

        ArgsUtil.requireNonBlank(sourcePath, "sourcePath");
        ArgsUtil.requireNonBlank(targetPath, "targetPath");
        validateWrite(sourcePath);
        validateWrite(targetPath);

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

    @Tool("Delete workspace file or directory recursively.")
    public String eclipseDeleteResource(
            @P(description = "workspace-relative path", name = "filePath") String filePath) {

        ArgsUtil.requireNonBlank(filePath, "filePath");
        validateWrite(filePath);

        var file = EclipseUtil.resolveInEclipse(filePath);
        if (file.isEmpty()) return "Not found: " + filePath;

        try {
            try {
                file.get().delete(IResource.KEEP_HISTORY, getProgressMonitor());
            } catch (Exception e) {
                file.get().delete(IResource.FORCE, getProgressMonitor());
            }
            onTool("Deleting " + JdtUtil.pathOf(file.get()));
            return "Deleted";
        } catch (CoreException e) {
            throw new RuntimeException("Failed to delete " + filePath, e);
        }
    }

    private IFile writeFileToProject(IProject targetProject, String projectRelativePath, String content) {
        return IoUtils.writeProjectFile(targetProject, projectRelativePath, content, getProgressMonitor());
    }
}
