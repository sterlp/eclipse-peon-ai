package org.sterl.llmpeon.tool.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Set;

import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.RegexUtils;
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

    @Tool("Disk: Search file contents for text (not eclipse).")
    public String grepDiskFiles(
            @P(description = "text or regex to match in file contents", name = "query") String query,
            @P(description = "directory path to search in, defaults to working dir", required = false, name = "path") String path,
            @P(description = "file extension, e.g. .java", required = false, name = "extension") String extension) {

        ArgsUtil.requireNonBlank(query, "query");

        Path searchDir = (path == null || path.isBlank()) ? workingDir : workingDir.resolve(path).normalize();
        if (!Files.isDirectory(searchDir)) {
            throw new IllegalArgumentException("Directory not found: " + path);
        }

        var matches = new LinkedHashMap<String, Integer>();
        final int MAX_FILES = 100;

        try (var walk = Files.walk(searchDir)) {
            var stream = walk.filter(Files::isRegularFile);
            if (StringUtil.hasValue(extension)) {
                String ext = extension.trim().toLowerCase();
                stream = stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(ext));
            } else {
                stream = stream.filter(p -> isTextFile(p));
            }

            stream.forEach(file -> {
                if (matches.size() >= MAX_FILES) return;
                try {
                    String content = Files.readString(file);
                    int count = RegexUtils.countOccurrences(content, query);
                    if (count > 0) {
                        matches.put(file.toAbsolutePath().toString(), count);
                    }
                } catch (IOException | IllegalArgumentException e) {
                    // skip unreadable files
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to search in " + searchDir, e);
        }

        onTool("Grep '" + query + "' type '" + StringUtil.getOrDefault(extension, "*")
                + "' found " + matches.size() + " matches");

        String suffix = null;
        if (matches.size() >= MAX_FILES) {
            suffix = "... result capped at " + MAX_FILES + " files. Narrow your search path.";
        }
        return AiReponseBuilder.searchComplete(matches.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue() + "occurrence(s)").toList(), 
                suffix);
    }

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "java", "xml", "json", "yaml", "yml", "properties", "txt", "md",
            "html", "css", "js", "ts", "jsx", "tsx", "sql", "sh", "bat",
            "gradle", "kt", "groovy", "scala", "py", "rb", "php", "c", "h",
            "cpp", "hpp", "rs", "go", "swift", "cfg", "ini", "toml", "csv");

    private static boolean isTextFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return TEXT_EXTENSIONS.contains(name.substring(dot + 1));
    }
}
