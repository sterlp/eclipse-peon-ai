package org.sterl.llmpeon.tool.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.SearchQuery;
import org.sterl.llmpeon.shared.TextFileTypes;
import org.sterl.llmpeon.shared.StringUtil;
import org.sterl.llmpeon.tool.AiReponseBuilder;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class DiskGrepTool extends AbstractTool {

    private Path workingDir;

    public DiskGrepTool(Path workingDir) {
        this.workingDir = workingDir.toAbsolutePath().normalize();
    }

    public DiskGrepTool(String workingDir) {
        this.workingDir = Path.of(workingDir).toAbsolutePath().normalize();
    }
    
    public void setWorkingDir(String workingDir) {
        if (workingDir == null) return;
        setWorkingDir(Path.of(workingDir));
    }
    
    public void setWorkingDir(Path workingDir) {
        this.workingDir = workingDir.toAbsolutePath().normalize();
    }

    @Override
    public boolean isEditTool() { return false; }

    @Tool("Search file contents on disk for text or regex. Scope to directory and extension.")
    public String diskGrepFiles(
            @P(description = "text or regex to match in file contents", name = "query") String query,
            @P(description = "directory path to search in, defaults to working dir", required = false, name = "path") String path,
            @P(description = "file extension, e.g. .java", required = false, name = "extension") String extension) {

        ArgsUtil.requireNonBlank(query, "query");
        var searchQuery = SearchQuery.of(query);

        Path searchDir = (path == null || path.isBlank()) ? workingDir : workingDir.resolve(path).normalize();
        if (!Files.isDirectory(searchDir)) {
            throw new IllegalArgumentException("Directory not found: " + path);
        }

        var matches = new LinkedHashMap<String, Integer>();

        try (var walk = Files.walk(searchDir)) {
            var stream = walk.filter(Files::isRegularFile);
            if (StringUtil.hasValue(extension)) {
                String ext = extension.trim().toLowerCase();
                stream = stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(ext));
            } else {
                stream = stream.filter(p -> TextFileTypes.isTextFile(p.getFileName().toString()));
            }

            stream.forEach(file -> {
                if (matches.size() >= AiReponseBuilder.MAX_GREP_FILES) return;
                try {
                    String content = Files.readString(file);
                    int count = searchQuery.count(content);
                    if (count > 0) {
                        matches.put(file.toAbsolutePath().toString(), count);
                    }
                } catch (IOException e) {
                    // skip unreadable files
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to search in " + searchDir, e);
        }

        onTool("Grep '" + query + "' type '" + StringUtil.getOrDefault(extension, "*")
                + "' found " + matches.size() + " matches");

        return AiReponseBuilder.grepComplete(matches, searchQuery, AiReponseBuilder.MAX_GREP_FILES, extension);
    }
}
