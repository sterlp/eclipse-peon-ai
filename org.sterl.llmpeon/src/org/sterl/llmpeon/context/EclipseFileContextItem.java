package org.sterl.llmpeon.context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;

import org.sterl.llmpeon.parts.shared.EclipseUtil;

/**
 * Eclipse-VFS-based context item with lastModified cache.
 * Renders as: "Static loaded file <path>:\n---\n<content>"
 */
public class EclipseFileContextItem implements ContextItem {

    private final String relativePath;
    private volatile String cachedContent;
    private volatile long lastModified = -1;

    public EclipseFileContextItem(String relativePath) {
        this.relativePath = relativePath;
    }

    @Override
    public String label() {
        return relativePath;
    }

    @Override
    public String render() {
        IProject project = EclipseUtil.firstOpenOrSelectedProject();
        if (project == null || !project.isAccessible()) {
            throw new RuntimeException("No accessible project for file: " + relativePath);
        }

        IFile file = project.getFile(relativePath);
        if (!file.exists()) {
            throw new RuntimeException("File not found: " + relativePath);
        }

        Path path = file.getLocation().toFile().toPath();
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            long modified = attrs.lastModifiedTime().toMillis();

            if (modified == lastModified && cachedContent != null) {
                return cachedContent;
            }

            String content = Files.readString(path);
            String rendered = "Static loaded file " + relativePath + ":\n---\n" + content;

            this.cachedContent = rendered;
            this.lastModified = modified;

            return rendered;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + relativePath, e);
        }
    }
}
