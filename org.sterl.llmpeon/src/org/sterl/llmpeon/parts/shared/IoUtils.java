package org.sterl.llmpeon.parts.shared;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

public class IoUtils {

    // StandardCharsets.UTF_8.name() > JDK 7
    public static String toString(InputStream input, String charset) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        for (int length; (length = input.read(buffer)) != -1; ) {
            result.write(buffer, 0, length);
        }
        return result.toString(charset);
    }

    public static String readFile(IFile file) {
        try {
            return file.readString();
        } catch (CoreException e) {
            throw new RuntimeException("Failed to read " + file.getFullPath(), e);
        }
    }

    /**
     * Writes content to a project-relative path, creating parent folders as needed.
     * Uses Eclipse IFile APIs so workspace history and refresh notifications are triggered.
     *
     * @param project              target project
     * @param projectRelativePath  path relative to the project root (e.g. ".plan/overview.md")
     * @param content              text content to write
     * @param monitor              progress monitor (may be null)
     * @return the written IFile
     */
    public static IFile writeProjectFile(IProject project, String projectRelativePath,
            String content, IProgressMonitor monitor) {
        if (monitor == null) monitor = new NullProgressMonitor();
        IFile file = project.getFile(projectRelativePath);
        try {
            Charset charset = getProjectCharset(project);
            byte[] bytes = content.getBytes(charset);
            ensureFolders(file.getParent(), monitor);
            file.write(bytes, true, false, true, monitor);
            file.refreshLocal(IResource.DEPTH_ZERO, monitor);
        } catch (CoreException e) {
            throw new RuntimeException("Failed to write " + file.getFullPath(), e);
        }
        return file;
    }

    /** Creates the folder hierarchy for the given container if it doesn't exist. */
    public static void ensureFolders(IContainer container, IProgressMonitor monitor) throws CoreException {
        if (container == null || container instanceof IProject) return;
        if (container instanceof IFolder folder && !folder.exists()) {
            ensureFolders(folder.getParent(), monitor);
            folder.create(IResource.FORCE, true, monitor);
        }
    }

    private static Charset getProjectCharset(IProject project) {
        try {
            return Charset.forName(project.getDefaultCharset());
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }
}
