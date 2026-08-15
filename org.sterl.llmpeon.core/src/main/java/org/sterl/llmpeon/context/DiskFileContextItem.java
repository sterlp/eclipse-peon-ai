package org.sterl.llmpeon.context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import lombok.RequiredArgsConstructor;

/**
 * Disk-based context item with lastModified cache.
 * Renders as: "Static loaded file <path>:\n---\n<content>"
 */
@RequiredArgsConstructor
public class DiskFileContextItem implements ContextItem {

    private final Path path;
    private volatile String cachedContent;
    private volatile long lastModified = -1;

    @Override
    public String render() {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            long modified = attrs.lastModifiedTime().toMillis();

            if (modified == lastModified && cachedContent != null) {
                return cachedContent;
            }

            String content = Files.readString(path);
            String rendered = "Static loaded file " + path + ":\n---\n" + content;

            this.cachedContent = rendered;
            this.lastModified = modified;

            return rendered;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + path, e);
        }
    }
}
