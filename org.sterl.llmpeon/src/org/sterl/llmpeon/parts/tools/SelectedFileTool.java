package org.sterl.llmpeon.parts.tools;

import java.io.InputStream;

import org.eclipse.core.resources.IFile;
import org.sterl.llmpeon.parts.shared.IoUtils;

import dev.langchain4j.agent.tool.Tool;

public class SelectedFileTool {
    private final IFile file;
    
    public SelectedFileTool(IFile file) {
        this.file = file;
    }
    
    @Tool("Reads and returns the complete content of the currently selected file in the Eclipse workspace")
    public String readCurrentFile() {
        if (file == null) {
            return "No file is currently selected";
        }
        
        try (InputStream contents = file.getContents()) {
            
            return getFileInfo() + "Content:\n" + IoUtils.toString(contents, file.getCharset());
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }
    
    public String getFileInfo() {
        if (file == null) {
            return "No file is currently selected";
        }
        
        try {
            return String.format("""
                File: %s
                Full path: %s
                Project path: %s
                Extension: %s
                Charset: %s
                """,
                file.getName(),
                file.getFullPath().toString(),
                file.getProjectRelativePath().toString(),
                file.getFileExtension(),
                file.getCharset()
            );
        } catch (Exception e) {
            return "Error getting file info: " + e.getMessage();
        }
    }
}