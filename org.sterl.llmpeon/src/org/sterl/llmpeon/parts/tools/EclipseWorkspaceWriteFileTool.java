package org.sterl.llmpeon.parts.tools;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.sterl.llmpeon.parts.shared.EclipseUtil;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.shared.AiMonitor.AiFileUpdate;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.FileLines;
import org.sterl.llmpeon.shared.FileUtils;
import org.sterl.llmpeon.shared.QualifiedPathValidator;

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
        

        if (inNewString == null && inOldString == null) throw new IllegalArgumentException("Provide a now or old string!");

        CompletableFuture<String> result = EclipseUtil.runInUiThread(() -> {
            var newString = inNewString == null ? "" : inNewString;
            var oldString = inOldString == null ? "" : inOldString;
            

            onTool("Edit in editor");
            
            var e = EclipseUtil.getOpenEditor();
            if (e.isEmpty()) return "Nothing currently open.";

            var text = EclipseUtil.getTextEditor(e.get());
            var openFile = EclipseUtil.getOpenFile();
            var path = openFile.isPresent() ? JdtUtil.pathOf(openFile.get()) : "Open in editor";
            validateWrite(path);
            
            IDocumentProvider provider = text.getDocumentProvider();
            IDocument document = provider.getDocument(text.getEditorInput());
            
            var oldDoc = document.get();
            var edit = FileUtils.applyEdit(path, document.get(), oldString, newString);
            document.set(edit.content());
            
            var success = "Saved!";
            if (!PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().saveEditor(text, false)) {
                if (!PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().saveEditor(text, true)) {
                    success = "Save failed! Ask user to save editor.";
                }
            }
            monitor.onFileUpdate(new AiFileUpdate(path, oldDoc, edit.content()));
            var verb = newString.isEmpty() ? "deleted" : "replaced";
            var countMsg = " — " + verb + " " + edit.count() + " occurrence(s)";
            return openFile.isPresent() ? success + " of " + JdtUtil.pathOf(openFile.get()) + countMsg : success + countMsg;
                
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

    @Tool("Replace all occurrences of an exact string in a workspace file; reports how many were replaced. newString=null deletes the matches.")
    public String eclipseEditFile(
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
            var edit = FileUtils.applyEdit(filePath, content, oldString, newString);

            IoUtils.writeFile(eclipseFile, edit.content(), getProgressMonitor());
            monitor.onFileUpdate(new AiFileUpdate(JdtUtil.pathOf(eclipseFile), content, edit.content()));

            var verb = newString.isEmpty() ? "deleted" : "replaced";
            return verb + " " + edit.count() + " occurrence(s) in " + JdtUtil.pathOf(eclipseFile);
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
    @Tool("Rename or move a workspace file or directory. Creates target parent folders. Paths must be workspace-qualified: /project/path.")
    public String eclipseRenameResource(
            @P(description = "existing source path, workspace-qualified as /project/path", name = "sourcePath") String sourcePath,
            @P(description = "new target path, workspace-qualified as /project/path", name = "targetPath") String targetPath) {

        ArgsUtil.requireNonBlank(sourcePath, "sourcePath");
        ArgsUtil.requireNonBlank(targetPath, "targetPath");
        QualifiedPathValidator.requireQualifiedEclipse("Rename", sourcePath, EclipseUtil::isExistingProject);
        QualifiedPathValidator.requireQualifiedEclipse("Rename", targetPath, EclipseUtil::isExistingProject);
        validateWrite(sourcePath);
        validateWrite(targetPath);

        var source = EclipseUtil.resolveInEclipse(sourcePath);
        if (source.isEmpty()) throw new IllegalArgumentException("Not found: " + sourcePath);

        var resource = source.get();
        if (EclipseUtil.resolveInEclipse(targetPath).isPresent()) {
            throw new IllegalArgumentException("Target already exists: " + targetPath);
        }

        var workspaceRoot = resource.getWorkspace().getRoot();
        IPath destPath = workspaceRoot.getFullPath().append(IPath.fromPortableString(targetPath.substring(1)));

        try {
            ensureParentFolders(workspaceRoot, destPath);
            resource.move(destPath, IResource.KEEP_HISTORY, getProgressMonitor());
            var result = "Renamed " + JdtUtil.pathOf(resource) + " -> " + destPath.toPortableString();
            onTool(result);
            return result;
        } catch (CoreException e) {
            throw new RuntimeException("Failed to rename " + sourcePath + " -> " + targetPath, e);
        }
    }
    @Tool("Copy a workspace file to a new location. Creates target parent folders. The source is kept. Paths must be workspace-qualified: /project/path.")
    public String eclipseCopyFile(
            @P(description = "existing source path, workspace-qualified as /project/path", name = "sourcePath") String sourcePath,
            @P(description = "target path, workspace-qualified as /project/path", name = "targetPath") String targetPath) {

        ArgsUtil.requireNonBlank(sourcePath, "sourcePath");
        ArgsUtil.requireNonBlank(targetPath, "targetPath");
        QualifiedPathValidator.requireQualifiedEclipse("Copy", sourcePath, EclipseUtil::isExistingProject);
        QualifiedPathValidator.requireQualifiedEclipse("Copy", targetPath, EclipseUtil::isExistingProject);
        validateWrite(sourcePath);
        validateWrite(targetPath);

        var source = EclipseUtil.resolveInEclipse(sourcePath);
        if (source.isEmpty()) throw new IllegalArgumentException("Not found: " + sourcePath);

        var resource = source.get();
        if (!(resource instanceof IFile)) throw new IllegalArgumentException("Not a file: " + sourcePath);
        if (EclipseUtil.resolveInEclipse(targetPath).isPresent()) {
            throw new IllegalArgumentException("Target already exists: " + targetPath);
        }

        var workspaceRoot = resource.getWorkspace().getRoot();
        IPath destPath = workspaceRoot.getFullPath().append(IPath.fromPortableString(targetPath.substring(1)));

        try {
            ensureParentFolders(workspaceRoot, destPath);
            resource.copy(destPath, IResource.KEEP_HISTORY, getProgressMonitor());
            var result = "Copied " + JdtUtil.pathOf(resource) + " -> " + destPath.toPortableString();
            onTool(result);
            return result;
        } catch (CoreException e) {
            throw new RuntimeException("Failed to copy " + sourcePath + " -> " + targetPath, e);
        }
    }

    /**
     * Creates the target parent folders. The parent may be the project itself (single
     * segment) — nothing to create, and {@code getFolder} requires at least two segments.
     */
    private void ensureParentFolders(IWorkspaceRoot root, IPath destPath) throws CoreException {
        var parentPath = destPath.removeLastSegments(1);
        if (parentPath.segmentCount() > 1) {
            var parent = root.getFolder(parentPath);
            if (!parent.exists()) IoUtils.ensureFolders(parent, getProgressMonitor());
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
