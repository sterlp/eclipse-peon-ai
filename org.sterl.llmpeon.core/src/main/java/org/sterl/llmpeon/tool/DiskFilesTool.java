package org.sterl.llmpeon.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.sterl.llmpeon.agent.AiMonitor.AiFileUpdate;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class DiskFilesTool extends AbstractTool {

    private Path workingDir;

    public DiskFilesTool(Path workingDir) {
        this.workingDir = workingDir.toAbsolutePath().normalize();
    }

    public void setWorkingDir(Path workingDir) {
        this.workingDir = workingDir.toAbsolutePath().normalize();
    }

    public Path getWorkingDir() {
        return workingDir;
    }

    @Tool("Reads a file from the disk filesystem using the current working directory. "
            + "Use searchDiskFiles first to find the file path.")
    public String readDiskFile(
            @P("Relative or absolute file path") String filePath) {

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be empty");
        }

        Path resolved = resolve(filePath);
        if (resolved == null || !Files.isRegularFile(resolved)) {
            onProblem("File not found: " + filePath);
            return "File not found: " + filePath;
        }
        monitorMessage("Reading " + filePath);
        try {
            return Files.readString(resolved);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + filePath, e);
        }
    }

    @Tool("Updates the complete content of an existing file on the disk filesystem. "
            + "Only works for files that already exist - does not create new files. "
            + "Use searchDiskFiles first to find the correct path.")
    public String writeDiskFile(
            @P("Relative or absolute file path") String filePath,
            @P("The complete new file content") String newContent) {

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

    @Tool("Creates a new file or overwrites an existing one on the disk filesystem. "
            + "Missing parent folders are created automatically.")
    public String createDiskFile(
            @P("Relative or absolute file path including file name, e.g. 'src/com/example/Foo.java'") String filePath,
            @P("File content to write") String content) {

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

    @Tool("Deletes a file at the given path on the disk filesystem. "
            + "Use searchDiskFiles first to find the correct path.")
    public String deleteDiskFile(
            @P("Relative or absolute file path") String filePath) {

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

    @Tool("Searches for files whose name contains the given query string on the disk filesystem.")
    public String searchDiskFiles(
            @P("Part of the file name to search for, e.g. 'Controller' or '.xml'") String query) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be empty");
        }
        monitorMessage("Searching for " + query);

        String lowerQuery = query.toLowerCase();
        var matches = new ArrayList<String>();
        try (var walk = Files.walk(workingDir)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase().contains(lowerQuery))
                .forEach(p -> matches.add(p.toAbsolutePath().toString()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to search in " + workingDir, e);
        }

        if (matches.isEmpty()) {
            return "No files found matching '" + query + "'";
        }
        monitorMessage("Found " + matches.size() + " files");
        return "Found " + matches.size() + " file(s):\n" + String.join("\n", matches);
    }

    @Tool("Lists files and folders directly in a directory on the disk filesystem (non-recursive). "
            + "Use this to navigate and explore the directory structure. "
            + "Returns entries prefixed with [DIR] or [FILE].")
    public String listDiskDirectory(
            @P("Relative or absolute directory path. Empty or '/' lists the working directory root.") String path) {

        Path dir;
        if (path == null || path.isBlank() || path.length() == 1) {
            dir = workingDir;
        } else {
            dir = resolve(path);
        }
        if (dir == null || !Files.isDirectory(dir)) {
            onProblem("Directory not found: " + path);
            return "Directory not found: " + path;
        }

        var entries = new ArrayList<String>();
        try (var stream = Files.list(dir)) {
            stream.sorted().forEach(p -> {
                String prefix = Files.isDirectory(p) ? "[DIR]  " : "[FILE] ";
                entries.add(prefix + p.toAbsolutePath());
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to list " + dir, e);
        }

        monitorMessage("Listing " + dir + " " + entries.size());
        if (entries.isEmpty()) {
            return "Directory is empty: " + dir;
        }
        return "Contents of " + dir + ":\n" + String.join("\n", entries);
    }

    private Path resolve(String path) {
        if (path == null) return null;
        Path p = Path.of(path);
        if (p.isAbsolute()) return p.normalize();
        return workingDir.resolve(p).normalize();
    }
}
