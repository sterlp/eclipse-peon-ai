package org.sterl.llmpeon.context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.jspecify.annotations.Nullable;

import org.sterl.llmpeon.parts.shared.JdtUtil;

/**
 * Eclipse-VFS-based context item.
 * Renders as: "&lt;full workspace path&gt;:\n---\n&lt;content&gt;".
 * A missing project/file or read error renders {@code null} (nothing to inject).
 */
public class EclipseFileContextItem implements ContextItem {

    private final String relativePath;
    private final IProject project;

    public EclipseFileContextItem(String relativePath, IProject project) {
        this.relativePath = relativePath;
        this.project = project;
    }

    @Override
    public String label() {
        return key();
    }

    @Override
    public String dedupKey() {
        String key = key();
        if (key == null) return null;
        return key + ":" + System.lineSeparator() + "---" + System.lineSeparator();
    }

    @Override
    public String render() {
        String key = key();
        if (key == null) return null;
        IFile file = project.getFile(relativePath);
        if (!file.exists()) return null;
        Path path = file.getLocation().toFile().toPath();
        try {
            String content = Files.readString(path);
            return key + ":" + System.lineSeparator() + "---" + System.lineSeparator() + content;
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
    private String key() {
        if (project == null || !project.isAccessible()) return null;
        return JdtUtil.pathOf(project.getFile(relativePath));
    }
}
