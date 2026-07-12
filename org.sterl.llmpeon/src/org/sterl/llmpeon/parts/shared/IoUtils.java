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
    
    public static String readString(IFile file) {
        try {
            return file.readString();
        } catch (CoreException e) {
            throw new RuntimeException("Failed to read " + JdtUtil.pathOf(file), e);
        }
    }

    // StandardCharsets.UTF_8.name() > JDK 7
    public static String toString(InputStream input, String charset) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        for (int length; (length = input.read(buffer)) != -1; ) {
            result.write(buffer, 0, length);
        }
        return result.toString(charset);
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
    public static IFile writeProjectFile(IProject project, 
            String projectRelativePath,
            String content, IProgressMonitor monitor) {
        if (monitor == null) monitor = new NullProgressMonitor();
        IFile file = project.getFile(projectRelativePath);
        writeFile(file, content, monitor);
        return file;
    }
    
    public static void writeFile(IFile file, String content,IProgressMonitor monitor ) {
        try {
            var charset = getCharset(file);
            ensureFolders(file.getParent(), monitor);
            file.write(content.getBytes(charset), true, false, true, monitor);
            file.refreshLocal(IResource.DEPTH_ZERO, monitor);
            // Refresh parent (folder or project) — DEPTH_ONE covers the file itself
            if (file.getParent() != null) {
                file.getParent().refreshLocal(IResource.DEPTH_ONE, monitor);
            }
        } catch (CoreException e) {
            throw new RuntimeException("Failed to write " + JdtUtil.pathOf(file), e);
        }
    }

    /** Creates the folder hierarchy for the given container if it doesn't exist. */
    public static void ensureFolders(IContainer container, IProgressMonitor monitor) throws CoreException {
        if (container == null || container instanceof IProject) return;
        if (container instanceof IFolder folder && !folder.exists()) {
            ensureFolders(folder.getParent(), monitor);
            folder.create(IResource.FORCE, true, monitor);
            if (folder.getParent() != null) folder.getParent().refreshLocal(IResource.DEPTH_ONE, monitor);
            else folder.refreshLocal(IResource.DEPTH_ZERO, monitor);
        }
    }

    private static Charset getCharset(IFile file) {
        try {
            var charset = file.getCharset();
            if (charset == null) charset = file.getProject().getDefaultCharset();
            if (charset == null) return StandardCharsets.UTF_8;
            return Charset.forName(charset);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }
}
