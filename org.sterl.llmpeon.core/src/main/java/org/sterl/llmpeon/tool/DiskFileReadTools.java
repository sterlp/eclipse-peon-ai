package org.sterl.llmpeon.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class DiskFileReadTools extends AbstractTool {

    private Path workingDir;

    public DiskFileReadTools(Path workingDir) {
        this.workingDir = workingDir.toAbsolutePath().normalize();
    }

    @Override
    public boolean isEditTool() { return false; }

    public void setWorkingDir(Path workingDir) {
        this.workingDir = workingDir.toAbsolutePath().normalize();
    }

    public Path getWorkingDir() {
        return workingDir;
    }

    @Tool("Disk: Read file.")
    public String readDiskFile(@P("file path") String filePath) {

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath must not be empty");
        }

        Path resolved = resolve(filePath);
        if (resolved == null || !Files.isRegularFile(resolved)) {
            onProblem("File not found: " + filePath);
            return "File not found: " + filePath;
        }
        try {
            monitorMessage("Reading " + filePath);
            return Files.readString(resolved);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + filePath, e);
        }
    }

    @Tool("Disk: Search files by name.")
    public String searchDiskFiles(@P("file name query") String query) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be empty");
        }
        String lowerQuery = query.toLowerCase();
        var matches = new ArrayList<String>();
        try (var walk = Files.walk(workingDir)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase().contains(lowerQuery))
                .forEach(p -> matches.add(p.toAbsolutePath().toString()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to search in " + workingDir, e);
        }

        monitorMessage("Found " + matches.size() + " files on disk for " + query);
        if (matches.isEmpty()) {
            return "No files found matching '" + query + "' adjust your query";
        }
        return String.join("\n", matches);
    }

    @Tool("Disk: List directory (non-recursive).")
    public String listDiskDirectory(
            @P("Optional path. Empty or '/' lists working dir root.") String path) {

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
                String prefix = Files.isDirectory(p) ? "[DIR] " : "[FILE] ";
                entries.add(prefix + p.toAbsolutePath());
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to list " + dir, e);
        }

        monitorMessage("List directory " + path + " with " + entries.size() + " elements");
        if (entries.isEmpty()) {
            return "Directory is empty: " + dir;
        }
        return String.join("\n", entries);
    }

    private Path resolve(String path) {
        if (path == null) return null;
        Path p = Path.of(path);
        if (p.isAbsolute()) return p.normalize();
        return workingDir.resolve(p).normalize();
    }
}
