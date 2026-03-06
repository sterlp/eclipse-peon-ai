package org.sterl.llmpeon.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.sterl.llmpeon.agent.AiMonitor.AiFileUpdate;

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
            return "No files found matching '" + query + "' adjust your query";
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
