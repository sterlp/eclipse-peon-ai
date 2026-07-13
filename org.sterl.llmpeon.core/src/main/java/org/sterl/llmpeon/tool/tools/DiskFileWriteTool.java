package org.sterl.llmpeon.tool.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.sterl.llmpeon.shared.AiMonitor.AiFileUpdate;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.FileLines;
import org.sterl.llmpeon.shared.FileUtils;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class DiskFileWriteTool extends AbstractTool {

    private Path workingDir;

    public DiskFileWriteTool(Path workingDir) {
        setWorkingDir(workingDir);
    }
    
    public DiskFileWriteTool(String workingDir) {
        setWorkingDir(workingDir);
    }

    @Override
    public boolean isEditTool() { return true; }

    public void setWorkingDir(Path workingDir) {
        this.workingDir = workingDir.toAbsolutePath().normalize();
    }
    
    public void setWorkingDir(String workingDir) {
        if (workingDir == null) return;
        setWorkingDir(Path.of(workingDir));
    }

    public Path getWorkingDir() {
        return workingDir;
    }

    @Tool("Write file. Creates parent dirs and overwrites if exists.")
    public void diskWriteFile(@P(name = "filePath") String filePath, @P(name = "content") String content) {
        ArgsUtil.requireNonBlank(filePath, "filePath");
        ArgsUtil.requireNonNull(content, "content");

        Path resolved = resolve(filePath);
        if (resolved == null) {
            throw new IllegalArgumentException("Cannot resolve path: " + filePath);
        }

        try {
            boolean existed = Files.exists(resolved);
            String oldContent = existed ? Files.readString(resolved) : "";
            Files.createDirectories(resolved.getParent());
            Files.writeString(resolved, content);

            if (existed) {
                monitor.onFileUpdate(new AiFileUpdate(workingDir.relativize(resolved).toString(), oldContent, content));
            }
            
            onTool((existed ? "Updated" : "Created") + " file: " + workingDir.relativize(resolved));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + filePath, e);
        }
    }

    @Tool("Delete file.")
    public void diskDeleteFile(@P(name = "filePath") String filePath) {
        ArgsUtil.requireNonBlank(filePath, "filePath");

        Path resolved = resolve(filePath);
        if (resolved == null || !Files.exists(resolved)) {
            throw new IllegalArgumentException("File not found: `" + filePath + "` maybe already deleted?");
        }

        try {
            Files.delete(resolved);
            onTool("Deleted file: " + workingDir.relativize(resolved));
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete " + filePath, e);
        }
    }
    
    @Tool("Replace lines by line number. newContent may span multiple lines.")
    public void diskReplaceLines(
            @P(name = "filePath") String filePath,
            @P("line to replace (1-based)") Integer line,
            @P(name = "newContent") String newContent) {

        ArgsUtil.requireNonBlank(filePath, "filePath");
        ArgsUtil.requireNonNull(line, "line");
        ArgsUtil.requireNonNull(newContent, "newContent");

        Path resolved = resolve(filePath);
        if (resolved == null || !Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException("File not found: " + filePath);
        }
        try {
            String content = Files.readString(resolved);
            String newFullContent = FileLines.replaceLines(content, line, line, newContent);
            Files.writeString(resolved, newFullContent);
            monitor.onFileUpdate(new AiFileUpdate(workingDir.relativize(resolved).toString(), content, newFullContent));
        } catch (IOException e) {
            throw new RuntimeException("Failed to edit " + filePath, e);
        }
    }

    @Tool("Replace exact string. Errors if 0 or >1 matches.")
    public void diskEditFile(@P(name = "filePath") String filePath, 
            @P(description = "exact string to replace", name = "oldString") String oldString, 
            @P(name = "newString") String newString) {

        ArgsUtil.requireNonBlank(filePath, "filePath");
        ArgsUtil.requireNonBlank(oldString, "oldString");
        ArgsUtil.requireNonNull(newString, "newString");

        if (oldString.equals(newString)) {
            throw new IllegalArgumentException("oldString and newString are identical - nothing to change");
        }

        Path resolved = resolve(filePath);
        if (resolved == null || !Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException(
                    "File not found: " + filePath + ". Use searchDiskFiles to find the correct path.");
        }

        try {
            String content = Files.readString(resolved);
            String newContent = FileUtils.applyEdit(filePath, content, oldString, newString);
            Files.writeString(resolved, newContent);

            var result = new AiFileUpdate(workingDir.relativize(resolved).toString(), oldString, newString);
            monitor.onFileUpdate(result);

        } catch (IOException e) {
            throw new RuntimeException("Failed to edit " + filePath, e);
        }
    }

    @Tool("Rename or move a file or directory. Creates target parent folders.")
    public void diskRenameResource(
            @P(name = "sourcePath") String sourcePath,
            @P(name = "targetPath") String targetPath) {

        ArgsUtil.requireNonBlank(sourcePath, "sourcePath");
        ArgsUtil.requireNonBlank(targetPath, "targetPath");

        Path source = resolve(sourcePath);
        if (source == null || !Files.exists(source)) {
            throw new IllegalArgumentException("Not found: " + sourcePath);
        }
        Path target = resolve(targetPath);
        if (target == null) {
            throw new IllegalArgumentException("Cannot resolve path: " + targetPath);
        }
        if (Files.exists(target)) {
            throw new IllegalArgumentException("Target already exists: " + targetPath);
        }
        try {
            if (target.getParent() != null) Files.createDirectories(target.getParent());
            Files.move(source, target);
            onTool("Renamed " + workingDir.relativize(source) + " -> " + workingDir.relativize(target));
        } catch (IOException e) {
            throw new RuntimeException("Failed to rename " + sourcePath + " -> " + targetPath, e);
        }
    }

    @Tool("Insert text into a file at a specific position. Omit afterLine to append at end. 0 inserts before the first line (prepend). 1..n inserts after that line.")
    public void diskInsertLines(
            @P(name = "filePath") String filePath,
            @P(description = "1-based line to insert after; omit to append, 0 to prepend",
               name = "afterLine", required = false) Integer afterLine,
            @P(description = "text to insert (may span multiple lines)", name = "newContent") String newContent) {

        ArgsUtil.requireNonBlank(filePath, "filePath");
        ArgsUtil.requireNonNull(newContent, "newContent");

        Path resolved = resolve(filePath);
        if (resolved == null || !Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException("File not found: " + filePath);
        }
        try {
            String content = Files.readString(resolved);
            String newFullContent = FileLines.insertLines(content, afterLine, newContent);
            Files.writeString(resolved, newFullContent);
            monitor.onFileUpdate(new AiFileUpdate(workingDir.relativize(resolved).toString(), content, newFullContent));
        } catch (IOException e) {
            throw new RuntimeException("Failed to edit " + filePath, e);
        }
    }

    private Path resolve(String path) {
        return FileUtils.resolve(workingDir, path);
    }
}
