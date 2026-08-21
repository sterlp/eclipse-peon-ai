package org.sterl.llmpeon.context;

import java.util.Arrays;
import java.util.function.Supplier;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.jspecify.annotations.Nullable;
import org.sterl.llmpeon.parts.shared.IoUtils;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.shared.FileLines;

/**
 * Dynamically loads the first found file into the context.
 */
public class EclipseFileContextItem implements ContextItem {

    private final String[] relativePaths;
    private final Supplier<IProject> project;

    public EclipseFileContextItem(String relativePath, Supplier<IProject> project) {
        this.relativePaths = new String[] { relativePath };
        this.project = project;
    }

    public EclipseFileContextItem(String[] relativePaths, Supplier<IProject> project) {
        this.relativePaths = relativePaths;
        this.project = project;
    }

    @Override
    public String label() {
        var file = exists();
        if (file == null) return null;
        return JdtUtil.pathOf(file);
    }

    @Override
    public String dedupKey() {
        String key = label();
        if (key == null) return null;
        return key + " content with line numbers:";
    }

    @Override
    public String render() {
        var file = exists();
        if (file == null) return null;
        return FileLines.format(IoUtils.readString(file), 1);
    }

    @Nullable
    private IFile exists() {
        if (project == null) return null;
        if (project.get() == null) return null;
        if (!project.get().isAccessible()) return null;
        // select the first found
        for (String relativePath : relativePaths) {
            IFile file = project.get().getFile(relativePath);
            if (file != null && file.exists()) return file;
        }
        return null;
    }
    
    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " [" + Arrays.toString(relativePaths) + "]";
    }
}
