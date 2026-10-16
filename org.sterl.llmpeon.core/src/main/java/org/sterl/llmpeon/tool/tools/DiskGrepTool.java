package org.sterl.llmpeon.tool.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.sterl.llmpeon.shared.ArgsUtil;
import org.sterl.llmpeon.shared.GrepHit;
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
        // The extension filter is a property of the directory walk: an explicitly named file is
        // never filtered, so the disclosure must not claim a filter that did not happen (C-EXT)
        String effectiveExtension = Files.isDirectory(searchDir) ? extension : null;
        List<GrepHit> hits;
        if (Files.isDirectory(searchDir)) {
            hits = grepDirectory(extension, searchQuery, searchDir);
        } else if (Files.isRegularFile(searchDir)) {
            try {
                hits = grepFile(searchQuery, searchDir);
            } catch (IOException e) {
                throw new IllegalArgumentException("Grep file " + searchDir + " failed: " + e.getMessage(), e);
            }
        } else {
            throw new IllegalArgumentException("Path not found: " + path + " check your query or leave the path empty.");
        }

        String result = AiReponseBuilder.grepComplete(hits, searchQuery, AiReponseBuilder.MAX_GREP_FILES,
                AiReponseBuilder.MAX_GREP_LINES, effectiveExtension);
        onTool("🔍 disk grep '" + query + "' type '" + StringUtil.getOrDefault(effectiveExtension, "*")
                + "' found " + hits.size() + " matched lines" + " " + StringUtil.charsSuffix(result));
        return result;
    }
    
    private @NonNull List<GrepHit> grepFile(SearchQuery searchQuery, Path file) throws IOException {
        var content = Files.readString(file);
        var absPath = file.toAbsolutePath().toString();
        var lineHits = searchQuery.matchingLines(content);
        if (lineHits.isEmpty()) return Collections.emptyList();
        return lineHits.stream().map(lh -> new GrepHit(absPath, lh.line(), lh.text())).toList();
    }

    private @NonNull List<GrepHit> grepDirectory(String extension,
            SearchQuery searchQuery, Path searchDir) {
        var hits = new ArrayList<GrepHit>();
        var matchedFiles = new HashSet<String>(); // distinct hit files — drives the file cap

        try (var walk = Files.walk(searchDir)) {
            var stream = walk.filter(Files::isRegularFile);
            if (StringUtil.hasValue(extension)) {
                String ext = extension.trim().toLowerCase();
                stream = stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(ext));
            } else {
                stream = stream.filter(p -> TextFileTypes.isTextFile(p.getFileName().toString()));
            }

            stream.forEach(file -> {
                if (matchedFiles.size() >= AiReponseBuilder.MAX_GREP_FILES) return;
                try {
                    String content = Files.readString(file);
                    String absPath = file.toAbsolutePath().toString();
                    var lineHits = searchQuery.matchingLines(content);
                    if (lineHits.isEmpty()) return;
                    matchedFiles.add(absPath);
                    lineHits.forEach(lh -> hits.add(new GrepHit(absPath, lh.line(), lh.text())));
                } catch (IOException e) {
                    // skip unreadable files
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to search in " + searchDir, e);
        }
        return hits;
    }
}
