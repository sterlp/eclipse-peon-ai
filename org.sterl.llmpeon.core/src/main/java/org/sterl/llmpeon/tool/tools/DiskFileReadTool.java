package org.sterl.llmpeon.tool.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.FileLines;
import org.sterl.llmpeon.shared.FileUtils;
import org.sterl.llmpeon.shared.StringMatcher;
import org.sterl.llmpeon.tool.AiReponseBuilder;

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
    public String readDiskFile(
            @P(name = "filePath") String filePath,
            @P(description = "first line to read (1-based). 0 = start of file.", required = false, name = "startLine") 
            Integer startLine,
            @P(description = "last line to read (1-based). 0 = end of file.", required = false, name = "endLine") 
            Integer endLine) {

        ArgsUtil.requireNonBlank(filePath, "filePath");
        if (startLine == null) startLine = 0;
        if (endLine == null) endLine = 0;

        Path resolved = resolve(filePath);
        if (resolved == null || !Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException("File not found: " + filePath + " also not in " + workingDir);
        }

        try {
            var lines = "";
            if (startLine > 0 && endLine > 0) lines = " from " + startLine + " to " + endLine;
            onTool("Reading " + lines + " file " + filePath);
            String content = Files.readString(resolved);
            return FileLines.extract(content, startLine, endLine);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + filePath, e);
        }
    }

    @Tool("Disk: Search files by name. Use '*' to list all files recursively.")
    public String searchDiskFiles(
            @P(description = "file name query - only *, ? wildcard is supported.", name = "query") String query,
            @P(description = "Optional: max results to return. 0 = unlimited.", name = "limit") Integer limit) {

        if (limit == null) limit = 0;
        ArgsUtil.requireNonBlank(query, "query");

        var matcher = StringMatcher.wildCardMatcher(FileUtils.normalizePath(query));
        var matches = new ArrayList<String>();
        try (var walk = Files.walk(workingDir)) {
            var stream = walk.filter(Files::isRegularFile)
                .filter(p -> matcher.match(p.getFileName().toString()) 
                        || matcher.match(FileUtils.normalizePath(p.toAbsolutePath().toString())));
            if (limit > 0) stream = stream.limit(limit);
            stream.forEach(p -> matches.add(p.toAbsolutePath().toString()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to search in " + workingDir, e);
        }

        onTool("Found " + matches.size() + " files in " + workingDir + " for '" + query + "'.");
        String suffix = null;
        if (matches.isEmpty()) {
            suffix =  "Use " + LIST_DISK_NAME + " to explore the project structure.";
        }
        return AiReponseBuilder.searchComplete(matches, suffix);
    }

    public static final String LIST_DISK_NAME = "listDiskDirectory";
    @Tool(name = LIST_DISK_NAME, value = "Disk: List directory (non-recursive).")
    public String listDiskDirectory(
            @P(description = "Empty or '/' lists working dir root.", name = "path", required = false) 
            String path) {

        Path dir;
        if (path == null || path.isBlank() || path.length() == 1) {
            dir = workingDir;
        } else {
            dir = resolve(path);
        }
        if (dir == null || !Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Directory not found: " + path);
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
