package org.sterl.llmpeon.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.sterl.llmpeon.agent.AiMonitor.AiFileUpdate;
import org.sterl.llmpeon.shared.FileUtils;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class DiskFileWriteTools extends AbstractTool {

    private Path workingDir;

    public DiskFileWriteTools(Path workingDir) {
        this.workingDir = workingDir.toAbsolutePath().normalize();
    }

    @Override
    public boolean isEditTool() { return true; }

    public void setWorkingDir(Path workingDir) {
        this.workingDir = workingDir.toAbsolutePath().normalize();
    }

    public Path getWorkingDir() {
        return workingDir;
    }

    @Tool("Disk: Overwrite existing file.")
    public String writeDiskFile(@P("file path") String filePath, @P("new content") String newContent) {

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be empty");
        }
        if (newContent == null || newContent.isBlank()) {
            throw new IllegalArgumentException("newContent must not be empty");
        }

        Path resolved = resolve(filePath);
        if (resolved == null || !Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException(
                    "File not found: " + filePath + ". Use searchDiskFiles to find the correct path.");
        }
        monitorMessage("Writing " + filePath);
        try {
            String oldContent = Files.readString(resolved);
            Files.writeString(resolved, newContent);
            var result = new AiFileUpdate(workingDir.relativize(resolved).toString(), oldContent, newContent);
            if (hasMonitor()) monitor.onFileUpdate(result);
            return "File " + result.file() + " updated.";
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + filePath, e);
        }
    }

    @Tool("Disk: Create/overwrite file. Creates parent dirs.")
    public String createDiskFile(@P("file path") String filePath, @P("file content") String content) {

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be empty");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be empty");
        }

        Path resolved = resolve(filePath);
        if (resolved == null) {
            throw new IllegalArgumentException("Cannot resolve path: " + filePath);
        }
        monitorMessage("Creating " + filePath);
        try {
            boolean existed = Files.exists(resolved);
            Files.createDirectories(resolved.getParent());
            Files.writeString(resolved, content);
            return (existed ? "Updated" : "Created") + " file: " + workingDir.relativize(resolved);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create " + filePath, e);
        }
    }

    @Tool("Disk: Delete file.")
    public String deleteDiskFile(@P("file path") String filePath) {

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be empty");
        }

        Path resolved = resolve(filePath);
        if (resolved == null || !Files.exists(resolved)) {
            onProblem("File not found: " + filePath);
            return "File not found: " + filePath;
        }
        monitorMessage("Deleting " + filePath);
        try {
            Files.delete(resolved);
            return "Deleted file: " + workingDir.relativize(resolved);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete " + filePath, e);
        }
    }

    private Path resolve(String path) {
        return FileUtils.resolve(workingDir, path);
    }
}
