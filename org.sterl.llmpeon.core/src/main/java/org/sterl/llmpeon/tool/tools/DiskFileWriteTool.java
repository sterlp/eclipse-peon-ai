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

    @Tool("Disk: Write file. Creates parent dirs and overwrites if exists.")
    public String writeDiskFile(@P(name = "filePath") String filePath, @P(name = "content") String content) {
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
            
            var msg = (existed ? "Updated" : "Created") + " file: " + workingDir.relativize(resolved);
            onTool(msg);
            return msg;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + filePath, e);
        }
    }

    @Tool("Disk: Delete file.")
    public String deleteDiskFile(@P(name = "filePath") String filePath) {
        ArgsUtil.requireNonBlank(filePath, "filePath");

        Path resolved = resolve(filePath);
        if (resolved == null || !Files.exists(resolved)) {
            return "File not found: `" + filePath + "` maybe already deleted?";
        }

        try {
            Files.delete(resolved);
            var msg = "Deleted file: " + workingDir.relativize(resolved);
            onTool(msg);
            return msg;
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete " + filePath, e);
        }
    }
    
    @Tool("Disk: Replace a single line by line number. newContent may span multiple lines.")
    public String replaceDiskLine(
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
            return "File " + filePath + " line " + line + " replaced.";
        } catch (IOException e) {
            throw new RuntimeException("Failed to edit " + filePath, e);
        }
    }

    @Tool("Disk: Replace exact string. Errors if 0 or >1 matches.")
    public String editDiskFile(@P(name = "filePath") String filePath, 
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

            return "File " + result.file() + " edited successfully.";
        } catch (IOException e) {
            throw new RuntimeException("Failed to edit " + filePath, e);
        }
    }

    private Path resolve(String path) {
        return FileUtils.resolve(workingDir, path);
    }
}
