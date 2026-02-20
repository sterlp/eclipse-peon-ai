package org.sterl.llmpeon.tool.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.sterl.llmpeon.agent.AiMonitor.AiFileUpdate;

/**
 * Plain filesystem implementation of {@link FileContext} using java.nio.file.
 * Resolves relative paths against the configured root directory.
 */
public class NioFileContext implements FileContext {

    private final Path rootDir;

    public NioFileContext(Path rootDir) {
        this.rootDir = rootDir.toAbsolutePath().normalize();
    }

    public Path getRootDir() {
        return rootDir;
    }

    @Override
    public String readFile(String path) {
        Path resolved = resolve(path);
        if (resolved == null || !Files.isRegularFile(resolved)) {
            return "File not found: " + path;
        }
        try {
            return Files.readString(resolved);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + path, e);
        }
    }

    @Override
    public AiFileUpdate writeFile(String path, String content) {
        Path resolved = resolve(path);
        if (resolved == null || !Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException(
                    "File not found: " + path + ". Use searchFiles to find the correct path.");
        }
        String oldContent;
        try {
            oldContent = Files.readString(resolved);
            Files.writeString(resolved, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + path, e);
        }
        return new AiFileUpdate(rootDir.relativize(resolved).toString(), oldContent, content);
    }

    @Override
    public String createFile(String path, String content) {
        Path resolved = resolve(path);
        if (resolved == null) {
            return "Cannot resolve path: " + path;
        }
        try {
            boolean existed = Files.exists(resolved);
            Files.createDirectories(resolved.getParent());
            Files.writeString(resolved, content);
            return (existed ? "Updated" : "Created") + " file: " + rootDir.relativize(resolved);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create " + path, e);
        }
    }

    @Override
    public String deleteFile(String path) {
        Path resolved = resolve(path);
        if (resolved == null || !Files.exists(resolved)) {
            return "File not found: " + path;
        }
        try {
            Files.delete(resolved);
            return "Deleted file: " + rootDir.relativize(resolved);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete " + path, e);
        }
    }

    @Override
    public List<String> searchFiles(String query) {
        var matches = new ArrayList<String>();
        if (query == null || query.isBlank()) return matches;
        String lowerQuery = query.toLowerCase();
        try (var walk = Files.walk(rootDir)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase().contains(lowerQuery))
                .forEach(p -> matches.add(formatFileInfo(p)));
        } catch (IOException e) {
            throw new RuntimeException("Failed to search in " + rootDir, e);
        }
        return matches;
    }

    private Path resolve(String path) {
        if (path == null) return null;
        Path p = Path.of(path);
        if (p.isAbsolute()) return p.normalize();
        return rootDir.resolve(p).normalize();
    }

    private String formatFileInfo(Path file) {
        return String.format("File: %s\nPath: %s",
                file.getFileName(), rootDir.relativize(file));
    }
}
