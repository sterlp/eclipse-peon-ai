package org.sterl.llmpeon.tool.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.sterl.llmpeon.shared.FileUtils;
import org.sterl.llmpeon.shared.StringMatcher;
import org.sterl.llmpeon.shared.StringUtil;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class DiskFileReadTool extends AbstractTool {

    private Path workingDir;

    public DiskFileReadTool(Path workingDir) {
        setWorkingDir(workingDir);
    }
    
    public DiskFileReadTool(String workingDir) {
        setWorkingDir(workingDir);
    }

    @Override
    public boolean isEditTool() { return false; }

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

    @Tool("Disk: Read file - not eclipse.")
    public String readDiskFile(@P("file path") String filePath) {

        if (StringUtil.hasNoValue(filePath)) {
            throw new IllegalArgumentException("filePath must not be empty");
        }

        Path resolved = resolve(filePath);
        if (resolved == null || !Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException("File not found: " + filePath + " also not in " + workingDir);
        }
        try {
            onTool("Reading " + filePath);
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
        
        var matcher = StringMatcher.wildCardMatcher(query);
        var matches = new ArrayList<String>();
        try (var walk = Files.walk(workingDir)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> matcher.match(p.getFileName().toString()) || matcher.match(p.toAbsolutePath().toString()))
                .forEach(p -> matches.add(p.toAbsolutePath().toString()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to search in " + workingDir, e);
        }

        onTool("Found " + matches.size() + " files on disk " + workingDir + " for " + query);
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

        onTool("List directory " + dir + " with " + entries.size() + " elements");
        if (entries.isEmpty()) {
            return "Directory is empty: " + dir;
        }
        return String.join("\n", entries);
    }

    private Path resolve(String path) {
        return FileUtils.resolve(workingDir, path);
    }
}
