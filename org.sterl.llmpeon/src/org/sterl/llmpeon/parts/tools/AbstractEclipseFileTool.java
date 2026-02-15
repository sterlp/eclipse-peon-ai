package org.sterl.llmpeon.parts.tools;

import java.io.InputStream;

import org.eclipse.core.resources.IFile;
import org.sterl.llmpeon.parts.shared.IoUtils;

public abstract class AbstractEclipseFileTool {

    public static String getFileInfo(IFile file) {
        if (file == null) {
            return "No file selected";
        }
        
        try {
            return String.format("""
                File: %s
                Full path: %s
                Extension: %s
                Project name: %s
                Project path: %s
                """,
                file.getName(),
                file.getFullPath().toString(),
                file.getFileExtension(),
                file.getProject().getName(),
                file.getProjectRelativePath().toString()
            );
        } catch (Exception e) {
            return "Error getting file info: " + e.getMessage();
        }
    }
    
    public static String read(IFile file) {
        try (InputStream contents = file.getContents()) {
            return IoUtils.toString(contents, file.getCharset());
        } catch (Exception e) {
            return "Failed to read " + file.getFullPath() + " " + e.getMessage();
        }
    }
}
