package org.sterl.llmpeon.parts.tools;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Generic file reading tool – no Eclipse dependencies.
 * Reads any file by its full filesystem path.
 */
public class ReadFileTool extends AbstractEclipseFileTool {

    @Tool("Reads and returns the complete content of a file given its full filesystem path. "
            + "Use searchFiles first to find the path of the file you want to read.")
    public String readFile(
            @P("The full filesystem path of the file to read, e.g. '/home/user/project/src/Main.java'") String filePath) {

        if (filePath == null || filePath.isBlank()) {
            return "Error: filePath must not be empty";
        }

        Path path = Path.of(filePath);
        try {
            if (Files.exists(path) && Files.isRegularFile(path)) {
                return Files.readString(path);
            } else {
                IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
                
                IFile file = root.getFile(IPath.fromPath(path));
                if (file.exists()) {
                    return read(file);
                } else {
                    return "File " + path + " not found!";
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
    }
}
