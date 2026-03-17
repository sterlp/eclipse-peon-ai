package org.sterl.llmpeon.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.sterl.llmpeon.agent.AiMonitor.AiFileUpdate;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class EditTool extends AbstractTool {

    private Path workingDir;

    public EditTool(Path workingDir) {
        this.workingDir = workingDir.toAbsolutePath().normalize();
    }

    @Override
    public boolean isEditTool() { return true; }

    public void setWorkingDir(Path workingDir) {
        this.workingDir = workingDir.toAbsolutePath().normalize();
    }

    @Tool("Disk: Replace exact string. Match must be unique.")
    public String editDiskFile(@P("file path") String filePath, @P("exact string to replace") String oldString, @P("replacement text") String newString) {

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

        Path resolved = resolve(filePath);
        if (resolved == null || !Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException(
                    "File not found: " + filePath + ". Use searchDiskFiles to find the correct path.");
        }

        monitorMessage("Editing " + filePath);
        try {
            String content = Files.readString(resolved);

            // Count occurrences
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

            String newContent = content.replace(oldString, newString);
            Files.writeString(resolved, newContent);

            var result = new AiFileUpdate(workingDir.relativize(resolved).toString(), oldString, newString);
            if (hasMonitor()) monitor.onFileUpdate(result);

            return "File " + result.file() + " edited successfully.";
        } catch (IOException e) {
            throw new RuntimeException("Failed to edit " + filePath, e);
        }
    }

    private Path resolve(String path) {
        if (path == null) return null;
        Path p = Path.of(path);
        if (p.isAbsolute()) return p.normalize();
        return workingDir.resolve(p).normalize();
    }
}
